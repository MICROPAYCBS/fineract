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
import org.apache.fineract.infrastructure.accountnumberformat.data.AccountNumberGenerationContext;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.infrastructure.accountnumberformat.exception.AccountNumberSequenceExhaustedException;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberFormatPatternParser.FormatSegment;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AccountNumberFormatEngineImpl implements AccountNumberFormatEngine {

    private final AccountNumberSequenceWritePlatformService accountNumberSequenceWritePlatformService;

    @Override
    public String generate(final AccountNumberGenerationContext context) {
        final List<FormatSegment> segments = AccountNumberFormatPatternParser.parse(context.getFormatPattern());
        final StringBuilder bodyBeforeCheckDigit = new StringBuilder();

        for (final FormatSegment segment : segments) {
            if (segment.isCheckDigit()) {
                final String checkDigit = CheckDigitCalculator.calculate(context.getCheckDigitAlgorithm(), bodyBeforeCheckDigit.toString());
                bodyBeforeCheckDigit.append(CheckDigitCalculator.normalizeSegmentValue(checkDigit, segment.getWidth(), true));
            } else {
                bodyBeforeCheckDigit.append(resolveSegmentValue(context, segment));
            }
        }

        return bodyBeforeCheckDigit.toString();
    }

    @Override
    public String buildScopeKey(final AccountNumberGenerationContext context) {
        final String entityCode = context.getEntityAccountType().name();
        final AccountNumberSequenceScope scope = context.getSequenceScope();
        final List<FormatSegment> segments = AccountNumberFormatPatternParser.parse(context.getFormatPattern());
        return switch (scope) {
            case GLOBAL -> entityCode;
            case OFFICE -> entityCode + "|" + scopePart(segments, "officeCode", context.getOfficeCode(), "000");
            case OFFICE_PRODUCT -> entityCode + "|" + scopePart(segments, "officeCode", context.getOfficeCode(), "000") + "|"
                    + scopePart(segments, "productCode", context.getProductCode(), "00");
            default -> entityCode;
        };
    }

    // The counter is keyed by the value as printed, so codes that print the same share one counter.
    private String scopePart(final List<FormatSegment> segments, final String token, final String rawValue, final String fallback) {
        return segments.stream().filter(segment -> token.equals(segment.getToken())).findFirst()
                .map(segment -> CheckDigitCalculator.normalizeSegmentValue(rawValue, segment.getWidth(), "officeCode".equals(token)))
                .orElse(rawValue != null ? rawValue : fallback);
    }

    private String resolveSegmentValue(final AccountNumberGenerationContext context, final FormatSegment segment) {
        return switch (segment.getToken()) {
            case "officeCode" -> CheckDigitCalculator.normalizeSegmentValue(context.getOfficeCode(), segment.getWidth(), true);
            case "regionCode" -> CheckDigitCalculator.normalizeSegmentValue(context.getRegionCode(), segment.getWidth(), false);
            case "branchType" -> CheckDigitCalculator.normalizeSegmentValue(context.getBranchType(), segment.getWidth(), false);
            case "productCode" -> CheckDigitCalculator.normalizeSegmentValue(context.getProductCode(), segment.getWidth(), false);
            case "clientTypeCode" -> CheckDigitCalculator.normalizeSegmentValue(context.getClientTypeCode(), segment.getWidth(), false);
            case "entityTypeCode" -> CheckDigitCalculator.normalizeSegmentValue(context.getEntityTypeCode(), segment.getWidth(), true);
            case "sequence" -> {
                final String scopeKey = buildScopeKey(context);
                final long sequenceValue = context.isPreview()
                        ? accountNumberSequenceWritePlatformService.previewNextSequenceValue(scopeKey)
                        : accountNumberSequenceWritePlatformService.nextSequenceValue(scopeKey);
                if (String.valueOf(sequenceValue).length() > segment.getWidth()) {
                    throw new AccountNumberSequenceExhaustedException(scopeKey, segment.getWidth());
                }
                yield CheckDigitCalculator.normalizeSegmentValue(String.valueOf(sequenceValue), segment.getWidth(), true);
            }
            default -> StringUtils.repeat("0", segment.getWidth());
        };
    }
}
