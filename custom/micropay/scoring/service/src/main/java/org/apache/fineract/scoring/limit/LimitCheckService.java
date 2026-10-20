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
package org.apache.fineract.scoring.limit;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.scoring.domain.ScoringClientLimit;
import org.apache.fineract.scoring.domain.ScoringClientLimitRepository;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringLimitPolicyRepository;
import org.apache.fineract.scoring.domain.ScoringLimitStatus;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.exception.ScoringLimitExceededException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Checks a loan against the pre-approved limits of its product. Under an ENFORCED policy a loan that does not fit, or
 * a client with no current limit, is refused; under ADVISORY the breach is only logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class LimitCheckService {

    private static final int LOAN_STATUS_APPROVED = 200;
    private static final int LOAN_STATUS_ACTIVE = 300;

    private final ScoringLimitPolicyRepository policyRepository;
    private final ScoringClientLimitRepository limitRepository;
    private final JdbcTemplate jdbcTemplate;

    public void check(final Long loanId, final Long clientId, final Long productId, final BigDecimal amount) {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        for (final ScoringLimitPolicy policy : this.policyRepository.findByLoanProductIdAndModeNot(productId, ScoringMode.OFF)) {
            final Optional<ScoringClientLimit> limit = currentLimit(clientId, policy.getId(), today);
            if (limit.isEmpty()) {
                breach(policy, new ScoringLimitExceededException(loanId, policy.getCode()));
                continue;
            }
            final BigDecimal used = usedOnProduct(clientId, productId, loanId);
            if (used.add(amount).compareTo(limit.get().getAmount()) > 0) {
                breach(policy, new ScoringLimitExceededException(loanId, policy.getCode(), limit.get().getAmount(), used, amount));
            }
        }
    }

    public Optional<ScoringClientLimit> currentLimit(final Long clientId, final Long policyId, final LocalDate today) {
        return this.limitRepository.findFirstByClientIdAndPolicyIdAndStatusOrderByIdDesc(clientId, policyId, ScoringLimitStatus.ACTIVE)
                .filter(limit -> limit.isValidOn(today));
    }

    /**
     * Outstanding principal of the client's active loans on the product plus the approved amount of loans not yet
     * disbursed, leaving out {@code loanId}.
     */
    public BigDecimal usedOnProduct(final Long clientId, final Long productId, final Long loanId) {
        final BigDecimal used = this.jdbcTemplate.queryForObject(
                "SELECT SUM(CASE WHEN loan_status_id = ? THEN principal_outstanding_derived ELSE approved_principal END) FROM m_loan"
                        + " WHERE client_id = ? AND product_id = ? AND loan_status_id IN (?, ?) AND id <> ?",
                BigDecimal.class, LOAN_STATUS_ACTIVE, clientId, productId, LOAN_STATUS_ACTIVE, LOAN_STATUS_APPROVED,
                loanId == null ? -1L : loanId);
        return used == null ? BigDecimal.ZERO : used;
    }

    private static void breach(final ScoringLimitPolicy policy, final ScoringLimitExceededException breach) {
        if (policy.getMode() == ScoringMode.ENFORCED) {
            throw breach;
        }
        log.warn("Advisory limit policy {}: {}", policy.getCode(), breach.getDefaultUserMessage());
    }
}
