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

import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.scoring.domain.ScoringAuthorityRule;
import org.apache.fineract.scoring.domain.ScoringAuthorityRuleRepository;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.service.WorkflowStageApplicability;
import org.apache.fineract.workflow.service.WorkflowStageRouter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Applies the approval authority matrix to held loan commands: stages whose authority rules do not match the loan are
 * skipped. Commands without a loan, and tasks with no rules, keep every stage.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringStageApplicability implements WorkflowStageApplicability {

    private final ScoringAuthorityRuleRepository ruleRepository;
    private final AuthorityContextBuilder contextBuilder;

    @Override
    public Set<String> stagesToSkip(final CommandSource commandSource, final WorkflowDefinition definition) {
        return route(commandSource.getLoanId(), commandSource.getCommandAsJson(), definition).map(AuthorityRoute::skipped).orElse(Set.of());
    }

    /**
     * @return empty when no authority rule applies to the definition's task or the loan cannot be read
     */
    public Optional<AuthorityRoute> route(final Long loanId, final String commandJson, final WorkflowDefinition definition) {
        if (loanId == null) {
            return Optional.empty();
        }
        final List<ScoringAuthorityRule> rules = this.ruleRepository
                .findByTaskPermissionCodeOrderByStageCodeAscIdAsc(definition.getTaskPermissionCode());
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        final List<String> stageCodes = WorkflowStageRouter.withoutSkips().chain(definition);
        return this.contextBuilder.build(loanId, commandJson).map(context -> AuthorityRuleEvaluator.evaluate(stageCodes, rules, context));
    }
}
