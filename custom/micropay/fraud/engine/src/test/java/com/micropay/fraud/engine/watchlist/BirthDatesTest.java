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

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

class BirthDatesTest {

    @Test
    void readsTheFormatsListsUse() {
        assertThat(BirthDates.normalise("18 Sep 1964")).containsExactly("1964-09-18");
        assertThat(BirthDates.normalise("1979-03-02T00:00:00")).containsExactly("1979-03-02");
        assertThat(BirthDates.normalise("circa 1961")).containsExactly("1961");
        assertThat(BirthDates.normalise("1958 to 1960")).containsExactly("1958", "1960");
        assertThat(BirthDates.normalise("unknown")).isEmpty();
        assertThat(BirthDates.normalise(null)).isEmpty();
    }

    @Test
    void comparesAgainstFullDatesAndYears() {
        final LocalDate dob = LocalDate.of(1964, 9, 18);
        assertThat(BirthDateCheck.of(dob, List.of("1964-09-18"))).isEqualTo(BirthDateCheck.MATCH);
        assertThat(BirthDateCheck.of(dob, List.of("1964"))).isEqualTo(BirthDateCheck.MATCH);
        assertThat(BirthDateCheck.of(dob, List.of("1965-01-01"))).isEqualTo(BirthDateCheck.CLOSE);
        assertThat(BirthDateCheck.of(dob, List.of("1990"))).isEqualTo(BirthDateCheck.MISMATCH);
        assertThat(BirthDateCheck.of(null, List.of("1990"))).isEqualTo(BirthDateCheck.UNKNOWN);
        assertThat(BirthDateCheck.of(dob, List.of())).isEqualTo(BirthDateCheck.UNKNOWN);
    }

    @Test
    void adjustsTheNameScore() {
        assertThat(BirthDateCheck.MATCH.adjust(98)).isEqualTo(100);
        assertThat(BirthDateCheck.MISMATCH.adjust(90)).isEqualTo(65);
        assertThat(BirthDateCheck.CLOSE.adjust(90)).isEqualTo(90);
    }
}
