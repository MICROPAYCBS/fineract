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
package com.micropay.fraud.engine.config;

import java.math.BigDecimal;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * All tunable settings of the engine, under the {@code fraud.*} prefix.
 */
@ConfigurationProperties(prefix = "fraud")
public record FraudEngineProperties(@DefaultValue("Africa/Kampala") ZoneId timeZone, @DefaultValue Kafka kafka, @DefaultValue Alerts alerts,
        @DefaultValue Rules rules, @DefaultValue Anomaly anomaly, @DefaultValue Fineract fineract, @DefaultValue AutoHold autoHold,
        @DefaultValue Api api, @DefaultValue Watchlist watchlist) {

    public record Kafka(@DefaultValue("external-events") String topic, @DefaultValue("fraud-engine") String groupId,
            @DefaultValue("true") boolean enabled) {
    }

    /**
     * Risk scores run 0 to 100. A transaction at or above {@code threshold} raises an alert.
     */
    public record Alerts(@DefaultValue("40") int threshold, @DefaultValue("60") int mediumFrom, @DefaultValue("80") int highFrom) {
    }

    /**
     * Amounts are per currency code (set in fraud-engine.properties); {@code DEFAULT} applies to any currency not
     * listed. {@code materialAmount} is the smallest amount worth an alert; {@code reportingThreshold} is the
     * regulatory large transaction reporting limit that structuring tries to stay under.
     */
    public record Rules(Map<String, BigDecimal> materialAmount, Map<String, BigDecimal> reportingThreshold,
            @DefaultValue("4.0") double largeAmountZScore, @DefaultValue("10") int velocityPerHour, @DefaultValue("30") int velocityPerDay,
            @DefaultValue("180") int dormantDays, @DefaultValue("0.8") double rapidOutShare, @DefaultValue("3") int structuringCount,
            @DefaultValue("30") int newClientDays, @DefaultValue("0") int oddHoursFrom, @DefaultValue("5") int oddHoursTo) {

        private static final BigDecimal FALLBACK_MATERIAL_AMOUNT = BigDecimal.valueOf(1_000_000);
        private static final BigDecimal FALLBACK_REPORTING_THRESHOLD = BigDecimal.valueOf(20_000_000);

        public BigDecimal materialAmountFor(String currency) {
            return lookup(materialAmount, currency, FALLBACK_MATERIAL_AMOUNT);
        }

        public BigDecimal reportingThresholdFor(String currency) {
            return lookup(reportingThreshold, currency, FALLBACK_REPORTING_THRESHOLD);
        }

        private static BigDecimal lookup(Map<String, BigDecimal> amounts, String currency, BigDecimal fallback) {
            if (amounts == null) {
                return fallback;
            }
            final BigDecimal amount = currency == null ? null : amounts.get(currency);
            return amount != null ? amount : amounts.getOrDefault("DEFAULT", fallback);
        }
    }

    /**
     * Isolation forest settings. The model only scores once a tenant has {@code minTrainingSamples} transactions.
     */
    public record Anomaly(@DefaultValue("true") boolean enabled, @DefaultValue("100") int trees, @DefaultValue("256") int sampleSize,
            @DefaultValue("500") int minTrainingSamples, @DefaultValue("20000") int maxTrainingSamples,
            @DefaultValue("0.62") double alertFrom, @DefaultValue("21600000") long retrainIntervalMs) {
    }

    /**
     * How the engine reaches Fineract: to find the client behind an account, and to place holds when auto-hold is on.
     */
    public record Fineract(@DefaultValue("false") boolean enabled,
            @DefaultValue("https://localhost:8443/fineract-provider/api/v1") String baseUrl, @DefaultValue("") String username,
            @DefaultValue("") String password) {
    }

    /**
     * Off by default: the baseline is to alert, not to block. When on, alerts at {@code minScore} or above put a fraud
     * hold on the account through Fineract's /fraud/holds API.
     */
    public record AutoHold(@DefaultValue("false") boolean enabled, @DefaultValue("90") int minScore) {
    }

    /**
     * The alerts API is closed unless {@code key} is set; callers send it in the X-API-Key header.
     */
    public record Api(@DefaultValue("") String key) {
    }

    /**
     * Watchlist screening. Clients are screened when Fineract reports them created or activated, and all clients again
     * after each refresh ({@code refreshCron}, in {@code fraud.time-zone}). A name scoring {@code matchThreshold} (0 to
     * 100) or more against a listed name raises an alert. {@code files} maps a list name to a local CSV file.
     *
     * @param tenants
     *            the Fineract tenants whose clients the nightly run copies from Fineract when {@code syncClients} is on
     * @param loadOnStartup
     *            run at startup when a configured list was never loaded, instead of waiting for the night
     * @param minRetainedShare
     *            a downloaded list with fewer entries than this share of the stored one is refused as a likely broken
     *            download
     */
    public record Watchlist(@DefaultValue("true") boolean enabled, @DefaultValue("85") int matchThreshold,
            @DefaultValue("0 0 2 * * *") String refreshCron, @DefaultValue Feed ofac, @DefaultValue Feed un, @DefaultValue Feed eu,
            Map<String, String> files, @DefaultValue("0.5") double minRetainedShare, @DefaultValue("true") boolean syncClients,
            @DefaultValue("default") List<String> tenants, @DefaultValue WatchlistHold autoHold,
            @DefaultValue("true") boolean loadOnStartup) {

        public Map<String, String> files() {
            return files == null ? Map.of() : files;
        }
    }

    /**
     * One published list. The URLs are set in fraud-engine.properties.
     */
    public record Feed(@DefaultValue("true") boolean enabled, String url) {
    }

    /**
     * Off by default. When on, a watchlist alert at {@code minScore} or above puts a fraud hold on the client.
     */
    public record WatchlistHold(@DefaultValue("false") boolean enabled, @DefaultValue("95") int minScore) {
    }
}
