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

import com.micropay.fraud.engine.event.PartyType;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * A local list kept as CSV, for lists with no feed of their own (a domestic sanctions or PEP list, an internal
 * blacklist). The first row names the columns: {@code uid}, {@code type} (PERSON or ENTITY), {@code name},
 * {@code aliases}, {@code birth_dates} and {@code programs}. Multiple values in a column are separated by {@code |}.
 * Lines starting with {@code #} are comments.
 */
public final class CsvListParser implements ListParser {

    @Override
    public List<WatchlistEntry> parse(String source, InputStream in) throws IOException {
        final List<WatchlistEntry> entries = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            final String header = nextLine(reader);
            if (header == null) {
                return entries;
            }
            final Map<String, Integer> columns = new HashMap<>();
            final List<String> names = split(stripBom(header));
            for (int i = 0; i < names.size(); i++) {
                columns.put(names.get(i).trim().toLowerCase(Locale.ROOT), i);
            }
            if (!columns.containsKey("uid") || !columns.containsKey("name")) {
                throw new IOException("CSV list " + source + " needs at least the columns uid and name");
            }
            String line;
            while ((line = nextLine(reader)) != null) {
                final List<String> row = split(line);
                final String uid = value(row, columns, "uid");
                final String name = value(row, columns, "name");
                if (uid == null || name == null) {
                    continue;
                }
                final List<String> birthDates = new ArrayList<>();
                for (String raw : multi(row, columns, "birth_dates")) {
                    birthDates.addAll(BirthDates.normalise(raw));
                }
                entries.add(new WatchlistEntry(source, uid, partyType(value(row, columns, "type")), name, multi(row, columns, "aliases"),
                        birthDates, multi(row, columns, "programs")));
            }
        }
        return entries;
    }

    /**
     * The next line that is neither blank nor a comment, or null at the end.
     */
    private static String nextLine(BufferedReader reader) throws IOException {
        String line;
        while ((line = reader.readLine()) != null) {
            final String trimmed = stripBom(line).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                return line;
            }
        }
        return null;
    }

    private static PartyType partyType(String type) {
        if (type == null) {
            return PartyType.UNKNOWN;
        }
        return switch (type.trim().toUpperCase(Locale.ROOT)) {
            case "PERSON", "INDIVIDUAL" -> PartyType.PERSON;
            case "ENTITY", "ORGANISATION", "ORGANIZATION", "COMPANY" -> PartyType.ENTITY;
            default -> PartyType.OTHER;
        };
    }

    private static String value(List<String> row, Map<String, Integer> columns, String column) {
        final Integer index = columns.get(column);
        if (index == null || index >= row.size()) {
            return null;
        }
        final String value = row.get(index).trim();
        return value.isEmpty() ? null : value;
    }

    private static List<String> multi(List<String> row, Map<String, Integer> columns, String column) {
        final String value = value(row, columns, column);
        return value == null ? List.of() : Arrays.stream(value.split("\\|")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == '﻿' ? line.substring(1) : line;
    }

    /**
     * Splits one CSV line, honouring double-quoted fields with doubled quotes inside.
     */
    static List<String> split(String line) {
        final List<String> fields = new ArrayList<>();
        final StringBuilder field = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            final char c = line.charAt(i);
            if (quoted) {
                if (c == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    field.append('"');
                    i++;
                } else if (c == '"') {
                    quoted = false;
                } else {
                    field.append(c);
                }
            } else if (c == '"') {
                quoted = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else {
                field.append(c);
            }
        }
        fields.add(field.toString());
        return fields;
    }
}
