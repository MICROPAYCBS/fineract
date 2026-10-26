/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package com.micropay.fraud.engine.watchlist;

import static org.assertj.core.api.Assertions.assertThat;

import com.micropay.fraud.engine.event.PartyType;
import java.io.IOException;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Downloads and parses the real published lists. Needs internet access, so it only runs when
 * {@code FRAUD_WATCHLIST_LIVE_TEST=true}; run it after a publisher changes its format.
 */
@EnabledIfEnvironmentVariable(named = "FRAUD_WATCHLIST_LIVE_TEST", matches = "true")
@Slf4j
class LiveListsTest {

    private static final String OFAC_URL = "https://sanctionslistservice.ofac.treas.gov/api/PublicationPreview/exports/SDN.XML";
    private static final String UN_URL = "https://scsanctions.un.org/resources/xml/en/consolidated.xml";
    private static final String EU_URL = "https://webgate.ec.europa.eu/fsd/fsf/public/files/xmlFullSanctionsList_1_1/content?token=dG9rZW4tMjAxNw";

    private static WatchlistSnapshot fetch(String source, String url, ListParser parser) throws IOException {
        final WatchlistSnapshot snapshot = new StreamWatchlistProvider(source, url, parser, StreamWatchlistProvider.defaultHttpClient())
                .fetch();
        log.info("{}: {} entries, {} people, {} with a date of birth, version {}", source, snapshot.entries().size(),
                snapshot.entries().stream().filter(e -> e.partyType() == PartyType.PERSON).count(),
                snapshot.entries().stream().filter(e -> !e.birthDates().isEmpty()).count(), snapshot.version());
        return snapshot;
    }

    @Test
    void ofac() throws IOException {
        final WatchlistSnapshot snapshot = fetch("OFAC", OFAC_URL, new OfacSdnParser());
        assertThat(snapshot.entries()).hasSizeGreaterThan(5_000);
        assertThat(snapshot.entries()).anyMatch(e -> e.partyType() == PartyType.PERSON && !e.birthDates().isEmpty());
        assertThat(snapshot.entries()).anyMatch(e -> !e.aliases().isEmpty());
    }

    @Test
    void un() throws IOException {
        final WatchlistSnapshot snapshot = fetch("UN", UN_URL, new UnConsolidatedParser());
        assertThat(snapshot.entries()).hasSizeGreaterThan(500);
        assertThat(snapshot.entries()).anyMatch(e -> e.partyType() == PartyType.ENTITY);
        assertThat(snapshot.entries()).anyMatch(e -> !e.birthDates().isEmpty());
    }

    @Test
    void eu() throws IOException {
        final WatchlistSnapshot snapshot = fetch("EU", EU_URL, new EuConsolidatedParser());
        assertThat(snapshot.entries()).hasSizeGreaterThan(1_000);
        assertThat(snapshot.entries()).anyMatch(e -> e.partyType() == PartyType.ENTITY);
        assertThat(snapshot.entries()).anyMatch(e -> !e.birthDates().isEmpty());
    }
}
