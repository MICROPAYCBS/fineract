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
package com.micropay.fraud.engine.feature;

import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.store.ClientProfileRepository;
import com.micropay.fraud.engine.store.Subject;
import com.micropay.fraud.engine.store.TransactionRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Computes {@link BehaviourFeatures} from the stored history, before the transaction itself is stored.
 */
@Service
@RequiredArgsConstructor
public class FeatureService {

    static final int MIN_HISTORY = 5;
    static final int HISTORY_WINDOW = 200;
    static final BigDecimal NEAR_THRESHOLD_SHARE = new BigDecimal("0.9");

    private final TransactionRepository transactions;
    private final ClientProfileRepository clients;
    private final FraudEngineProperties properties;

    public BehaviourFeatures compute(TransactionEvent event) {
        final Subject subject = Subject.of(event);
        final Instant at = event.occurredAt();
        final boolean debit = event.kind().isDebit();

        final List<BigDecimal> history = transactions.recentAmounts(subject, debit, at, HISTORY_WINDOW);
        final Double zScore = zScore(event.amount(), history);

        final int lastHour = transactions.countBetween(subject, at.minus(Duration.ofHours(1)), at);
        final int lastDay = transactions.countBetween(subject, at.minus(Duration.ofDays(1)), at);
        final BigDecimal creditsLastHour = transactions.sumCreditsBetween(subject, at.minus(Duration.ofHours(1)), at);

        final Long daysSinceLast = transactions.lastActivity(event.tenantId(), event.accountType(), event.accountId(), at)
                .map(last -> ChronoUnit.DAYS.between(last, at)).orElse(null);

        final int hour = at.atZone(properties.timeZone()).getHour();

        final Long clientAge = Optional.ofNullable(event.clientId()).flatMap(id -> clients.findActivationDate(event.tenantId(), id))
                .map(activated -> ChronoUnit.DAYS.between(activated, LocalDate.ofInstant(at, properties.timeZone()))).orElse(null);

        final BigDecimal threshold = properties.rules().reportingThresholdFor(event.currency());
        final BigDecimal nearLow = threshold.multiply(NEAR_THRESHOLD_SHARE);
        int nearThreshold = transactions.countAmountsInRange(subject, at.minus(Duration.ofDays(1)), at, nearLow, threshold);
        if (event.amount().compareTo(nearLow) >= 0 && event.amount().compareTo(threshold) < 0) {
            nearThreshold++;
        }

        return new BehaviourFeatures(event.amount(), debit, zScore, history.size(), lastHour, lastDay, creditsLastHour, daysSinceLast, hour,
                clientAge, nearThreshold);
    }

    static Double zScore(BigDecimal amount, List<BigDecimal> history) {
        if (history.size() < MIN_HISTORY) {
            return null;
        }
        double mean = 0;
        for (BigDecimal value : history) {
            mean += value.doubleValue();
        }
        mean /= history.size();
        double variance = 0;
        for (BigDecimal value : history) {
            final double diff = value.doubleValue() - mean;
            variance += diff * diff;
        }
        variance /= history.size() - 1;
        // A floor of 10% of the mean stops a perfectly regular history (identical amounts) from producing huge scores
        final double std = Math.max(Math.sqrt(variance), Math.abs(mean) * 0.1);
        return std == 0 ? null : (amount.doubleValue() - mean) / std;
    }
}
