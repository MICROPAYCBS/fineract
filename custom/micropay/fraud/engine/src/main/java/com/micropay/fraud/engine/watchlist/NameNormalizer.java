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
package com.micropay.fraud.engine.watchlist;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Brings names into one comparable form: accents removed, lower case, punctuation dropped, and titles and company
 * suffixes taken out, so "Dr. José O'Brien" and "jose obrien" compare as equal.
 */
public final class NameNormalizer {

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern APOSTROPHES = Pattern.compile("['’`]");
    private static final Pattern NON_ALNUM = Pattern.compile("[^\\p{L}\\p{N}]+");
    private static final Set<String> NOISE = Set.of("mr", "mrs", "ms", "miss", "dr", "prof", "hon", "sir", "rev", "eng", "haji", "hajji",
            "alhaji", "sheikh", "shaykh", "ltd", "limited", "llc", "inc", "plc", "co", "corp", "corporation", "company", "gmbh", "sa",
            "the", "and");

    private NameNormalizer() {}

    public static List<String> tokens(String name) {
        if (name == null) {
            return List.of();
        }
        String value = Normalizer.normalize(name, Normalizer.Form.NFKD);
        value = MARKS.matcher(value).replaceAll("");
        value = APOSTROPHES.matcher(value).replaceAll("");
        value = NON_ALNUM.matcher(value.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
        if (value.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(value.split(" ")).filter(t -> !NOISE.contains(t)).toList();
    }

    public static String normalise(String name) {
        return String.join(" ", tokens(name));
    }
}
