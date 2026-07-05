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

import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;

public final class CheckDigitCalculator {

    private CheckDigitCalculator() {
    }

    public static String calculate(final CheckDigitAlgorithm algorithm, final String body) {
        if (algorithm == null || algorithm == CheckDigitAlgorithm.NONE) {
            return "0";
        }
        final String numericBody = body.replaceAll("[^0-9A-Za-z]", "").toUpperCase();
        return switch (algorithm) {
            case LUHN -> String.valueOf(luhnCheckDigit(numericBody));
            case MOD10 -> String.valueOf(mod10CheckDigit(numericBody));
            case MOD11 -> String.valueOf(mod11CheckDigit(numericBody));
            default -> "0";
        };
    }

    private static int luhnCheckDigit(final String number) {
        int sum = 0;
        boolean alternate = true;
        for (int i = number.length() - 1; i >= 0; i--) {
            int n = Character.getNumericValue(number.charAt(i));
            if (alternate) {
                n *= 2;
                if (n > 9) {
                    n -= 9;
                }
            }
            sum += n;
            alternate = !alternate;
        }
        return (10 - (sum % 10)) % 10;
    }

    private static int mod10CheckDigit(final String number) {
        int sum = 0;
        for (int i = 0; i < number.length(); i++) {
            sum += Character.getNumericValue(number.charAt(i));
        }
        return sum % 10;
    }

    private static int mod11CheckDigit(final String number) {
        int sum = 0;
        int weight = 2;
        for (int i = number.length() - 1; i >= 0; i--) {
            sum += Character.getNumericValue(number.charAt(i)) * weight;
            weight = weight >= 7 ? 2 : weight + 1;
        }
        final int remainder = sum % 11;
        return remainder == 0 ? 0 : 11 - remainder;
    }

    public static String normalizeSegmentValue(final String rawValue, final int width, final boolean numericPadding) {
        if (StringUtils.isBlank(rawValue)) {
            return StringUtils.repeat(numericPadding ? "0" : " ", width);
        }
        String value = rawValue.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        if (value.length() > width) {
            value = value.substring(0, width);
        }
        if (numericPadding) {
            return StringUtils.leftPad(value, width, '0');
        }
        return StringUtils.rightPad(value, width, ' ');
    }
}
