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
package org.apache.fineract.accounting.glaccount.service;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.glaccount.exception.GlAccountCodeCategoryMismatchException;
import org.apache.fineract.accounting.glaccount.exception.GlAccountCodeHeaderMismatchException;
import org.apache.fineract.accounting.glaccount.exception.GlAccountInvalidCodeFormatException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class GlAccountStructuredCodeValidator {

    private final ConfigurationDomainService configurationDomainService;

    public void validateIfEnabled(final String glCode, final Integer typeId) {
        validateIfEnabled(glCode, typeId, null);
    }

    public void validateIfEnabled(final String glCode, final Integer typeId, final String parentGlCode) {
        if (!this.configurationDomainService.isStructuredGlCodesEnforced()) {
            return;
        }
        final int expectedLength = this.configurationDomainService.retrieveStructuredGlCodeLength();
        validate(glCode, typeId, expectedLength);
        validateHeaderDna(glCode, parentGlCode);
    }

    void validate(final String glCode, final Integer typeId, final int expectedLength) {
        if (StringUtils.isBlank(glCode) || typeId == null) {
            return;
        }
        final GLAccountType accountType = GLAccountType.fromInt(typeId);
        if (accountType == null) {
            return;
        }
        final String normalized = glCode.trim();
        if (normalized.length() != expectedLength || !StringUtils.isNumeric(normalized)) {
            throw new GlAccountInvalidCodeFormatException(normalized, expectedLength);
        }
        final int expectedCategoryDigit = accountType.getValue();
        if (Character.getNumericValue(normalized.charAt(0)) != expectedCategoryDigit) {
            throw new GlAccountCodeCategoryMismatchException(normalized, expectedCategoryDigit);
        }
    }

    void validateHeaderDna(final String glCode, final String parentGlCode) {
        if (StringUtils.isBlank(glCode) || StringUtils.isBlank(parentGlCode)) {
            return;
        }
        final String normalizedChild = glCode.trim();
        final String normalizedParent = parentGlCode.trim();
        final String headerStem = deriveHeaderStem(normalizedParent);
        if (normalizedChild.equals(normalizedParent) || !normalizedChild.startsWith(headerStem)) {
            throw new GlAccountCodeHeaderMismatchException(normalizedChild, normalizedParent, headerStem);
        }
    }

    static String deriveHeaderStem(final String glCode) {
        if (StringUtils.isBlank(glCode)) {
            return glCode;
        }
        final String normalized = glCode.trim();
        int lastNonZeroIndex = -1;
        for (int index = normalized.length() - 1; index >= 0; index--) {
            if (normalized.charAt(index) != '0') {
                lastNonZeroIndex = index;
                break;
            }
        }
        if (lastNonZeroIndex < 0) {
            return normalized.substring(0, 1);
        }
        if (lastNonZeroIndex == 0) {
            return normalized.substring(0, 1);
        }
        int trailingZeros = 0;
        for (int index = normalized.length() - 1; index >= 0 && normalized.charAt(index) == '0'; index--) {
            trailingZeros++;
        }
        final int stemEnd;
        if (trailingZeros >= 3) {
            stemEnd = normalized.length() - 3;
        } else {
            stemEnd = normalized.length() - trailingZeros;
        }
        return normalized.substring(0, Math.max(stemEnd, 1));
    }
}
