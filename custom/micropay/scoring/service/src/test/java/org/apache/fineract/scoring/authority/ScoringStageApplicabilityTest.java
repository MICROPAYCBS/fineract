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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;
import org.apache.fineract.scoring.domain.ScoringAuthorityRuleRepository;
import org.apache.fineract.scoring.domain.ScoringOutcome;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.domain.WorkflowTransition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ScoringStageApplicabilityTest {

    private final ScoringAuthorityRuleRepository ruleRepository = mock(ScoringAuthorityRuleRepository.class);
    private final AuthorityContextBuilder contextBuilder = mock(AuthorityContextBuilder.class);
    private final ScoringStageApplicability applicability = new ScoringStageApplicability(ruleRepository, contextBuilder);
    private WorkflowDefinition definition;

    @BeforeEach
    void setUp() {
        definition = WorkflowDefinition.create("APPROVE_LOAN", "Loan approval", null, 0);
        final WorkflowStage manager = stage("BRANCH_MANAGER");
        final WorkflowStage committee = stage("CREDIT_COMMITTEE");
        definition.addStage(manager);
        definition.addStage(committee);
        definition.addTransition(WorkflowTransition.create(manager, committee, 1));
    }

    @Test
    void commandWithoutLoanKeepsEveryStage() {
        assertEquals(Set.of(), applicability.stagesToSkip(CommandSource.builder().build(), definition));
        verify(contextBuilder, never()).build(any(), any());
    }

    @Test
    void taskWithoutRulesKeepsEveryStage() {
        when(ruleRepository.findByTaskPermissionCodeOrderByStageCodeAscIdAsc("APPROVE_LOAN")).thenReturn(List.of());

        assertEquals(Set.of(), applicability.stagesToSkip(CommandSource.builder().loanId(5L).build(), definition));
        verify(contextBuilder, never()).build(any(), any());
    }

    @Test
    void stageWhoseRulesDoNotMatchIsSkipped() {
        final ScoringAuthorityRule rule = new ScoringAuthorityRule();
        rule.setStageCode("CREDIT_COMMITTEE");
        rule.setMinAmount(new BigDecimal("50000000"));
        when(ruleRepository.findByTaskPermissionCodeOrderByStageCodeAscIdAsc("APPROVE_LOAN")).thenReturn(List.of(rule));
        when(contextBuilder.build(5L, "{\"approvedLoanAmount\":1000000}")).thenReturn(Optional.of(new AuthorityContext(5L, 3L, "UGX",
                new BigDecimal("1000000"), new BigDecimal("1000000"), "A", ScoringOutcome.APPROVE, false, true)));

        final CommandSource command = CommandSource.builder().loanId(5L).commandAsJson("{\"approvedLoanAmount\":1000000}").build();

        assertEquals(Set.of("CREDIT_COMMITTEE"), applicability.stagesToSkip(command, definition));
    }

    @Test
    void unreadableLoanKeepsEveryStage() {
        final ScoringAuthorityRule rule = new ScoringAuthorityRule();
        rule.setStageCode("CREDIT_COMMITTEE");
        when(ruleRepository.findByTaskPermissionCodeOrderByStageCodeAscIdAsc("APPROVE_LOAN")).thenReturn(List.of(rule));
        when(contextBuilder.build(any(), any())).thenReturn(Optional.empty());

        assertEquals(Set.of(), applicability.stagesToSkip(CommandSource.builder().loanId(5L).build(), definition));
    }

    private static WorkflowStage stage(final String code) {
        final WorkflowStage stage = new WorkflowStage();
        stage.setStageCode(code);
        return stage;
    }
}
