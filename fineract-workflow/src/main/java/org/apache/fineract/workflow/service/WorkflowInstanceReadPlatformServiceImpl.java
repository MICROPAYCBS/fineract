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

import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.workflow.data.WorkflowInstanceData;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Transactional(readOnly = true)
public class WorkflowInstanceReadPlatformServiceImpl implements WorkflowInstanceReadPlatformService {

    private final WorkflowInstanceRepository workflowInstanceRepository;

    @Override
    public Optional<WorkflowInstanceData> retrieveByCommandSourceId(final Long commandSourceId) {
        return this.workflowInstanceRepository.findByCommandSourceId(commandSourceId).map(this::map);
    }

    private WorkflowInstanceData map(final WorkflowInstance instance) {
        return WorkflowInstanceData.builder() //
                .id(instance.getId()) //
                .commandSourceId(instance.getCommandSourceId()) //
                .workflowDefinitionId(instance.getWorkflowDefinition().getId()) //
                .taskPermissionCode(instance.getTaskPermissionCode()) //
                .currentStageCode(instance.getCurrentStageCode()) //
                .status(instance.getStatus().name()) //
                .build();
    }
}
