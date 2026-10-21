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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberFormatPatternParser.FormatSegment;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;

public final class StructuredAccountNumberPatternValidator {

    public static final int MAX_ACCOUNT_NUMBER_LENGTH = 34;

    private static final Pattern WHOLE_PATTERN = Pattern.compile(
            "^(\\{(officeCode|regionCode|branchType|productCode|clientTypeCode|entityTypeCode|sequence|checkDigit):[1-9][0-9]?\\})+$");

    private StructuredAccountNumberPatternValidator() {}

    public static List<ApiParameterError> validate(final EntityAccountType type, final String pattern,
            final AccountNumberSequenceScope scope) {
        final List<ApiParameterError> errors = new ArrayList<>();
        if (pattern == null || !WHOLE_PATTERN.matcher(pattern).matches()) {
            errors.add(patternError("invalid.segments",
                    "Pattern must be {token:width} segments using officeCode, regionCode, branchType, productCode, clientTypeCode, "
                            + "entityTypeCode, sequence or checkDigit, each 1 to 99 wide.",
                    pattern));
            return errors;
        }
        final List<FormatSegment> segments = AccountNumberFormatPatternParser.parse(pattern);
        final Set<String> notAllowed = tokensNotAllowed(type);
        segments.stream().map(FormatSegment::getToken).filter(notAllowed::contains).distinct()
                .forEach(token -> errors.add(patternError("token.not.allowed",
                        "Segment `" + token + "` cannot be used in " + type.name() + " account numbers.", pattern)));
        if (segments.stream().filter(FormatSegment::isSequence).count() != 1) {
            errors.add(patternError("sequence.count", "Pattern must contain exactly one sequence segment.", pattern));
        }
        final long checkDigits = segments.stream().filter(FormatSegment::isCheckDigit).count();
        if (checkDigits > 1 || (checkDigits == 1 && !segments.get(segments.size() - 1).isCheckDigit())) {
            errors.add(patternError("check.digit.position", "The checkDigit segment may appear once, as the last segment.", pattern));
        }
        final int totalWidth = segments.stream().mapToInt(FormatSegment::getWidth).sum();
        final int maxWidth = maxWidth(type);
        if (totalWidth > maxWidth) {
            errors.add(patternError("too.long",
                    "Total width is " + totalWidth + "; the maximum for " + type.name() + " is " + maxWidth + ".", pattern));
        }
        final boolean printsOffice = segments.stream().anyMatch(segment -> "officeCode".equals(segment.getToken()));
        final boolean printsProduct = segments.stream().anyMatch(segment -> "productCode".equals(segment.getToken()));
        if ((scope == AccountNumberSequenceScope.OFFICE || scope == AccountNumberSequenceScope.OFFICE_PRODUCT) && !printsOffice) {
            errors.add(scopeError("needs.office.code", "A counter per office needs an officeCode segment in the pattern.", scope));
        }
        if (scope == AccountNumberSequenceScope.OFFICE_PRODUCT) {
            if (!isProductType(type)) {
                errors.add(
                        scopeError("not.allowed", "A counter per office and product is only for loan, savings and share accounts.", scope));
            } else if (!printsProduct) {
                errors.add(scopeError("needs.product.code", "A counter per office and product needs a productCode segment in the pattern.",
                        scope));
            }
        }
        return errors;
    }

    private static boolean isProductType(final EntityAccountType type) {
        return switch (type) {
            case LOAN, SAVINGS, SHARES, WORKING_CAPITAL_LOAN -> true;
            default -> false;
        };
    }

    private static Set<String> tokensNotAllowed(final EntityAccountType type) {
        return switch (type) {
            case CLIENT -> Set.of("productCode", "entityTypeCode");
            case LOAN, SAVINGS, SHARES, WORKING_CAPITAL_LOAN -> Set.of("clientTypeCode", "entityTypeCode");
            case CENTER, GROUP -> Set.of("productCode", "clientTypeCode");
        };
    }

    // Group loans (GLIM) and group savings (GSIM) append "1", so loan and savings allow one character less.
    private static int maxWidth(final EntityAccountType type) {
        return type == EntityAccountType.LOAN || type == EntityAccountType.SAVINGS ? MAX_ACCOUNT_NUMBER_LENGTH - 1
                : MAX_ACCOUNT_NUMBER_LENGTH;
    }

    private static ApiParameterError patternError(final String code, final String message, final String value) {
        return ApiParameterError.parameterErrorWithValue("validation.msg.accountNumberFormat.formatPattern." + code, message,
                AccountNumberFormatConstants.formatPatternParamName, value);
    }

    private static ApiParameterError scopeError(final String code, final String message, final AccountNumberSequenceScope scope) {
        return ApiParameterError.parameterErrorWithValue("validation.msg.accountNumberFormat.sequenceScope." + code, message,
                AccountNumberFormatConstants.sequenceScopeParamName, scope == null ? null : String.valueOf(scope.getValue()));
    }
}
