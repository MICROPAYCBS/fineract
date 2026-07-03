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
package org.apache.fineract.workflow.service;

import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowDefinitionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowSelectionServiceImpl implements WorkflowSelectionService {

    private final WorkflowDefinitionRepository workflowDefinitionRepository;

    @Override
    public Optional<WorkflowDefinition> selectWorkflow(final String moduleName, final BigDecimal amount, final String currencyCode) {
        final List<WorkflowDefinition> activeDefinitions = this.workflowDefinitionRepository
                .findActiveByModuleNameOrderByPriorityDesc(moduleName);

        // Criteria-bearing workflows are examined first (in priority order); a criteria-less definition is the
        // module default and only wins when no criteria match.
        final Optional<WorkflowDefinition> criteriaMatch = activeDefinitions.stream() //
                .filter(WorkflowDefinition::hasSelectionCriteria) //
                .filter(definition -> definition.matches(amount, currencyCode)) //
                .findFirst();
        if (criteriaMatch.isPresent()) {
            return criteriaMatch;
        }
        return activeDefinitions.stream() //
                .filter(definition -> !definition.hasSelectionCriteria()) //
                .findFirst();
    }
}
