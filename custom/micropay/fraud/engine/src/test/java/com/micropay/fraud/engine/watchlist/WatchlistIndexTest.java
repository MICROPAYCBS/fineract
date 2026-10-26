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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class WatchlistIndexTest {

    private static WatchlistIndex index;

    @BeforeAll
    static void load() throws IOException {
        final List<WatchlistEntry> entries = new ArrayList<>();
        entries.addAll(new OfacSdnParser().parse("OFAC", ListParsersTest.resource("ofac-sdn.xml")));
        entries.addAll(new UnConsolidatedParser().parse("UN", ListParsersTest.resource("un-consolidated.xml")));
        entries.addAll(new EuConsolidatedParser().parse("EU", ListParsersTest.resource("eu-consolidated.xml")));
        entries.addAll(new CsvListParser().parse("LOCAL", ListParsersTest.resource("local.csv")));
        index = new WatchlistIndex("v1", entries);
    }

    private static List<WatchlistMatch> search(String name, LocalDate dob, PartyType party) {
        return index.search(new ScreeningSubject("default", 1L, name, dob, party), 85);
    }

    @Test
    void findsAListedPersonThroughAnAlias() {
        final List<WatchlistMatch> matches = search("Joseph Koni", LocalDate.of(1964, 9, 18), PartyType.PERSON);

        assertThat(matches).hasSize(1);
        assertThat(matches.get(0).entry().uid()).isEqualTo("9001");
        assertThat(matches.get(0).matchedName()).isEqualTo("Joseph KONI");
        assertThat(matches.get(0).birthDateCheck()).isEqualTo(BirthDateCheck.MATCH);
        assertThat(matches.get(0).score()).isEqualTo(100);
    }

    @Test
    void aClearlyDifferentDateOfBirthDropsTheMatch() {
        assertThat(search("Joseph Kony", LocalDate.of(2001, 1, 1), PartyType.PERSON)).isEmpty();
        assertThat(search("Joseph Kony", null, PartyType.PERSON)).hasSize(1);
    }

    @Test
    void personsAreNotComparedWithOrganisationsOrVessels() {
        assertThat(search("Nile Basin Trading Ltd", null, PartyType.PERSON)).isEmpty();
        assertThat(search("Nile Basin Trading Ltd", null, PartyType.ENTITY)).extracting(m -> m.entry().uid()).containsExactly("9002");
        assertThat(search("Ocean Star", null, PartyType.ENTITY)).isEmpty();
    }

    @Test
    void searchesEveryList() {
        assertThat(search("Amina Hassan Abdi", LocalDate.of(1979, 3, 2), PartyType.PERSON)).extracting(m -> m.entry().source())
                .containsExactly("UN");
        assertThat(search("Ivan Sidorov", null, PartyType.UNKNOWN)).extracting(m -> m.entry().source()).containsExactly("EU");
        assertThat(search("Brian M Okello", LocalDate.of(1985, 11, 20), PartyType.PERSON)).extracting(m -> m.entry().source())
                .containsExactly("LOCAL");
        assertThat(search("Sarah Namubiru", null, PartyType.PERSON)).isEmpty();
    }
}
