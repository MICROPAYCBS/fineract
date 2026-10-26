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

import com.micropay.fraud.engine.alert.AlertRepository;
import com.micropay.fraud.engine.alert.WatchlistMatchData;
import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.fineract.FineractClient;
import com.micropay.fraud.engine.rule.RuleHit;
import com.micropay.fraud.engine.scoring.Severity;
import com.micropay.fraud.engine.store.ClientProfileRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Screens names against the loaded lists and turns matches into watchlist alerts.
 */
@Slf4j
@Service
public class WatchlistService {

    /** At most this many listed entries are recorded on one alert. */
    static final int MAX_MATCHES_PER_ALERT = 10;
    static final int HIGH_FROM = 95;
    static final int MEDIUM_FROM = 90;
    public static final String REASON_PREFIX = "WATCHLIST_";

    private final WatchlistRepository lists;
    private final ClientProfileRepository clients;
    private final AlertRepository alerts;
    private final FineractClient fineract;
    private final FraudEngineProperties.Watchlist config;
    private final Clock clock;
    private final AtomicReference<WatchlistIndex> index = new AtomicReference<>();
    private final Object alertLock = new Object();

    public WatchlistService(WatchlistRepository lists, ClientProfileRepository clients, AlertRepository alerts, FineractClient fineract,
            FraudEngineProperties properties, Clock clock) {
        this.lists = lists;
        this.clients = clients;
        this.alerts = alerts;
        this.fineract = fineract;
        this.config = properties.watchlist();
        this.clock = clock;
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    /**
     * Rebuilds the in-memory index from the stored lists.
     */
    public WatchlistIndex reloadIndex() {
        final WatchlistIndex fresh = new WatchlistIndex(version(lists.sources()), lists.loadAll());
        index.set(fresh);
        log.info("Watchlist index loaded: {} entries, {} names", fresh.entryCount(), fresh.nameCount());
        return fresh;
    }

    public WatchlistIndex index() {
        final WatchlistIndex current = index.get();
        return current != null ? current : reloadIndex();
    }

    /**
     * Checks a name without raising an alert, for example before a client is created.
     */
    public List<WatchlistMatch> check(ScreeningSubject subject) {
        return index().search(subject, config.matchThreshold());
    }

    /**
     * Screens a stored client and raises one alert for the listed entries it newly matches.
     *
     * @return the new alert's id, empty when nothing new matched
     */
    @Transactional
    public Optional<Long> screenClient(ScreeningSubject subject) {
        final WatchlistIndex current = index();
        final List<WatchlistMatch> found = current.search(subject, config.matchThreshold());
        final Instant now = clock.instant();
        Optional<Long> alertId = Optional.empty();
        if (!found.isEmpty()) {
            synchronized (alertLock) {
                final List<WatchlistMatch> fresh = found.stream().filter(
                        m -> !alerts.watchlistPairAlerted(subject.tenantId(), subject.clientId(), m.entry().source(), m.entry().uid()))
                        .limit(MAX_MATCHES_PER_ALERT).toList();
                if (!fresh.isEmpty()) {
                    alertId = Optional.of(raise(subject, fresh, now));
                }
            }
        }
        clients.markScreened(subject.tenantId(), subject.clientId(), current.version(), now);
        return alertId;
    }

    private Long raise(ScreeningSubject subject, List<WatchlistMatch> matches, Instant now) {
        final int top = matches.get(0).score();
        final Severity severity = top >= HIGH_FROM ? Severity.HIGH : top >= MEDIUM_FROM ? Severity.MEDIUM : Severity.LOW;
        final List<RuleHit> reasons = new ArrayList<>();
        final List<WatchlistMatchData> rows = new ArrayList<>();
        for (WatchlistMatch m : matches) {
            reasons.add(new RuleHit(REASON_PREFIX + m.entry().source(), m.score(), describe(subject, m)));
            rows.add(new WatchlistMatchData(m.entry().source(), m.entry().uid(), m.entry().primaryName(), m.matchedName(), subject.name(),
                    m.score(), m.birthDateCheck().name(), m.entry().programs()));
        }
        final Long alertId = alerts.insertWatchlist(subject.tenantId(), subject.clientId(), top, severity, reasons, rows, now);
        log.info("Watchlist alert {} for client {} of tenant {}: {} listed entries matched, best {} {} (score {})", alertId,
                subject.clientId(), subject.tenantId(), matches.size(), matches.get(0).entry().source(), matches.get(0).entry().uid(), top);
        maybeHold(alertId, subject, matches.get(0));
        return alertId;
    }

    private void maybeHold(Long alertId, ScreeningSubject subject, WatchlistMatch best) {
        final FraudEngineProperties.WatchlistHold hold = config.autoHold();
        if (!hold.enabled() || best.score() < hold.minScore()) {
            return;
        }
        final String reason = "Watchlist alert " + alertId + ": matches " + best.entry().source() + " entry " + best.entry().uid() + " ("
                + best.entry().primaryName() + "), score " + best.score();
        fineract.placeHold(subject.tenantId(), "CLIENT", subject.clientId(), reason, "WATCHLIST", "fraud-alert-" + alertId)
                .ifPresent(holdId -> alerts.recordHold(alertId, holdId, clock.instant()));
    }

    static String describe(ScreeningSubject subject, WatchlistMatch m) {
        final StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "'%s' matches %s entry %s '%s'", subject.name(), m.entry().source(), m.entry().uid(),
                m.entry().primaryName()));
        if (!m.matchedName().equals(m.entry().primaryName())) {
            sb.append(" as '").append(m.matchedName()).append('\'');
        }
        sb.append(", name score ").append(m.nameScore());
        sb.append(switch (m.birthDateCheck()) {
            case MATCH -> ", date of birth matches";
            case CLOSE -> ", date of birth within a year";
            case MISMATCH -> ", date of birth differs";
            case UNKNOWN -> ", date of birth not compared";
        });
        if (!m.entry().programs().isEmpty()) {
            sb.append("; listed under ").append(String.join(", ", m.entry().programs()));
        }
        return sb.toString();
    }

    /**
     * One value for the content of all loaded lists, so a client is re-screened whenever any list changes.
     */
    static String version(List<WatchlistSourceStatus> sources) {
        final StringBuilder sb = new StringBuilder();
        for (WatchlistSourceStatus s : sources) {
            if (s.version() != null) {
                sb.append(s.source()).append(':').append(s.version()).append(';');
            }
        }
        try {
            final byte[] hash = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
