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

import java.util.List;

/**
 * Scores how well two names match, from 0 to 100. Tokens are paired in any order by Jaro-Winkler similarity, so word
 * order, small spelling differences and initials are tolerated. A missing or extra middle name costs little; a wholly
 * different surname costs a lot.
 */
public final class NameMatcher {

    /** Token pairs less similar than this count as different words. */
    static final double TOKEN_FLOOR = 0.85;
    static final double INITIAL_SIMILARITY = 0.85;
    private static final double MAIN_WEIGHT = 0.8;

    private NameMatcher() {}

    /**
     * @return 0 to 100; 0 when fewer than two words match, except for names that are a single word on both sides
     */
    public static int score(List<String> query, List<String> candidate) {
        if (query.isEmpty() || candidate.isEmpty()) {
            return 0;
        }
        if (query.size() == 1 || candidate.size() == 1) {
            // A single word is too weak to pair loosely; only the same single word matches
            return query.size() == 1 && candidate.size() == 1 && query.get(0).equals(candidate.get(0)) ? 100 : 0;
        }
        final boolean[] usedQuery = new boolean[query.size()];
        final boolean[] used = new boolean[candidate.size()];
        double total = 0;
        int matched = 0;
        // Greedy pairing, strongest pairs first, so "kony joseph" pairs with "joseph kony"
        for (int round = 0; round < Math.min(query.size(), candidate.size()); round++) {
            double best = 0;
            int bestQ = -1;
            int bestC = -1;
            for (int q = 0; q < query.size(); q++) {
                if (usedQuery[q]) {
                    continue;
                }
                for (int c = 0; c < candidate.size(); c++) {
                    if (used[c]) {
                        continue;
                    }
                    final double sim = similarity(query.get(q), candidate.get(c));
                    if (sim > best) {
                        best = sim;
                        bestQ = q;
                        bestC = c;
                    }
                }
            }
            if (bestQ < 0) {
                break;
            }
            used[bestC] = true;
            usedQuery[bestQ] = true;
            total += best;
            matched++;
        }
        if (matched < 2) {
            return 0;
        }
        final double queryCoverage = total / query.size();
        final double candidateCoverage = total / candidate.size();
        final double higher = Math.max(queryCoverage, candidateCoverage);
        final double lower = Math.min(queryCoverage, candidateCoverage);
        return (int) Math.round(100 * (MAIN_WEIGHT * higher + (1 - MAIN_WEIGHT) * lower));
    }

    static double similarity(String a, String b) {
        if (a.equals(b)) {
            return 1.0;
        }
        if (a.length() == 1 || b.length() == 1) {
            return a.charAt(0) == b.charAt(0) ? INITIAL_SIMILARITY : 0;
        }
        final double jw = jaroWinkler(a, b);
        return jw >= TOKEN_FLOOR ? jw : 0;
    }

    static double jaroWinkler(String a, String b) {
        final int window = Math.max(0, Math.max(a.length(), b.length()) / 2 - 1);
        final boolean[] aMatched = new boolean[a.length()];
        final boolean[] bMatched = new boolean[b.length()];
        int matches = 0;
        for (int i = 0; i < a.length(); i++) {
            final int from = Math.max(0, i - window);
            final int to = Math.min(b.length() - 1, i + window);
            for (int j = from; j <= to; j++) {
                if (!bMatched[j] && a.charAt(i) == b.charAt(j)) {
                    aMatched[i] = true;
                    bMatched[j] = true;
                    matches++;
                    break;
                }
            }
        }
        if (matches == 0) {
            return 0;
        }
        int transpositions = 0;
        int k = 0;
        for (int i = 0; i < a.length(); i++) {
            if (aMatched[i]) {
                while (!bMatched[k]) {
                    k++;
                }
                if (a.charAt(i) != b.charAt(k)) {
                    transpositions++;
                }
                k++;
            }
        }
        final double m = matches;
        final double jaro = (m / a.length() + m / b.length() + (m - transpositions / 2.0) / m) / 3.0;
        int prefix = 0;
        while (prefix < Math.min(4, Math.min(a.length(), b.length())) && a.charAt(prefix) == b.charAt(prefix)) {
            prefix++;
        }
        return jaro + prefix * 0.1 * (1 - jaro);
    }
}
