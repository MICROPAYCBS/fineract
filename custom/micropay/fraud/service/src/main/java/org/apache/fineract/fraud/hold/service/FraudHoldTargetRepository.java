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
package org.apache.fineract.fraud.hold.service;

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountStatusType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountSubStatusEnum;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Read-only lookups on core Fineract tables for the clients, savings accounts and loans that holds are placed on.
 */
@Repository
@RequiredArgsConstructor
public class FraudHoldTargetRepository {

    private final JdbcTemplate jdbcTemplate;

    public boolean clientExists(Long clientId) {
        return exists("SELECT COUNT(*) FROM m_client WHERE id = ?", clientId);
    }

    public boolean savingsAccountExists(Long savingsId) {
        return exists("SELECT COUNT(*) FROM m_savings_account WHERE id = ?", savingsId);
    }

    public boolean loanExists(Long loanId) {
        return exists("SELECT COUNT(*) FROM m_loan WHERE id = ?", loanId);
    }

    public List<Long> findActiveSavingsAccountIdsOfClient(Long clientId) {
        return jdbcTemplate.queryForList("SELECT id FROM m_savings_account WHERE client_id = ? AND status_enum = ? ORDER BY id", Long.class,
                clientId, SavingsAccountStatusType.ACTIVE.getValue());
    }

    public boolean isSavingsAccountActive(Long savingsId) {
        return exists("SELECT COUNT(*) FROM m_savings_account WHERE id = ? AND status_enum = ?", savingsId,
                SavingsAccountStatusType.ACTIVE.getValue());
    }

    /**
     * True when debits are already blocked, either on their own or as part of a full account block.
     */
    public boolean areSavingsDebitsBlocked(Long savingsId) {
        final Optional<Integer> subStatus = jdbcTemplate
                .queryForList("SELECT sub_status_enum FROM m_savings_account WHERE id = ?", Integer.class, savingsId).stream().findFirst();
        return subStatus.map(
                s -> s.equals(SavingsAccountSubStatusEnum.BLOCK_DEBIT.getValue()) || s.equals(SavingsAccountSubStatusEnum.BLOCK.getValue()))
                .orElse(false);
    }

    public boolean isLoanFlaggedAsFraud(Long loanId) {
        return jdbcTemplate.queryForList("SELECT is_fraud FROM m_loan WHERE id = ?", Boolean.class, loanId).stream().findFirst()
                .orElse(false);
    }

    private boolean exists(String sql, Object... params) {
        final Integer count = jdbcTemplate.queryForObject(sql, Integer.class, params);
        return count != null && count > 0;
    }
}
