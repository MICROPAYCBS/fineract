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

import org.junit.jupiter.api.Test;

class NameMatcherTest {

    private static int score(String a, String b) {
        return NameMatcher.score(NameNormalizer.tokens(a), NameNormalizer.tokens(b));
    }

    @Test
    void normalisingRemovesAccentsTitlesAndPunctuation() {
        assertThat(NameNormalizer.normalise("Dr. José  O'Brien-Smith")).isEqualTo("jose obrien smith");
        assertThat(NameNormalizer.normalise("Quick Cash Ventures Ltd.")).isEqualTo("quick cash ventures");
    }

    @Test
    void sameNameInAnyOrderScoresFull() {
        assertThat(score("Joseph Kony", "KONY, Joseph")).isEqualTo(100);
    }

    @Test
    void smallSpellingDifferencesStillMatch() {
        assertThat(score("Josef Konyi", "Joseph Kony")).isGreaterThanOrEqualTo(85);
    }

    @Test
    void anExtraOrMissingMiddleNameCostsLittle() {
        assertThat(score("Joseph Kony", "Joseph Rao Kony")).isGreaterThanOrEqualTo(90);
        assertThat(score("John Joseph Kony", "Joseph Kony")).isGreaterThanOrEqualTo(90);
    }

    @Test
    void aDifferentSurnameDoesNotMatch() {
        assertThat(score("Joseph Mukasa", "Joseph Kony")).isLessThan(85);
        assertThat(score("Sarah Namubiru", "Joseph Kony")).isZero();
    }

    @Test
    void singleWordsOnlyMatchExactly() {
        assertThat(score("GLRF", "glrf")).isEqualTo(100);
        assertThat(score("Kony", "Joseph Kony")).isZero();
        assertThat(score("GLRFX", "GLRF")).isZero();
    }

    @Test
    void initialsPairWithFullNames() {
        assertThat(score("B. M. Okello", "Brian Mugisha Okello")).isGreaterThanOrEqualTo(85);
    }

    @Test
    void jaroWinklerKnownValues() {
        assertThat(NameMatcher.jaroWinkler("martha", "marhta")).isBetween(0.96, 0.97);
        assertThat(NameMatcher.jaroWinkler("abc", "xyz")).isZero();
    }
}
