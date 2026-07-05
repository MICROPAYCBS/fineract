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
package org.apache.fineract.infrastructure.accountnumberformat.domain;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public final class StructuredAccountNumberRuleDefaults {

    private final String formatPattern;
    private final AccountNumberSequenceScope sequenceScope;
    private final CheckDigitAlgorithm checkDigitAlgorithm;

    public static StructuredAccountNumberRuleDefaults forEntityType(final EntityAccountType entityAccountType) {
        return switch (entityAccountType) {
            case CLIENT -> new StructuredAccountNumberRuleDefaults("{officeCode:3}{clientTypeCode:1}{sequence:8}{checkDigit:1}",
                    AccountNumberSequenceScope.OFFICE, CheckDigitAlgorithm.LUHN);
            case LOAN, SAVINGS, SHARES, WORKING_CAPITAL_LOAN -> new StructuredAccountNumberRuleDefaults(
                    "{officeCode:3}{productCode:2}{sequence:9}{checkDigit:1}", AccountNumberSequenceScope.OFFICE_PRODUCT,
                    CheckDigitAlgorithm.LUHN);
            case GROUP, CENTER -> new StructuredAccountNumberRuleDefaults("{officeCode:3}{entityTypeCode:1}{sequence:7}{checkDigit:1}",
                    AccountNumberSequenceScope.OFFICE, CheckDigitAlgorithm.LUHN);
        };
    }
}
