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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import lombok.Getter;
import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;

public final class AccountNumberFormatPatternParser {

    private static final Pattern SEGMENT_PATTERN = Pattern.compile("\\{(\\w+):(\\d+)\\}");

    private AccountNumberFormatPatternParser() {
    }

    public static List<FormatSegment> parse(final String formatPattern) {
        final List<FormatSegment> segments = new ArrayList<>();
        final Matcher matcher = SEGMENT_PATTERN.matcher(formatPattern);
        while (matcher.find()) {
            segments.add(new FormatSegment(matcher.group(1), Integer.parseInt(matcher.group(2))));
        }
        return segments;
    }

    public static boolean requiresOfficeCode(final String formatPattern) {
        return formatPattern != null && formatPattern.contains("{officeCode:");
    }

    public static boolean containsSequenceSegment(final List<FormatSegment> segments) {
        return segments.stream().anyMatch(segment -> "sequence".equals(segment.getToken()));
    }

    public static boolean containsCheckDigitSegment(final List<FormatSegment> segments) {
        return segments.stream().anyMatch(segment -> "checkDigit".equals(segment.getToken()));
    }

    @Getter
    public static final class FormatSegment {

        private final String token;
        private final int width;

        public FormatSegment(final String token, final int width) {
            this.token = token;
            this.width = width;
        }

        public boolean isCheckDigit() {
            return "checkDigit".equals(this.token);
        }

        public boolean isSequence() {
            return "sequence".equals(this.token);
        }
    }
}
