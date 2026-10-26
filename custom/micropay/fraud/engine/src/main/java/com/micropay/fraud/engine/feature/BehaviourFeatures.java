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

import java.math.BigDecimal;
import java.util.Locale;

/**
 * What the engine knows about a transaction compared with the subject's past behaviour.
 *
 * @param amountZScore
 *            how many standard deviations the amount is above the subject's usual amount in the same direction; null
 *            with fewer than {@link FeatureService#MIN_HISTORY} past transactions
 * @param pastSameDirection
 *            how many past transactions in the same direction the z-score is based on
 * @param countLastHour
 *            the subject's transactions in the hour before this one
 * @param countLastDay
 *            the subject's transactions in the 24 hours before this one
 * @param creditsLastHour
 *            money that reached the subject in the hour before this one
 * @param daysSinceLastActivity
 *            days since the account last moved money; null for an account with no history
 * @param localHour
 *            hour of day in the engine's time zone
 * @param clientAgeDays
 *            days since the client was activated; null when unknown
 * @param nearThresholdLastDay
 *            transactions just under the reporting threshold in the last 24 hours, this one included
 */
public record BehaviourFeatures(BigDecimal amount, boolean debit, Double amountZScore, int pastSameDirection, int countLastHour,
        int countLastDay, BigDecimal creditsLastHour, Long daysSinceLastActivity, int localHour, Long clientAgeDays,
        int nearThresholdLastDay) {

    /**
     * The numeric vector the anomaly model sees. Log scales keep large amounts and counts from dominating.
     */
    public double[] vector() {
        final double hourAngle = 2 * Math.PI * localHour / 24.0;
        return new double[] { Math.log1p(amount.doubleValue()), amountZScore == null ? 0.0 : Math.max(-5, Math.min(amountZScore, 20)),
                Math.log1p(countLastHour), Math.log1p(countLastDay),
                Math.log1p(daysSinceLastActivity == null ? 0 : Math.min(daysSinceLastActivity, 365)), Math.sin(hourAngle),
                Math.cos(hourAngle), debit ? 1.0 : 0.0 };
    }

    public static String encode(double[] vector) {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(String.format(Locale.ROOT, "%.5f", vector[i]));
        }
        return sb.toString();
    }

    public static double[] decode(String encoded) {
        final String[] parts = encoded.split(",");
        final double[] vector = new double[parts.length];
        for (int i = 0; i < parts.length; i++) {
            vector[i] = Double.parseDouble(parts[i]);
        }
        return vector;
    }
}
