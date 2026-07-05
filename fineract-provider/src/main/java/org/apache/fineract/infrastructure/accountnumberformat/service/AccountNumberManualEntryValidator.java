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

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepository;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.StructuredAccountNumberRuleDefaults;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberFormatPatternParser.FormatSegment;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountNumberManualEntryValidator {

    private final ConfigurationDomainService configurationDomainService;
    private final AccountNumberFormatRepository accountNumberFormatRepository;

    public void validateManualAccountNumberIfStructuredModeEnabled(final EntityAccountType entityAccountType, final String accountNumber) {
        if (StringUtils.isBlank(accountNumber) || !configurationDomainService.isStructuredAccountNumberFormatsEnabled()) {
            return;
        }
        final AccountNumberFormat accountNumberFormat = accountNumberFormatRepository
                .findOneByAccountTypeEnum(entityAccountType.getValue());
        if (accountNumberFormat != null && Boolean.FALSE.equals(accountNumberFormat.getStructuredEnabled())) {
            return;
        }
        final StructuredAccountNumberRuleDefaults defaults = StructuredAccountNumberRuleDefaults.forEntityType(entityAccountType);
        final String formatPattern = accountNumberFormat != null && StringUtils.isNotBlank(accountNumberFormat.getFormatPattern())
                ? accountNumberFormat.getFormatPattern()
                : defaults.getFormatPattern();
        final int expectedLength = AccountNumberFormatPatternParser.parse(formatPattern).stream().mapToInt(FormatSegment::getWidth).sum();
        if (accountNumber.length() != expectedLength) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterError(
                    "validation.msg.account.number.manual.invalid.length",
                    "Manual account number length must be " + expectedLength + " for structured format.", "accountNo", accountNumber)));
        }
    }
}
