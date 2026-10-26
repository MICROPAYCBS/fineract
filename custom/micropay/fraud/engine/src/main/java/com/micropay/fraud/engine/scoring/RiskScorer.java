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

import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.feature.BehaviourFeatures;
import com.micropay.fraud.engine.rule.FraudRule;
import com.micropay.fraud.engine.rule.RuleHit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Runs the rules, adds the anomaly score, and combines them into one 0 to 100 risk score. Reasons combine like
 * independent probabilities (1 - product of (1 - score/100)), so two medium reasons together score higher than either
 * alone but never above 100.
 */
@Service
@RequiredArgsConstructor
public class RiskScorer {

    public static final String ANOMALY = "ANOMALY";

    private final List<FraudRule> rules;
    private final FraudEngineProperties properties;

    public RiskAssessment assess(TransactionEvent event, BehaviourFeatures features, OptionalDouble anomaly) {
        final List<RuleHit> hits = new ArrayList<>();
        for (FraudRule rule : rules) {
            rule.evaluate(event, features, properties.rules()).ifPresent(hits::add);
        }
        final Double anomalyScore = anomaly.isPresent() ? anomaly.getAsDouble() : null;
        if (anomalyScore != null && anomalyScore >= properties.anomaly().alertFrom()) {
            hits.add(new RuleHit(ANOMALY, anomalyContribution(anomalyScore),
                    String.format(Locale.ROOT, "Unusual compared with this tenant's transactions (anomaly score %.2f)", anomalyScore)));
        }
        hits.sort(Comparator.comparingInt(RuleHit::score).reversed());

        final int score = combine(hits);
        final FraudEngineProperties.Alerts alerts = properties.alerts();
        final Severity severity = score >= alerts.highFrom() ? Severity.HIGH
                : score >= alerts.mediumFrom() ? Severity.MEDIUM : Severity.LOW;
        return new RiskAssessment(score, severity, score >= alerts.threshold(), List.copyOf(hits), anomalyScore);
    }

    /**
     * 0.62 maps to 25 and 0.80 or more to 60: the model can raise an alert on its own only when it is quite sure.
     */
    static int anomalyContribution(double anomalyScore) {
        return (int) Math.round(Math.min(60, Math.max(0, 25 + (anomalyScore - 0.62) * (35 / 0.18))));
    }

    static int combine(List<RuleHit> hits) {
        double clean = 1.0;
        for (RuleHit hit : hits) {
            clean *= 1.0 - Math.min(100, Math.max(0, hit.score())) / 100.0;
        }
        return (int) Math.round((1.0 - clean) * 100);
    }
}
