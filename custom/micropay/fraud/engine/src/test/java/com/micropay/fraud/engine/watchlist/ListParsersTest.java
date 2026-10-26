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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.micropay.fraud.engine.event.PartyType;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class ListParsersTest {

    static InputStream resource(String name) {
        return ListParsersTest.class.getResourceAsStream("/watchlist/" + name);
    }

    @Test
    void readsOfacSdn() throws IOException {
        final List<WatchlistEntry> entries = new OfacSdnParser().parse("OFAC", resource("ofac-sdn.xml"));

        assertThat(entries).hasSize(3);
        final WatchlistEntry kony = entries.get(0);
        assertThat(kony.uid()).isEqualTo("9001");
        assertThat(kony.partyType()).isEqualTo(PartyType.PERSON);
        assertThat(kony.primaryName()).isEqualTo("Joseph KONY");
        assertThat(kony.aliases()).containsExactly("Joseph KONI");
        assertThat(kony.birthDates()).containsExactly("1961", "1964-09-18");
        assertThat(kony.programs()).containsExactly("CAR", "SDGT");
        assertThat(entries.get(1).partyType()).isEqualTo(PartyType.ENTITY);
        assertThat(entries.get(2).partyType()).isEqualTo(PartyType.OTHER);
    }

    @Test
    void readsUnConsolidated() throws IOException {
        final List<WatchlistEntry> entries = new UnConsolidatedParser().parse("UN", resource("un-consolidated.xml"));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).uid()).isEqualTo("QDi.900");
        assertThat(entries.get(0).primaryName()).isEqualTo("AMINA HASSAN ABDI");
        assertThat(entries.get(0).aliases()).containsExactly("Amina Hassan");
        assertThat(entries.get(0).birthDates()).containsExactly("1979-03-02");
        assertThat(entries.get(1).partyType()).isEqualTo(PartyType.ENTITY);
        assertThat(entries.get(1).aliases()).containsExactly("GLRF");
    }

    @Test
    void readsEuConsolidated() throws IOException {
        final List<WatchlistEntry> entries = new EuConsolidatedParser().parse("EU", resource("eu-consolidated.xml"));

        assertThat(entries).hasSize(2);
        final WatchlistEntry person = entries.get(0);
        assertThat(person.uid()).isEqualTo("EU.900.1");
        assertThat(person.primaryName()).isEqualTo("Ivan Petrovich Sidorov");
        assertThat(person.aliases()).containsExactly("Иван Петрович Сидоров");
        assertThat(person.birthDates()).containsExactly("1970-06-01");
        assertThat(person.programs()).containsExactly("RUS");
        assertThat(entries.get(1).partyType()).isEqualTo(PartyType.ENTITY);
    }

    @Test
    void readsLocalCsv() throws IOException {
        final List<WatchlistEntry> entries = new CsvListParser().parse("LOCAL", resource("local.csv"));

        assertThat(entries).hasSize(2);
        assertThat(entries.get(0).primaryName()).isEqualTo("Okello, Brian Mugisha");
        assertThat(entries.get(0).aliases()).containsExactly("Brian Okello", "B. M. Okello");
        assertThat(entries.get(0).birthDates()).containsExactly("1985-11-20");
        assertThat(entries.get(1).programs()).containsExactly("FIA notice, 2026");
        assertThat(entries.get(1).partyType()).isEqualTo(PartyType.ENTITY);
    }

    @Test
    void refusesDtdsAndBrokenXml() {
        final String xxe = "<?xml version=\"1.0\"?><!DOCTYPE x [<!ENTITY e SYSTEM \"file:///etc/passwd\">]>"
                + "<sdnList><sdnEntry><uid>1</uid><lastName>&e;</lastName></sdnEntry></sdnList>";
        assertThatThrownBy(() -> new OfacSdnParser().parse("OFAC", new ByteArrayInputStream(xxe.getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class);
        assertThatThrownBy(
                () -> new OfacSdnParser().parse("OFAC", new ByteArrayInputStream("<sdnList><sdnEntry>".getBytes(StandardCharsets.UTF_8))))
                .isInstanceOf(IOException.class);
    }
}
