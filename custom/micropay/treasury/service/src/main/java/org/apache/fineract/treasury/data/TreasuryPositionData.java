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
package org.apache.fineract.treasury.data;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Live position of one treasury account. {@code balance} is the GL balance (debits less credits) of the account's GL
 * account in its office and currency; {@code projectedBalance} adds transfers in transit. The level status compares
 * {@code balance} with the minimum and maximum. {@code lastReconciledDate} and {@code unreconciledCount} (statement
 * lines and GL entries not matched) come from the statement reconciliation.
 */
public record TreasuryPositionData(Long treasuryAccountId, String code, String name, String accountType, Long officeId, String officeName,
        String currencyCode, Long glAccountId, String glAccountCode, BigDecimal balance, BigDecimal pendingIn, BigDecimal pendingOut,
        BigDecimal projectedBalance, BigDecimal minBalance, BigDecimal maxBalance, String levelStatus, LocalDate lastSnapshotDate,
        BigDecimal lastSnapshotBalance, String lastSnapshotLevelStatus, LocalDate lastReconciledDate, Long unreconciledCount) {
}
