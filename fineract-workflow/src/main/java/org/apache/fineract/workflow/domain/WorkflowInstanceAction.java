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
 * Audit record of an action taken on a workflow instance at a particular stage.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_workflow_instance_action")
public class WorkflowInstanceAction extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_instance_id", nullable = false)
    private WorkflowInstance workflowInstance;

    @Column(name = "stage_code", nullable = false, length = 100)
    private String stageCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 20)
    private WorkflowApprovalAction action;

    @Column(name = "comment", length = 1000)
    private String comment;

    public static WorkflowInstanceAction create(final WorkflowInstance workflowInstance, final String stageCode,
            final WorkflowApprovalAction action, final String comment) {
        final WorkflowInstanceAction instanceAction = new WorkflowInstanceAction();
        instanceAction.setWorkflowInstance(workflowInstance);
        instanceAction.setStageCode(stageCode);
        instanceAction.setAction(action);
        instanceAction.setComment(comment);
        return instanceAction;
    }
}
