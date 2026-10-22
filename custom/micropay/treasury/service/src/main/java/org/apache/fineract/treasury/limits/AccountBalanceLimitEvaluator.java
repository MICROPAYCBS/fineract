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
package org.apache.fineract.treasury.limits;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryLevelStatus;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.service.TreasuryBalanceReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Each active treasury account's GL balance on the as-of date, as the position snapshot reads it. With
 * {@code useOwnLevels} the account's own minimum and maximum apply ({@link TreasuryLevelStatus}, the P1.1 levels):
 * below the minimum or above the maximum is a HARD breach; otherwise the limit's levels apply. The position snapshot
 * job keeps flagging its rows and logging; only this limit writes account breaches, so nothing is alerted twice.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class AccountBalanceLimitEvaluator implements LimitEvaluator {

    private final TreasuryAccountRepository accountRepository;
    private final TreasuryBalanceReader balanceReader;

    @Override
    public LimitMetric metric() {
        return LimitMetric.ACCOUNT_BALANCE;
    }

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        final List<TreasuryAccount> accounts = this.accountRepository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE).stream()
                .filter(a -> a.getCurrencyCode().equals(context.currencyCode()))
                .filter(a -> limit.getScopeType() != LimitScopeType.OFFICE || a.getOfficeId().equals(limit.getScopeId()))
                .filter(a -> limit.getScopeType() != LimitScopeType.ACCOUNT || a.getId().equals(limit.getScopeId())).toList();
        final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(accounts.stream().map(TreasuryAccount::getId).toList(),
                context.asOfDate());
        final List<Observation> observations = new ArrayList<>();
        for (final TreasuryAccount account : accounts) {
            final Observation observation = observe(limit, account, balances.getOrDefault(account.getId(), BigDecimal.ZERO));
            if (observation != null) {
                observations.add(observation);
            }
        }
        return observations;
    }

    static Observation observe(final TreasuryLimit limit, final TreasuryAccount account, final BigDecimal balance) {
        final String label = account.getCode() + " (" + account.getName() + ")";
        final String detail = "GL balance " + LimitSubjects.amount(balance) + " " + account.getCurrencyCode();
        if (!limit.isUseOwnLevels()) {
            return Observation.of(LimitScopeType.ACCOUNT, account.getId(), label, balance, limit.getComparison(), limit.getWarningLevel(),
                    limit.getHardLevel(), "ACCOUNT", account.getId(), detail);
        }
        if (account.getMinBalance() == null && account.getMaxBalance() == null) {
            return null;
        }
        final TreasuryLevelStatus status = TreasuryLevelStatus.of(balance, account.getMinBalance(), account.getMaxBalance());
        final boolean max = status == TreasuryLevelStatus.ABOVE_MAX || status == TreasuryLevelStatus.OK && account.getMinBalance() == null;
        return new Observation(LimitScopeType.ACCOUNT, account.getId(), label, balance, max ? LimitComparison.MAX : LimitComparison.MIN,
                null, max ? account.getMaxBalance() : account.getMinBalance(), status.isBreach() ? LimitLevel.HARD : null, "ACCOUNT",
                account.getId(), detail + " (" + status.name() + "; min " + LimitSubjects.amount(account.getMinBalance()) + ", max "
                        + LimitSubjects.amount(account.getMaxBalance()) + ")");
    }
}
