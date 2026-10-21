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

import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.StructuredAccountNumberRuleDefaults;
import org.junit.jupiter.api.Test;

public class StructuredAccountNumberPatternValidatorTest {

    @Test
    public void defaultPatternsAreValid() {
        for (final EntityAccountType type : EntityAccountType.values()) {
            final StructuredAccountNumberRuleDefaults defaults = StructuredAccountNumberRuleDefaults.forEntityType(type);
            assertThat(StructuredAccountNumberPatternValidator.validate(type, defaults.getFormatPattern(), defaults.getSequenceScope()))
                    .isEmpty();
        }
    }

    @Test
    public void checkDigitMustBeLastAndSequenceIsRequired() {
        assertThat(StructuredAccountNumberPatternValidator.validate(EntityAccountType.CLIENT, "{checkDigit:1}{sequence:4}",
                AccountNumberSequenceScope.GLOBAL)).isNotEmpty();
        assertThat(StructuredAccountNumberPatternValidator.validate(EntityAccountType.CLIENT, "{officeCode:3}",
                AccountNumberSequenceScope.GLOBAL)).isNotEmpty();
    }

    @Test
    public void officeScopeMustPrintTheOfficeCode() {
        assertThat(StructuredAccountNumberPatternValidator.validate(EntityAccountType.CLIENT, "{sequence:4}",
                AccountNumberSequenceScope.OFFICE)).isNotEmpty();
    }
}
