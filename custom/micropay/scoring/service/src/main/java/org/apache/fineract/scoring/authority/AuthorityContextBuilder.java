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
package org.apache.fineract.scoring.authority;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.scoring.domain.ScoringDecision;
import org.apache.fineract.scoring.domain.ScoringDecisionRepository;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.scoring.domain.ScoringOverride;
import org.apache.fineract.scoring.service.ScoringService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads what the authority rules need for one loan. Reads are plain queries of committed data, like the scoring
 * inputs, so they do not wait on the approval transaction.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class AuthorityContextBuilder {

    private static final String APPROVED_AMOUNT_PARAM = "approvedLoanAmount";
    private static final int LOAN_STATUS_APPROVED = 200;
    private static final int LOAN_STATUS_ACTIVE = 300;

    private final JdbcTemplate jdbcTemplate;
    private final FromJsonHelper fromJsonHelper;
    private final ScoringDecisionRepository decisionRepository;
    private final ScoringService scoringService;

    /**
     * @param commandJson
     *            the held command's JSON, read for {@code approvedLoanAmount}; may be null
     * @return empty when the loan does not exist
     */
    @Transactional(readOnly = true)
    public Optional<AuthorityContext> build(final Long loanId, final String commandJson) {
        final List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
                "SELECT client_id, product_id, currency_code, principal_amount FROM m_loan WHERE id = ?", loanId);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        final Map<String, Object> loan = rows.getFirst();
        final Long clientId = loan.get("client_id") == null ? null : ((Number) loan.get("client_id")).longValue();
        final Long productId = ((Number) loan.get("product_id")).longValue();
        final BigDecimal amount = max(toBigDecimal(loan.get("principal_amount")), approvedAmount(commandJson));

        final Optional<ScoringDecision> decision = this.decisionRepository.findFirstByLoanIdOrderByIdDesc(loanId);
        final Optional<ScoringOverride> override = this.scoringService.findEffectiveOverride(loanId);
        final ScoringOutcome outcome = override.map(ScoringOverride::getToOutcome).orElse(decision.map(ScoringDecision::getOutcome).orElse(null));

        return Optional.of(new AuthorityContext(loanId, productId, (String) loan.get("currency_code"), amount,
                amount.add(otherExposure(clientId, loanId)), decision.map(ScoringDecision::getGrade).orElse(null), outcome,
                override.isPresent(), this.scoringService.findActiveConfig(productId).isPresent()));
    }

    private BigDecimal otherExposure(final Long clientId, final Long loanId) {
        if (clientId == null) {
            return BigDecimal.ZERO;
        }
        final BigDecimal outstanding = this.jdbcTemplate.queryForObject(
                "SELECT SUM(CASE WHEN loan_status_id = ? THEN principal_outstanding_derived ELSE approved_principal END) FROM m_loan"
                        + " WHERE client_id = ? AND loan_status_id IN (?, ?) AND id <> ?",
                BigDecimal.class, LOAN_STATUS_ACTIVE, clientId, LOAN_STATUS_ACTIVE, LOAN_STATUS_APPROVED, loanId);
        return outstanding == null ? BigDecimal.ZERO : outstanding;
    }

    private BigDecimal approvedAmount(final String commandJson) {
        if (StringUtils.isBlank(commandJson)) {
            return null;
        }
        try {
            final JsonElement element = this.fromJsonHelper.parse(commandJson);
            if (!element.isJsonObject() || !element.getAsJsonObject().has(APPROVED_AMOUNT_PARAM)) {
                return null;
            }
            return this.fromJsonHelper.extractBigDecimalWithLocaleNamed(APPROVED_AMOUNT_PARAM, element);
        } catch (RuntimeException e) {
            // the loan principal is used instead
            log.warn("Could not read {} from the held approval; using the loan principal", APPROVED_AMOUNT_PARAM, e);
            return null;
        }
    }

    private static BigDecimal toBigDecimal(final Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        return value instanceof BigDecimal decimal ? decimal : new BigDecimal(value.toString());
    }

    private static BigDecimal max(final BigDecimal principal, final BigDecimal approved) {
        return approved == null || principal.compareTo(approved) >= 0 ? principal : approved;
    }
}
