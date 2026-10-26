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
package com.micropay.fraud.engine.rule;

import com.micropay.fraud.engine.config.FraudEngineProperties.Rules;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * The baseline rule set. Each rule only fires on amounts worth an alert (the per-currency material amount), so small
 * everyday transactions never alert on their own.
 */
@Configuration
public class FraudRules {

    public static final String LARGE_AMOUNT = "LARGE_AMOUNT";
    public static final String FIRST_LARGE_AMOUNT = "FIRST_LARGE_AMOUNT";
    public static final String VELOCITY_HOUR = "VELOCITY_HOUR";
    public static final String VELOCITY_DAY = "VELOCITY_DAY";
    public static final String DORMANT_REACTIVATION = "DORMANT_REACTIVATION";
    public static final String RAPID_IN_OUT = "RAPID_IN_OUT";
    public static final String STRUCTURING = "STRUCTURING";
    public static final String NEW_CLIENT_HIGH_VALUE = "NEW_CLIENT_HIGH_VALUE";
    public static final String ODD_HOURS = "ODD_HOURS";

    /**
     * Far above what this client usually moves.
     */
    @Bean
    FraudRule largeAmountRule() {
        return (event, f, config) -> {
            if (!isMaterial(event, config) || f.amountZScore() == null || f.amountZScore() < config.largeAmountZScore()) {
                return Optional.empty();
            }
            return hit(LARGE_AMOUNT, 45, "Amount is %.1f standard deviations above the usual %s amount (%d past transactions)",
                    f.amountZScore(), f.debit() ? "outgoing" : "incoming", f.pastSameDirection());
        };
    }

    /**
     * A big amount with almost no history to compare with.
     */
    @Bean
    FraudRule firstLargeAmountRule() {
        return (event, f, config) -> {
            final BigDecimal bar = config.materialAmountFor(event.currency()).multiply(BigDecimal.valueOf(5));
            if (f.amountZScore() != null || event.amount().compareTo(bar) < 0) {
                return Optional.empty();
            }
            return hit(FIRST_LARGE_AMOUNT, 35, "Amount %s is at least 5x the material amount with only %d past transactions to compare",
                    plain(event.amount()), f.pastSameDirection());
        };
    }

    /**
     * Many transactions in a short time, a sign of account takeover or scripted abuse.
     */
    @Bean
    FraudRule velocityRule() {
        return (event, f, config) -> {
            if (f.countLastHour() + 1 >= config.velocityPerHour()) {
                return hit(VELOCITY_HOUR, 40, "%d transactions within one hour", f.countLastHour() + 1);
            }
            if (f.countLastDay() + 1 >= config.velocityPerDay()) {
                return hit(VELOCITY_DAY, 30, "%d transactions within 24 hours", f.countLastDay() + 1);
            }
            return Optional.empty();
        };
    }

    /**
     * Money leaving an account that had been quiet for months.
     */
    @Bean
    FraudRule dormantReactivationRule() {
        return (event, f, config) -> {
            if (!event.kind().isDebit() || !isMaterial(event, config) || f.daysSinceLastActivity() == null
                    || f.daysSinceLastActivity() < config.dormantDays()) {
                return Optional.empty();
            }
            return hit(DORMANT_REACTIVATION, 50, "Withdrawal from an account inactive for %d days", f.daysSinceLastActivity());
        };
    }

    /**
     * Money that arrived and left again within the hour, the typical mule pattern.
     */
    @Bean
    FraudRule rapidInOutRule() {
        return (event, f, config) -> {
            if (event.kind() != TransactionKind.WITHDRAWAL || !isMaterial(event, config) || f.creditsLastHour().signum() <= 0) {
                return Optional.empty();
            }
            final BigDecimal share = event.amount().divide(f.creditsLastHour(), 4, RoundingMode.HALF_UP);
            if (share.doubleValue() < config.rapidOutShare()) {
                return Optional.empty();
            }
            return hit(RAPID_IN_OUT, 55, "Withdrew %s within an hour of receiving %s", plain(event.amount()), plain(f.creditsLastHour()));
        };
    }

    /**
     * Several amounts just under the reporting threshold in one day.
     */
    @Bean
    FraudRule structuringRule() {
        return (event, f, config) -> {
            if (f.nearThresholdLastDay() < config.structuringCount()) {
                return Optional.empty();
            }
            return hit(STRUCTURING, 60, "%d transactions just under the reporting threshold of %s within 24 hours",
                    f.nearThresholdLastDay(), plain(config.reportingThresholdFor(event.currency())));
        };
    }

    /**
     * A recently activated client already moving large sums.
     */
    @Bean
    FraudRule newClientHighValueRule() {
        return (event, f, config) -> {
            final BigDecimal bar = config.materialAmountFor(event.currency()).multiply(BigDecimal.valueOf(5));
            if (f.clientAgeDays() == null || f.clientAgeDays() >= config.newClientDays() || event.amount().compareTo(bar) < 0) {
                return Optional.empty();
            }
            return hit(NEW_CLIENT_HIGH_VALUE, 35, "Client activated %d days ago moved %s", f.clientAgeDays(), plain(event.amount()));
        };
    }

    /**
     * Money leaving in the middle of the night.
     */
    @Bean
    FraudRule oddHoursRule() {
        return (event, f, config) -> {
            if (!event.kind().isDebit() || !isMaterial(event, config) || f.localHour() < config.oddHoursFrom()
                    || f.localHour() >= config.oddHoursTo()) {
                return Optional.empty();
            }
            return hit(ODD_HOURS, 20, "Withdrawal at %02d:00 local time", f.localHour());
        };
    }

    private static boolean isMaterial(TransactionEvent event, Rules config) {
        return event.amount().compareTo(config.materialAmountFor(event.currency())) >= 0;
    }

    private static Optional<RuleHit> hit(String code, int score, String detail, Object... args) {
        return Optional.of(new RuleHit(code, score, String.format(Locale.ROOT, detail, args)));
    }

    private static String plain(BigDecimal amount) {
        return amount.stripTrailingZeros().toPlainString();
    }
}
