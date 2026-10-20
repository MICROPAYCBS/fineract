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
package org.apache.fineract.scoring.service;

import static org.apache.fineract.scoring.api.ScoringApiConstants.DEFAULT_AUTHORITY_TASK;
import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.scoring.authority.AuthorityContext;
import org.apache.fineract.scoring.authority.AuthorityContextBuilder;
import org.apache.fineract.scoring.authority.AuthorityRoute;
import org.apache.fineract.scoring.authority.AuthorityRoute.StageRoute;
import org.apache.fineract.scoring.authority.AuthorityRuleEvaluator;
import org.apache.fineract.scoring.data.ApprovalRouteData;
import org.apache.fineract.scoring.data.ScoringAuthorityRuleData;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;
import org.apache.fineract.scoring.domain.ScoringAuthorityRuleRepository;
import org.apache.fineract.scoring.exception.ScoringAuthorityRuleNotFoundException;
import org.apache.fineract.scoring.exception.ScoringConfigurationException;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.service.WorkflowSelectionService;
import org.apache.fineract.workflow.service.WorkflowStageRouter;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringAuthorityReadPlatformServiceImpl implements ScoringAuthorityReadPlatformService {

    static final String KEPT_AS_ENTRY_STAGE = "KEPT_AS_ENTRY_STAGE";

    private final ScoringAuthorityRuleRepository ruleRepository;
    private final AuthorityContextBuilder contextBuilder;
    private final ObjectProvider<WorkflowSelectionService> workflowSelectionService;

    @Override
    public List<ScoringAuthorityRuleData> retrieveRules(final String taskPermissionCode) {
        final List<ScoringAuthorityRule> rules = StringUtils.isBlank(taskPermissionCode)
                ? this.ruleRepository.findAllByOrderByTaskPermissionCodeAscStageCodeAscIdAsc()
                : this.ruleRepository.findByTaskPermissionCodeOrderByStageCodeAscIdAsc(taskPermissionCode.toUpperCase());
        return rules.stream().map(ScoringAuthorityRuleData::from).toList();
    }

    @Override
    public ScoringAuthorityRuleData retrieveRule(final Long ruleId) {
        return this.ruleRepository.findById(ruleId).map(ScoringAuthorityRuleData::from)
                .orElseThrow(() -> new ScoringAuthorityRuleNotFoundException(ruleId));
    }

    @Override
    public ApprovalRouteData retrieveApprovalRoute(final Long loanId, final String taskPermissionCode) {
        final String task = StringUtils.isBlank(taskPermissionCode) ? DEFAULT_AUTHORITY_TASK : taskPermissionCode.toUpperCase();
        final AuthorityContext context = this.contextBuilder.build(loanId, null)
                .orElseThrow(() -> new ScoringConfigurationException("loan.not.found", "Loan " + loanId + " does not exist", loanId));
        final WorkflowSelectionService selection = this.workflowSelectionService.getIfAvailable();
        final Optional<WorkflowDefinition> definition = selection == null ? Optional.empty() : selection.selectWorkflow(task);

        final List<ApprovalRouteData.Stage> stages = new ArrayList<>();
        definition.ifPresent(workflow -> {
            final AuthorityRoute route = AuthorityRuleEvaluator.evaluate(WorkflowStageRouter.withoutSkips().chain(workflow),
                    this.ruleRepository.findByTaskPermissionCodeOrderByStageCodeAscIdAsc(task), context);
            final boolean noneRequired = route.stages().stream().noneMatch(StageRoute::required);
            for (int i = 0; i < route.stages().size(); i++) {
                final StageRoute stage = route.stages().get(i);
                final String name = workflow.findStageByCode(stage.stageCode()).map(WorkflowStage::getName).orElse(null);
                if (noneRequired && i == 0) {
                    stages.add(new ApprovalRouteData.Stage(stage.stageCode(), name, true, KEPT_AS_ENTRY_STAGE, List.of()));
                } else {
                    stages.add(new ApprovalRouteData.Stage(stage.stageCode(), name, stage.required(), stage.reason(), stage.matchedRuleIds()));
                }
            }
        });
        return new ApprovalRouteData(loanId, task, definition.map(WorkflowDefinition::getId).orElse(null), context.amount(),
                context.exposure(), context.grade(), context.outcome() == null ? null : context.outcome().name(), context.overridden(),
                stages);
    }
}
