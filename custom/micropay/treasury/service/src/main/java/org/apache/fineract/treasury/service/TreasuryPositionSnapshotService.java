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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryLevelStatus;
import org.apache.fineract.treasury.domain.TreasuryPositionSnapshot;
import org.apache.fineract.treasury.domain.TreasuryPositionSnapshotRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the day-end position of every active treasury account.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryPositionSnapshotService {

    private final TreasuryAccountRepository accountRepository;
    private final TreasuryPositionSnapshotRepository snapshotRepository;
    private final TreasuryBalanceReader balanceReader;

    public record SnapshotSummary(LocalDate businessDate, int written, int belowMin, int aboveMax) {
    }

    /**
     * Snapshots the day being closed: the business date less one, the same day the GL balance snapshot job closes, so
     * the two agree when this job runs after it.
     */
    @Transactional
    public SnapshotSummary snapshotClosedDay() {
        return snapshot(DateUtils.getBusinessLocalDate().minusDays(1));
    }

    /**
     * One row per active account for the date, from the GL entries dated that day and before. A rerun for the same date
     * overwrites the rows.
     */
    @Transactional
    public SnapshotSummary snapshot(final LocalDate businessDate) {
        final List<TreasuryAccount> accounts = this.accountRepository.findByStatusOrderByIdAsc(TreasuryAccountStatus.ACTIVE);
        final Map<Long, BigDecimal> balances = this.balanceReader.glBalances(accounts.stream().map(TreasuryAccount::getId).toList(),
                businessDate);
        final OffsetDateTime now = DateUtils.getAuditOffsetDateTime();
        int belowMin = 0;
        int aboveMax = 0;
        for (final TreasuryAccount account : accounts) {
            final BigDecimal glBalance = balances.getOrDefault(account.getId(), BigDecimal.ZERO);
            final TreasuryLevelStatus levelStatus = TreasuryLevelStatus.of(glBalance, account.getMinBalance(), account.getMaxBalance());
            final TreasuryPositionSnapshot snapshot = this.snapshotRepository
                    .findByTreasuryAccountIdAndBusinessDate(account.getId(), businessDate).orElseGet(TreasuryPositionSnapshot::new);
            snapshot.setTreasuryAccountId(account.getId());
            snapshot.setBusinessDate(businessDate);
            snapshot.setGlBalance(glBalance);
            // in-transit transfers come with funding transfers (P1.2)
            snapshot.setPendingIn(BigDecimal.ZERO);
            snapshot.setPendingOut(BigDecimal.ZERO);
            snapshot.setLevelStatus(levelStatus);
            snapshot.setCreatedOnUtc(now);
            this.snapshotRepository.save(snapshot);
            if (levelStatus == TreasuryLevelStatus.BELOW_MIN) {
                belowMin++;
                log.warn("Treasury account {} ({}) is below its minimum on {}: balance {} {}, minimum {}", account.getCode(),
                        account.getId(), businessDate, account.getCurrencyCode(), glBalance, account.getMinBalance());
            } else if (levelStatus == TreasuryLevelStatus.ABOVE_MAX) {
                aboveMax++;
                log.warn("Treasury account {} ({}) is above its maximum on {}: balance {} {}, maximum {}", account.getCode(),
                        account.getId(), businessDate, account.getCurrencyCode(), glBalance, account.getMaxBalance());
            }
        }
        this.snapshotRepository.flush();
        log.info("Treasury position snapshot for {}: {} accounts, {} below minimum, {} above maximum", businessDate, accounts.size(),
                belowMin, aboveMax);
        return new SnapshotSummary(businessDate, accounts.size(), belowMin, aboveMax);
    }
}
