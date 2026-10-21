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
package org.apache.fineract.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Runtime state for a maker-checker held command that is governed by a multi-stage approval workflow.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_workflow_instance")
public class WorkflowInstance extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "command_source_id", nullable = false, unique = true)
    private Long commandSourceId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "task_permission_code", nullable = false, length = 100)
    private String taskPermissionCode;

    @Column(name = "current_stage_code", nullable = false, length = 100)
    private String currentStageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkflowInstanceStatus status;

    public static WorkflowInstance create(final Long commandSourceId, final WorkflowDefinition workflowDefinition,
            final String entryStageCode) {
        final WorkflowInstance instance = new WorkflowInstance();
        instance.setCommandSourceId(commandSourceId);
        instance.setWorkflowDefinition(workflowDefinition);
        instance.setTaskPermissionCode(workflowDefinition.getTaskPermissionCode());
        instance.setCurrentStageCode(entryStageCode);
        instance.setStatus(WorkflowInstanceStatus.IN_PROGRESS);
        return instance;
    }

    public boolean isInProgress() {
        return this.status == WorkflowInstanceStatus.IN_PROGRESS;
    }
}
