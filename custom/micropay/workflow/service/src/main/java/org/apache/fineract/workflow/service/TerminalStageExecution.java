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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.workflow.domain.WorkflowInstance;
import org.apache.fineract.workflow.domain.WorkflowInstanceRepository;
import org.apache.fineract.workflow.domain.WorkflowInstanceStatus;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Remembers the in-progress instance whose terminal approval is about to execute the held command.
 *
 * <p>
 * processCheckerApprove commits before that command runs, so the instance is marked completed only after approveEntry
 * returns. A failed execution disarms this holder and leaves the terminal stage retryable.
 */
@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TerminalStageExecution {

    private final WorkflowInstanceRepository workflowInstanceRepository;

    private final ThreadLocal<Long> armedInstanceId = new ThreadLocal<>();

    public void arm(final Long workflowInstanceId) {
        this.armedInstanceId.set(workflowInstanceId);
    }

    public void disarm() {
        this.armedInstanceId.remove();
    }

    @Transactional(propagation = Propagation.REQUIRED)
    public void completeIfArmed() {
        final Long instanceId = this.armedInstanceId.get();
        this.armedInstanceId.remove();
        if (instanceId == null) {
            return;
        }

        final WorkflowInstance instance = this.workflowInstanceRepository.findById(instanceId).orElse(null);
        if (instance == null) {
            log.warn("No workflow instance {} to complete after the held command succeeded", instanceId);
            return;
        }
        if (!instance.isInProgress()) {
            log.warn("Workflow instance {} is {} and was not marked completed", instanceId, instance.getStatus());
            return;
        }
        instance.setStatus(WorkflowInstanceStatus.COMPLETED);
        this.workflowInstanceRepository.saveAndFlush(instance);
    }
}
