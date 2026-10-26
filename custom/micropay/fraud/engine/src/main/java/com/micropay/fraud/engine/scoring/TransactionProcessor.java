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
package com.micropay.fraud.engine.scoring;

import com.micropay.fraud.engine.alert.AlertRepository;
import com.micropay.fraud.engine.anomaly.AnomalyDetector;
import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.ClientEvent;
import com.micropay.fraud.engine.event.FineractEvent;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import com.micropay.fraud.engine.feature.BehaviourFeatures;
import com.micropay.fraud.engine.feature.FeatureService;
import com.micropay.fraud.engine.fineract.AccountOwnerResolver;
import com.micropay.fraud.engine.fineract.FineractClient;
import com.micropay.fraud.engine.store.ClientProfileRepository;
import com.micropay.fraud.engine.store.ProcessedEventRepository;
import com.micropay.fraud.engine.store.TransactionRecord;
import com.micropay.fraud.engine.store.TransactionRepository;
import com.micropay.fraud.engine.watchlist.WatchlistService;
import java.time.Clock;
import java.util.Optional;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The pipeline for one event: skip duplicates, find the client, compute features against history, score, store, and
 * raise an alert when the score reaches the threshold. Client events are stored and screened against the watchlists.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TransactionProcessor {

    private final ProcessedEventRepository processedEvents;
    private final ClientProfileRepository clients;
    private final TransactionRepository transactions;
    private final AlertRepository alerts;
    private final AccountOwnerResolver ownerResolver;
    private final FeatureService featureService;
    private final AnomalyDetector anomalyDetector;
    private final RiskScorer riskScorer;
    private final FineractClient fineract;
    private final WatchlistService watchlist;
    private final FraudEngineProperties properties;
    private final Clock clock;

    /**
     * @return the assessment for a scored transaction, empty for duplicates, client events and reversals
     */
    @Transactional
    public Optional<RiskAssessment> process(FineractEvent event) {
        if (!processedEvents.markProcessed(event.tenantId(), event.eventKey(), clock.instant())) {
            log.debug("Skipping already processed event {} of tenant {}", event.eventKey(), event.tenantId());
            return Optional.empty();
        }
        return switch (event) {
            case ClientEvent client -> {
                clients.save(client, clock.instant());
                if (watchlist.isEnabled()) {
                    clients.findSubject(client.tenantId(), client.clientId()).ifPresent(watchlist::screenClient);
                }
                yield Optional.empty();
            }
            case TransactionEvent txn -> processTransaction(txn);
        };
    }

    private Optional<RiskAssessment> processTransaction(TransactionEvent raw) {
        final TransactionEvent event = ownerResolver.resolve(raw);
        if (event.kind() == TransactionKind.REVERSAL) {
            transactions.insert(new TransactionRecord(event, 0, null, null));
            return Optional.empty();
        }
        final BehaviourFeatures features = featureService.compute(event);
        final OptionalDouble anomaly = anomalyDetector.score(event.tenantId(), features);
        final RiskAssessment risk = riskScorer.assess(event, features, anomaly);

        final Long transactionRef = transactions
                .insert(new TransactionRecord(event, risk.score(), risk.anomalyScore(), BehaviourFeatures.encode(features.vector())));
        if (risk.alert()) {
            final Long alertId = alerts.insert(transactionRef, event, risk, clock.instant());
            log.info("Alert {} for {} account {} of tenant {}: score {} ({})", alertId, event.accountType(), event.accountId(),
                    event.tenantId(), risk.score(), risk.reasons().get(0).code());
            maybeHold(alertId, event, risk);
        }
        return Optional.of(risk);
    }

    private void maybeHold(Long alertId, TransactionEvent event, RiskAssessment risk) {
        final FraudEngineProperties.AutoHold autoHold = properties.autoHold();
        if (!autoHold.enabled() || risk.score() < autoHold.minScore()) {
            return;
        }
        final String entityType = event.accountType() == AccountType.SAVINGS ? "SAVINGS_ACCOUNT" : "LOAN";
        final String reason = "Fraud engine alert " + alertId + " (score " + risk.score() + "): " + risk.reasons().get(0).detail();
        fineract.placeHold(event.tenantId(), entityType, event.accountId(), reason, "FRAUD_ENGINE", "fraud-alert-" + alertId)
                .ifPresent(holdId -> alerts.recordHold(alertId, holdId, clock.instant()));
    }
}
