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
package org.apache.fineract.infrastructure.accountnumberformat.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;
import org.junit.jupiter.api.Test;

public class CheckDigitCalculatorTest {

    @Test
    public void normalizesSegmentValues() {
        assertThat(CheckDigitCalculator.normalizeSegmentValue("1", 3, true)).isEqualTo("001");
        assertThat(CheckDigitCalculator.normalizeSegmentValue("SAVINGS", 2, false)).isEqualTo("SA");
        assertThat(CheckDigitCalculator.normalizeSegmentValue("S", 2, false)).isEqualTo("S0");
    }

    @Test
    public void calculatesLuhnCheckDigit() {
        assertThat(CheckDigitCalculator.calculate(CheckDigitAlgorithm.LUHN, "7992739871")).isEqualTo("3");
        assertThat(CheckDigitCalculator.calculate(CheckDigitAlgorithm.LUHN, "7992739871A")).isEqualTo("3");
    }

    @Test
    public void mod11RemainderOneIsZero() {
        assertThat(CheckDigitCalculator.calculate(CheckDigitAlgorithm.MOD11, "6")).isEqualTo("0");
    }
}
