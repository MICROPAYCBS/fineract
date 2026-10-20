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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.scoring.domain.ScoringClientLimit;
import org.apache.fineract.scoring.domain.ScoringClientLimitRepository;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringLimitPolicyRepository;
import org.apache.fineract.scoring.domain.ScoringLimitStatus;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.exception.ScoringLimitExceededException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class LimitCheckServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 20);
    private static final Long PRODUCT_ID = 3L;

    private final ScoringLimitPolicyRepository policyRepository = mock(ScoringLimitPolicyRepository.class);
    private final ScoringClientLimitRepository limitRepository = mock(ScoringClientLimitRepository.class);
    private LimitCheckService service;
    private ScoringLimitPolicy policy;

    @BeforeEach
    void setUp() {
        service = spy(new LimitCheckService(policyRepository, limitRepository, mock(JdbcTemplate.class)));
        policy = new ScoringLimitPolicy();
        policy.setId(11L);
        policy.setCode("AGENT_FLOAT");
        policy.setLoanProductId(PRODUCT_ID);
        policy.setMode(ScoringMode.ENFORCED);
        when(policyRepository.findByLoanProductIdAndModeNot(PRODUCT_ID, ScoringMode.OFF)).thenReturn(List.of(policy));
        doReturn(new BigDecimal("300000")).when(service).usedOnProduct(9L, PRODUCT_ID, 42L);
    }

    @Test
    void loanWithinLimitPasses() {
        limit("1000000", TODAY.minusDays(1), TODAY.plusDays(29));

        assertDoesNotThrow(() -> check("700000"));
    }

    @Test
    void loanOverLimitIsRefused() {
        limit("1000000", TODAY.minusDays(1), TODAY.plusDays(29));

        final ScoringLimitExceededException exception = assertThrows(ScoringLimitExceededException.class, () -> check("700001"));
        assertEquals("error.msg.scoring.limit.exceeded", exception.getGlobalisationMessageCode());
    }

    @Test
    void clientWithoutLimitIsRefused() {
        when(limitRepository.findFirstByClientIdAndPolicyIdAndStatusOrderByIdDesc(anyLong(), anyLong(), any())).thenReturn(Optional.empty());

        final ScoringLimitExceededException exception = assertThrows(ScoringLimitExceededException.class, () -> check("1"));
        assertEquals("error.msg.scoring.limit.missing", exception.getGlobalisationMessageCode());
    }

    @Test
    void expiredLimitCountsAsNoLimit() {
        limit("1000000", TODAY.minusDays(30), TODAY.minusDays(1));

        assertThrows(ScoringLimitExceededException.class, () -> check("1"));
    }

    @Test
    void advisoryPolicyOnlyLogs() {
        policy.setMode(ScoringMode.ADVISORY);
        limit("1000000", TODAY.minusDays(1), TODAY.plusDays(29));

        assertDoesNotThrow(() -> check("5000000"));
    }

    private void check(final String amount) {
        try (MockedStatic<DateUtils> dates = Mockito.mockStatic(DateUtils.class)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(TODAY);
            service.check(42L, 9L, PRODUCT_ID, new BigDecimal(amount));
        }
    }

    private void limit(final String amount, final LocalDate from, final LocalDate to) {
        final ScoringClientLimit limit = new ScoringClientLimit();
        limit.setAmount(new BigDecimal(amount));
        limit.setValidFrom(from);
        limit.setValidTo(to);
        limit.setStatus(ScoringLimitStatus.ACTIVE);
        when(limitRepository.findFirstByClientIdAndPolicyIdAndStatusOrderByIdDesc(eq(9L), eq(11L), eq(ScoringLimitStatus.ACTIVE)))
                .thenReturn(Optional.of(limit));
    }
}
