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
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stored copies of the lists, so screening works across restarts and while a publisher's site is down.
 */
@Repository
@RequiredArgsConstructor
public class WatchlistRepository {

    private static final int MAX_TEXT = 500;
    private static final int MAX_ERROR = 500;
    private static final int BATCH = 1000;
    private static final String SEPARATOR = "|";

    private final JdbcTemplate jdbcTemplate;

    public List<WatchlistSourceStatus> sources() {
        return jdbcTemplate.query(
                "SELECT source, version, entry_count, loaded_at, checked_at, last_error FROM fe_watchlist_source ORDER BY source",
                (rs, rowNum) -> new WatchlistSourceStatus(rs.getString("source"), rs.getString("version"), rs.getInt("entry_count"),
                        instant(rs.getTimestamp("loaded_at")), instant(rs.getTimestamp("checked_at")), rs.getString("last_error")));
    }

    public WatchlistSourceStatus source(String source) {
        return sources().stream().filter(s -> s.source().equals(source)).findFirst().orElse(null);
    }

    /**
     * Replaces every stored entry of the snapshot's list in one transaction, so screening never sees half a list.
     */
    @Transactional
    public void replace(WatchlistSnapshot snapshot, Instant now) {
        final String source = snapshot.source();
        jdbcTemplate.update("DELETE FROM fe_watchlist_name WHERE source = ?", source);
        jdbcTemplate.update("DELETE FROM fe_watchlist_entry WHERE source = ?", source);
        final List<Object[]> entryRows = new ArrayList<>();
        final List<Object[]> nameRows = new ArrayList<>();
        final Set<String> uids = new HashSet<>();
        for (WatchlistEntry entry : snapshot.entries()) {
            final String uid = cut(entry.uid(), 100);
            if (!uids.add(uid)) {
                continue;
            }
            entryRows.add(new Object[] { source, uid, entry.partyType().name(), cut(entry.primaryName(), MAX_TEXT),
                    cut(join(entry.birthDates()), MAX_TEXT), cut(join(entry.programs()), MAX_TEXT) });
            for (String alias : entry.aliases()) {
                nameRows.add(new Object[] { source, uid, cut(alias, MAX_TEXT) });
            }
        }
        batch("INSERT INTO fe_watchlist_entry (source, entry_uid, party_type, primary_name, birth_dates, programs) VALUES (?, ?, ?, ?, ?, ?)",
                entryRows);
        batch("INSERT INTO fe_watchlist_name (source, entry_uid, name) VALUES (?, ?, ?)", nameRows);
        final int updated = jdbcTemplate.update(
                "UPDATE fe_watchlist_source SET version = ?, entry_count = ?, loaded_at = ?, checked_at = ?, last_error = NULL WHERE source = ?",
                snapshot.version(), entryRows.size(), Timestamp.from(now), Timestamp.from(now), source);
        if (updated == 0) {
            jdbcTemplate.update(
                    "INSERT INTO fe_watchlist_source (source, version, entry_count, loaded_at, checked_at) VALUES (?, ?, ?, ?, ?)", source,
                    snapshot.version(), entryRows.size(), Timestamp.from(now), Timestamp.from(now));
        }
    }

    /**
     * Records a check that loaded nothing new: the list was unchanged, or fetching it failed ({@code error} set).
     */
    public void markChecked(String source, Instant now, String error) {
        final String message = cut(error, MAX_ERROR);
        final int updated = jdbcTemplate.update("UPDATE fe_watchlist_source SET checked_at = ?, last_error = ? WHERE source = ?",
                Timestamp.from(now), message, source);
        if (updated == 0) {
            jdbcTemplate.update("INSERT INTO fe_watchlist_source (source, entry_count, checked_at, last_error) VALUES (?, 0, ?, ?)", source,
                    Timestamp.from(now), message);
        }
    }

    public List<WatchlistEntry> loadAll() {
        final Map<String, List<String>> aliases = new HashMap<>();
        jdbcTemplate.query("SELECT source, entry_uid, name FROM fe_watchlist_name", rs -> {
            aliases.computeIfAbsent(rs.getString("source") + SEPARATOR + rs.getString("entry_uid"), k -> new ArrayList<>())
                    .add(rs.getString("name"));
        });
        return jdbcTemplate.query("SELECT source, entry_uid, party_type, primary_name, birth_dates, programs FROM fe_watchlist_entry",
                (rs, rowNum) -> {
                    final String source = rs.getString("source");
                    final String uid = rs.getString("entry_uid");
                    return new WatchlistEntry(source, uid, PartyType.valueOf(rs.getString("party_type")), rs.getString("primary_name"),
                            aliases.getOrDefault(source + SEPARATOR + uid, List.of()), split(rs.getString("birth_dates")),
                            split(rs.getString("programs")));
                });
    }

    private void batch(String sql, List<Object[]> rows) {
        for (int from = 0; from < rows.size(); from += BATCH) {
            jdbcTemplate.batchUpdate(sql, rows.subList(from, Math.min(rows.size(), from + BATCH)));
        }
    }

    private static String join(List<String> values) {
        return values.isEmpty() ? null : String.join(SEPARATOR, values);
    }

    private static List<String> split(String value) {
        return value == null || value.isEmpty() ? List.of() : Arrays.asList(value.split("\\|"));
    }

    private static String cut(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
