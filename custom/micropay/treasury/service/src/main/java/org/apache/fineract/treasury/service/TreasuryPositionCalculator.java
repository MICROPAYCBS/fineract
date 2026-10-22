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
package org.apache.fineract.treasury.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.apache.fineract.treasury.data.TreasuryAccountData;
import org.apache.fineract.treasury.data.TreasuryPositionData;
import org.apache.fineract.treasury.data.TreasuryPositionSummaryData;
import org.apache.fineract.treasury.domain.TreasuryLevelStatus;

/**
 * Builds positions from balances that were already read. Holds no state and reads nothing, so it is tested on its own.
 */
public final class TreasuryPositionCalculator {

    private TreasuryPositionCalculator() {}

    public record LastSnapshot(LocalDate businessDate, BigDecimal glBalance, String levelStatus) {
    }

    public record ReconStatus(LocalDate lastReconciledDate, long unreconciledCount) {
    }

    public static TreasuryPositionData position(final TreasuryAccountData account, final BigDecimal glBalance, final BigDecimal pendingIn,
            final BigDecimal pendingOut, final LastSnapshot lastSnapshot) {
        return position(account, glBalance, pendingIn, pendingOut, lastSnapshot, null);
    }

    public static TreasuryPositionData position(final TreasuryAccountData account, final BigDecimal glBalance, final BigDecimal pendingIn,
            final BigDecimal pendingOut, final LastSnapshot lastSnapshot, final ReconStatus recon) {
        final BigDecimal balance = zeroIfNull(glBalance);
        final BigDecimal in = zeroIfNull(pendingIn);
        final BigDecimal out = zeroIfNull(pendingOut);
        final TreasuryLevelStatus levelStatus = TreasuryLevelStatus.of(balance, account.minBalance(), account.maxBalance());
        return new TreasuryPositionData(account.id(), account.code(), account.name(), account.accountType(), account.officeId(),
                account.officeName(), account.currencyCode(), account.glAccountId(), account.glAccountCode(), balance, in, out,
                balance.add(in).subtract(out), account.minBalance(), account.maxBalance(), levelStatus.name(),
                lastSnapshot == null ? null : lastSnapshot.businessDate(), lastSnapshot == null ? null : lastSnapshot.glBalance(),
                lastSnapshot == null ? null : lastSnapshot.levelStatus(), recon == null ? null : recon.lastReconciledDate(),
                recon == null ? null : recon.unreconciledCount());
    }

    /**
     * Groups positions by office and currency, keeping the order in which each group is first met.
     */
    public static List<TreasuryPositionSummaryData> summarise(final List<TreasuryPositionData> positions) {
        final Map<List<Object>, List<TreasuryPositionData>> groups = new LinkedHashMap<>();
        for (final TreasuryPositionData position : positions) {
            groups.computeIfAbsent(List.of(position.officeId(), position.currencyCode()), key -> new ArrayList<>()).add(position);
        }
        final List<TreasuryPositionSummaryData> summaries = new ArrayList<>();
        for (final List<TreasuryPositionData> group : groups.values()) {
            final TreasuryPositionData first = group.getFirst();
            BigDecimal balance = BigDecimal.ZERO;
            BigDecimal pendingIn = BigDecimal.ZERO;
            BigDecimal pendingOut = BigDecimal.ZERO;
            int belowMin = 0;
            int aboveMax = 0;
            for (final TreasuryPositionData position : group) {
                balance = balance.add(position.balance());
                pendingIn = pendingIn.add(position.pendingIn());
                pendingOut = pendingOut.add(position.pendingOut());
                if (Objects.equals(TreasuryLevelStatus.BELOW_MIN.name(), position.levelStatus())) {
                    belowMin++;
                } else if (Objects.equals(TreasuryLevelStatus.ABOVE_MAX.name(), position.levelStatus())) {
                    aboveMax++;
                }
            }
            summaries.add(new TreasuryPositionSummaryData(first.officeId(), first.officeName(), first.currencyCode(), group.size(), balance,
                    pendingIn, pendingOut, balance.add(pendingIn).subtract(pendingOut), belowMin, aboveMax, List.copyOf(group)));
        }
        return summaries;
    }

    private static BigDecimal zeroIfNull(final BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
