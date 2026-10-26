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
package com.micropay.fraud.engine.anomaly;

import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.feature.BehaviourFeatures;
import com.micropay.fraud.engine.store.TransactionRepository;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Keeps one isolation forest per tenant, trained on that tenant's recent transactions and refreshed on a schedule.
 * Until a tenant has enough history the detector stays silent rather than guessing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetector {

    private static final long SEED = 42L;

    private final TransactionRepository transactions;
    private final FraudEngineProperties properties;
    private final Map<String, IsolationForest> models = new ConcurrentHashMap<>();
    private final Map<String, Long> attemptedAt = new ConcurrentHashMap<>();

    public OptionalDouble score(String tenantId, BehaviourFeatures features) {
        if (!properties.anomaly().enabled()) {
            return OptionalDouble.empty();
        }
        IsolationForest model = models.get(tenantId);
        if (model == null) {
            model = trainIfDue(tenantId);
        }
        return model == null ? OptionalDouble.empty() : OptionalDouble.of(model.score(features.vector()));
    }

    @Scheduled(fixedDelayString = "${fraud.anomaly.retrain-interval-ms:21600000}", initialDelayString = "${fraud.anomaly.retrain-interval-ms:21600000}")
    public void retrainAll() {
        if (!properties.anomaly().enabled()) {
            return;
        }
        for (String tenantId : transactions.tenants()) {
            train(tenantId);
        }
    }

    /**
     * Tenants without a model yet are retried at most every ten minutes, not on every transaction.
     */
    private IsolationForest trainIfDue(String tenantId) {
        final long now = System.currentTimeMillis();
        final Long last = attemptedAt.get(tenantId);
        if (last != null && now - last < 600_000) {
            return null;
        }
        attemptedAt.put(tenantId, now);
        return train(tenantId);
    }

    IsolationForest train(String tenantId) {
        final FraudEngineProperties.Anomaly config = properties.anomaly();
        final List<double[]> vectors = transactions.recentFeatureVectors(tenantId, config.maxTrainingSamples()).stream()
                .map(BehaviourFeatures::decode).toList();
        if (vectors.size() < config.minTrainingSamples()) {
            log.debug("Tenant {} has {} transactions, the anomaly model needs {}", tenantId, vectors.size(), config.minTrainingSamples());
            return null;
        }
        final IsolationForest model = IsolationForest.train(vectors, config.trees(), config.sampleSize(), SEED);
        models.put(tenantId, model);
        log.info("Trained anomaly model for tenant {} on {} transactions", tenantId, vectors.size());
        return model;
    }
}
