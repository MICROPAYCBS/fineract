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
package com.micropay.fineract.infrastructure.service;

import com.micropay.fineract.platform.configuration.MicropayConfigurationService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepository;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.StructuredAccountNumberRuleDefaults;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberFormatPatternParser;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberFormatPatternParser.FormatSegment;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberManualEntryValidator;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.springframework.stereotype.Service;

/**
 * Enforces the configured structured account-number length when a caller supplies an account number by hand and the
 * {@code structured-account-number-formats} configuration is on.
 */
@Service
@RequiredArgsConstructor
public class MicropayAccountNumberManualEntryValidator implements AccountNumberManualEntryValidator {

    private final MicropayConfigurationService micropayConfigurationService;
    private final AccountNumberFormatRepository accountNumberFormatRepository;

    @Override
    public void validateManualAccountNumberIfStructuredModeEnabled(final EntityAccountType entityAccountType, final String accountNumber) {
        if (StringUtils.isBlank(accountNumber) || !micropayConfigurationService.isStructuredAccountNumberFormatsEnabled()) {
            return;
        }
        final AccountNumberFormat accountNumberFormat = accountNumberFormatRepository
                .findOneByAccountTypeEnum(entityAccountType.getValue());
        if (accountNumberFormat == null || !Boolean.TRUE.equals(accountNumberFormat.getStructuredEnabled())) {
            return;
        }
        final StructuredAccountNumberRuleDefaults defaults = StructuredAccountNumberRuleDefaults.forEntityType(entityAccountType);
        final String formatPattern = StringUtils.isNotBlank(accountNumberFormat.getFormatPattern()) ? accountNumberFormat.getFormatPattern()
                : defaults.getFormatPattern();
        final int expectedLength = AccountNumberFormatPatternParser.parse(formatPattern).stream().mapToInt(FormatSegment::getWidth).sum();
        if (accountNumber.length() != expectedLength) {
            throw new PlatformApiDataValidationException(List.of(ApiParameterError.parameterErrorWithValue(
                    "validation.msg.account.number.manual.invalid.length",
                    "Manual account number length must be " + expectedLength + " for structured format.", "accountNo", accountNumber)));
        }
    }
}
