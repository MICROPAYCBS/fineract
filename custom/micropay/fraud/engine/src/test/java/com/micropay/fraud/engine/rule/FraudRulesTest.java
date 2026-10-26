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

import static org.assertj.core.api.Assertions.assertThat;

import com.micropay.fraud.engine.TestEvents;
import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import com.micropay.fraud.engine.feature.BehaviourFeatures;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FraudRulesTest {

    private static final FraudEngineProperties.Rules CONFIG = new FraudEngineProperties.Rules(Map.of("UGX", BigDecimal.valueOf(1_000_000)),
            Map.of("UGX", BigDecimal.valueOf(20_000_000)), 4.0, 10, 30, 180, 0.8, 3, 30, 0, 5);
    private static final Instant NOON = Instant.parse("2026-10-20T09:00:00Z");

    private final FraudRules rules = new FraudRules();

    @Test
    void largeAmountNeedsBothHistoryAndMaterialAmount() {
        final TransactionEvent big = withdrawal(5_000_000);
        assertThat(rules.largeAmountRule().evaluate(big, features(5_000_000, true, 6.0, 0, 0, 0, 1L, 12, null, 0), CONFIG))
                .map(RuleHit::code).contains(FraudRules.LARGE_AMOUNT);
        assertThat(rules.largeAmountRule().evaluate(withdrawal(500_000), features(500_000, true, 6.0, 0, 0, 0, 1L, 12, null, 0), CONFIG))
                .isEmpty();
        assertThat(rules.largeAmountRule().evaluate(big, features(5_000_000, true, null, 0, 0, 0, 1L, 12, null, 0), CONFIG)).isEmpty();
    }

    @Test
    void firstLargeAmountFiresWithoutHistory() {
        assertThat(rules.firstLargeAmountRule().evaluate(withdrawal(6_000_000), features(6_000_000, true, null, 0, 0, 0, null, 12, null, 0),
                CONFIG)).map(RuleHit::code).contains(FraudRules.FIRST_LARGE_AMOUNT);
    }

    @Test
    void velocityCountsThisTransactionToo() {
        assertThat(rules.velocityRule().evaluate(withdrawal(1_000), features(1_000, true, null, 9, 9, 0, 0L, 12, null, 0), CONFIG))
                .map(RuleHit::code).contains(FraudRules.VELOCITY_HOUR);
        assertThat(rules.velocityRule().evaluate(withdrawal(1_000), features(1_000, true, null, 2, 29, 0, 0L, 12, null, 0), CONFIG))
                .map(RuleHit::code).contains(FraudRules.VELOCITY_DAY);
        assertThat(rules.velocityRule().evaluate(withdrawal(1_000), features(1_000, true, null, 2, 5, 0, 0L, 12, null, 0), CONFIG))
                .isEmpty();
    }

    @Test
    void dormantAccountWithdrawal() {
        assertThat(rules.dormantReactivationRule().evaluate(withdrawal(2_000_000),
                features(2_000_000, true, null, 0, 0, 0, 200L, 12, null, 0), CONFIG)).map(RuleHit::code)
                .contains(FraudRules.DORMANT_REACTIVATION);
        assertThat(rules.dormantReactivationRule().evaluate(deposit(2_000_000),
                features(2_000_000, false, null, 0, 0, 0, 200L, 12, null, 0), CONFIG)).isEmpty();
    }

    @Test
    void rapidInOutNeedsMostOfTheIncomingMoney() {
        assertThat(rules.rapidInOutRule().evaluate(withdrawal(4_500_000), features(4_500_000, true, null, 1, 1, 5_000_000, 0L, 12, null, 0),
                CONFIG)).map(RuleHit::code).contains(FraudRules.RAPID_IN_OUT);
        assertThat(rules.rapidInOutRule().evaluate(withdrawal(1_500_000), features(1_500_000, true, null, 1, 1, 5_000_000, 0L, 12, null, 0),
                CONFIG)).isEmpty();
    }

    @Test
    void structuringCountsNearThresholdAmounts() {
        assertThat(
                rules.structuringRule().evaluate(deposit(19_000_000), features(19_000_000, false, null, 2, 2, 0, 0L, 12, null, 3), CONFIG))
                .map(RuleHit::code).contains(FraudRules.STRUCTURING);
        assertThat(
                rules.structuringRule().evaluate(deposit(19_000_000), features(19_000_000, false, null, 1, 1, 0, 0L, 12, null, 2), CONFIG))
                .isEmpty();
    }

    @Test
    void newClientMovingLargeSums() {
        assertThat(rules.newClientHighValueRule().evaluate(deposit(8_000_000), features(8_000_000, false, null, 0, 0, 0, null, 12, 5L, 0),
                CONFIG)).map(RuleHit::code).contains(FraudRules.NEW_CLIENT_HIGH_VALUE);
        assertThat(rules.newClientHighValueRule().evaluate(deposit(8_000_000), features(8_000_000, false, null, 0, 0, 0, null, 12, 400L, 0),
                CONFIG)).isEmpty();
    }

    @Test
    void oddHoursOnlyForMaterialWithdrawals() {
        assertThat(rules.oddHoursRule().evaluate(withdrawal(2_000_000), features(2_000_000, true, null, 0, 0, 0, 1L, 3, null, 0), CONFIG))
                .map(RuleHit::code).contains(FraudRules.ODD_HOURS);
        assertThat(rules.oddHoursRule().evaluate(withdrawal(2_000_000), features(2_000_000, true, null, 0, 0, 0, 1L, 14, null, 0), CONFIG))
                .isEmpty();
    }

    @Test
    void smallEverydayTransactionsNeverAlert() {
        final TransactionEvent small = withdrawal(20_000);
        final BehaviourFeatures f = features(20_000, true, 8.0, 0, 0, 25_000, 400L, 2, 3L, 0);
        assertThat(rules.largeAmountRule().evaluate(small, f, CONFIG)).isEmpty();
        assertThat(rules.dormantReactivationRule().evaluate(small, f, CONFIG)).isEmpty();
        assertThat(rules.rapidInOutRule().evaluate(small, f, CONFIG)).isEmpty();
        assertThat(rules.oddHoursRule().evaluate(small, f, CONFIG)).isEmpty();
    }

    private static TransactionEvent withdrawal(long amount) {
        return TestEvents.savings(TransactionKind.WITHDRAWAL, 1L, 1L, amount, NOON);
    }

    private static TransactionEvent deposit(long amount) {
        return TestEvents.savings(TransactionKind.DEPOSIT, 1L, 1L, amount, NOON);
    }

    private static BehaviourFeatures features(long amount, boolean debit, Double z, int lastHour, int lastDay, long creditsLastHour,
            Long daysSinceLast, int hour, Long clientAge, int nearThreshold) {
        return new BehaviourFeatures(BigDecimal.valueOf(amount), debit, z, 10, lastHour, lastDay, BigDecimal.valueOf(creditsLastHour),
                daysSinceLast, hour, clientAge, nearThreshold);
    }
}
