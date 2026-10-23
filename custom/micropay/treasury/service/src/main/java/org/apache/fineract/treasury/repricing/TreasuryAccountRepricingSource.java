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
package org.apache.fineract.treasury.repricing;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryAccountType;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.service.TreasuryBalanceReader;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Treasury accounts at their GL balance (source OPENING): vaults, wallets and agent float pools are NON_SENSITIVE; bank
 * and central bank balances are spread by the {@code REPRICING_DIST} rows of source OPENING as ADMINISTERED, and are
 * NON_SENSITIVE otherwise (the default).
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryAccountRepricingSource implements RepricingSource {

    static final Set<TreasuryAccountType> INTEREST_BEARING = EnumSet.of(TreasuryAccountType.BANK, TreasuryAccountType.CENTRAL_BANK);

    private final TreasuryAccountRepository accountRepository;
    private final TreasuryBalanceReader balanceReader;

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.OPENING;
    }

    @Override
    public List<RepricingPosition> positions(final LadderContext context) {
        final List<TreasuryAccount> accounts = this.accountRepository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE).stream()
                .filter(a -> a.getCurrencyCode().equals(context.currencyCode())
                        && (context.officeId() == null || context.officeId().equals(a.getOfficeId())))
                .toList();
        final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(accounts.stream().map(TreasuryAccount::getId).toList(),
                context.asOf());
        final List<RepricingPosition> positions = new ArrayList<>();
        for (final TreasuryAccount account : accounts) {
            final BigDecimal balance = balances.getOrDefault(account.getId(), BigDecimal.ZERO);
            final String line = account.getAccountType().name();
            if (INTEREST_BEARING.contains(account.getAccountType())) {
                positions.addAll(RepricingDistribution.spread(context, LadderSourceCode.OPENING, line, null, null, RepricingSide.ASSET,
                        RateType.ADMINISTERED, RateType.NON_SENSITIVE, balance));
            } else {
                positions.add(new RepricingPosition(LadderSourceCode.OPENING, line, null, null, RepricingSide.ASSET, RateType.NON_SENSITIVE,
                        null, null, balance));
            }
        }
        return positions;
    }
}
