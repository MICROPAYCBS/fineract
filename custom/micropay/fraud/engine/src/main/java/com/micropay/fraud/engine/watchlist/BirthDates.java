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

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lists write dates of birth in many ways ("12 Mar 1962", "1962-03-12", "circa 1960", "1958 to 1960"). They are kept as
 * {@code yyyy-MM-dd} when the day is known and {@code yyyy} otherwise.
 */
public final class BirthDates {

    private static final Pattern ISO = Pattern.compile("(?<!\\d)(\\d{4})-(\\d{2})-(\\d{2})(?!\\d)");
    private static final Pattern YEAR = Pattern.compile("\\b(1[89]\\d{2}|20\\d{2})\\b");
    private static final DateTimeFormatter DAY_MONTH_YEAR = new DateTimeFormatterBuilder().parseCaseInsensitive()
            .appendPattern("d MMM uuuu").toFormatter(Locale.ENGLISH);
    private static final int MAX_YEARS = 5;

    private BirthDates() {}

    public static List<String> normalise(String raw) {
        final List<String> dates = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return dates;
        }
        final String value = raw.trim();
        final Matcher iso = ISO.matcher(value);
        if (iso.find() && validDate(iso.group())) {
            dates.add(iso.group());
            return dates;
        }
        try {
            dates.add(LocalDate.parse(value, DAY_MONTH_YEAR).toString());
            return dates;
        } catch (DateTimeParseException e) {
            // Not a full date; fall back to the years it mentions
        }
        final Matcher year = YEAR.matcher(value);
        while (year.find() && dates.size() < MAX_YEARS) {
            if (!dates.contains(year.group())) {
                dates.add(year.group());
            }
        }
        return dates;
    }

    public static int year(String normalised) {
        return Integer.parseInt(normalised.substring(0, 4));
    }

    private static boolean validDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
