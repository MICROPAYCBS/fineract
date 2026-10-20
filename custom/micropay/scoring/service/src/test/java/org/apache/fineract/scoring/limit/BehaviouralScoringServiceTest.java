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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringLimitPolicyRepository;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.apache.fineract.scoring.limit.BehaviouralScoringService.RunSummary;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;

class BehaviouralScoringServiceTest {

    @Test
    void oneFailingClientDoesNotStopTheRun() {
        final LocalDate today = LocalDate.of(2026, 10, 20);
        final ScoringLimitPolicyRepository policyRepository = mock(ScoringLimitPolicyRepository.class);
        final ClientLimitWriter writer = mock(ClientLimitWriter.class);
        final ScoringLimitPolicy policy = new ScoringLimitPolicy();
        policy.setId(11L);
        policy.setCode("AGENT_FLOAT");
        when(policyRepository.findByModeNot(ScoringMode.OFF)).thenReturn(List.of(policy));
        when(writer.scoreClient(eq(11L), eq(2L), any())).thenThrow(new IllegalStateException("bad datatable"));
        final BehaviouralScoringService service = spy(new BehaviouralScoringService(policyRepository, writer, mock(JdbcTemplate.class)));
        doReturn(List.of(1L, 2L, 3L)).when(service).segment(policy);

        final RunSummary summary;
        try (MockedStatic<DateUtils> dates = Mockito.mockStatic(DateUtils.class)) {
            dates.when(DateUtils::getBusinessLocalDate).thenReturn(today);
            summary = service.runAll();
        }

        assertEquals(new RunSummary(1, 2, 1), summary);
        verify(writer).scoreClient(11L, 3L, today);
    }
}
