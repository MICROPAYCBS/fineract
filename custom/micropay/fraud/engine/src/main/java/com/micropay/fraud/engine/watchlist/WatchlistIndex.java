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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * An in-memory, read-only index of every listed name. Candidates are found through blocking keys (the first three
 * letters of each word), so a client is only compared with names that share at least one word start, then scored with
 * {@link NameMatcher}. Rebuilt whenever a list is reloaded.
 */
public final class WatchlistIndex {

    static final int KEY_LENGTH = 3;

    private final String version;
    private final List<WatchlistEntry> entries;
    private final List<IndexedName> names = new ArrayList<>();
    private final Map<String, List<Integer>> byKey = new HashMap<>();
    private final Map<String, List<Integer>> singleWord = new HashMap<>();

    private record IndexedName(WatchlistEntry entry, String name, List<String> tokens) {
    }

    public WatchlistIndex(String version, List<WatchlistEntry> entries) {
        this.version = version;
        this.entries = List.copyOf(entries);
        for (WatchlistEntry entry : this.entries) {
            final Set<String> seen = new HashSet<>();
            for (String name : entry.names()) {
                final List<String> tokens = NameNormalizer.tokens(name);
                if (tokens.isEmpty() || !seen.add(String.join(" ", tokens))) {
                    continue;
                }
                final int position = names.size();
                names.add(new IndexedName(entry, name, tokens));
                if (tokens.size() == 1) {
                    singleWord.computeIfAbsent(tokens.get(0), k -> new ArrayList<>()).add(position);
                } else {
                    for (String key : keys(tokens)) {
                        byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(position);
                    }
                }
            }
        }
    }

    public static WatchlistIndex empty() {
        return new WatchlistIndex("", List.of());
    }

    public String version() {
        return version;
    }

    public int entryCount() {
        return entries.size();
    }

    public int nameCount() {
        return names.size();
    }

    /**
     * @return the best match per listed entry at or above {@code threshold}, strongest first
     */
    public List<WatchlistMatch> search(ScreeningSubject subject, int threshold) {
        final List<String> tokens = NameNormalizer.tokens(subject.name());
        if (tokens.isEmpty()) {
            return List.of();
        }
        final Set<Integer> candidates = new HashSet<>();
        if (tokens.size() == 1) {
            candidates.addAll(singleWord.getOrDefault(tokens.get(0), List.of()));
        } else {
            for (String key : keys(tokens)) {
                candidates.addAll(byKey.getOrDefault(key, List.of()));
            }
        }
        final Map<WatchlistEntry, WatchlistMatch> best = new LinkedHashMap<>();
        for (Integer position : candidates) {
            final IndexedName candidate = names.get(position);
            if (!compatible(subject.partyType(), candidate.entry().partyType())) {
                continue;
            }
            final int nameScore = NameMatcher.score(tokens, candidate.tokens());
            if (nameScore == 0) {
                continue;
            }
            final BirthDateCheck dob = subject.partyType() == PartyType.ENTITY ? BirthDateCheck.UNKNOWN
                    : BirthDateCheck.of(subject.dateOfBirth(), candidate.entry().birthDates());
            final int score = dob.adjust(nameScore);
            if (score < threshold) {
                continue;
            }
            final WatchlistMatch match = new WatchlistMatch(candidate.entry(), candidate.name(), nameScore, dob, score);
            best.merge(candidate.entry(), match, (a, b) -> a.score() >= b.score() ? a : b);
        }
        return best.values().stream().sorted(Comparator.comparingInt(WatchlistMatch::score).reversed()).toList();
    }

    /**
     * Vessels and aircraft are never compared with clients; a person is not compared with an organisation.
     */
    private static boolean compatible(PartyType client, PartyType listed) {
        if (listed == PartyType.OTHER) {
            return false;
        }
        if (client == PartyType.PERSON || client == PartyType.ENTITY) {
            return listed == client || listed == PartyType.UNKNOWN;
        }
        return true;
    }

    private static Set<String> keys(List<String> tokens) {
        final Set<String> keys = new HashSet<>();
        for (String token : tokens) {
            if (token.length() >= 2) {
                keys.add(token.length() > KEY_LENGTH ? token.substring(0, KEY_LENGTH) : token);
            }
        }
        return keys;
    }
}
