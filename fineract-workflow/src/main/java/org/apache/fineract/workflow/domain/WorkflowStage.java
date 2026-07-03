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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * A single step in an approval chain. Within a stage, {@code requiredApprovals} distinct approvals complete the stage
 * (N-of-M parallel approval); the {@code rejectionPolicy} determines when the stage - and with it the workflow instance
 * - is rejected.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_workflow_stage")
public class WorkflowStage extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @Column(name = "stage_code", nullable = false, length = 100)
    private String stageCode;

    @Column(name = "name", length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage_type", nullable = false, length = 20)
    private WorkflowStageType stageType;

    @Column(name = "required_approvals", nullable = false)
    private Integer requiredApprovals;

    @Enumerated(EnumType.STRING)
    @Column(name = "rejection_policy", nullable = false, length = 20)
    private WorkflowRejectionPolicy rejectionPolicy;

    @Column(name = "rejection_threshold")
    private Integer rejectionThreshold;

    @Enumerated(EnumType.STRING)
    @Column(name = "expiry_period_unit", length = 20)
    private WorkflowExpiryPeriodUnit expiryPeriodUnit;

    @Column(name = "expiry_period_value")
    private Integer expiryPeriodValue;

    @Column(name = "escalation_enabled", nullable = false)
    private boolean escalationEnabled;

    @Column(name = "escalation_target_stage_code", length = 100)
    private String escalationTargetStageCode;

    @Column(name = "allow_cross_branch_access", nullable = false)
    private boolean allowCrossBranchAccess;

    @Column(name = "require_distinct_approver", nullable = false)
    private boolean requireDistinctApprover;

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkflowStageParticipant> participants = new ArrayList<>();

    @OneToMany(mappedBy = "stage", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<WorkflowStageAction> actions = new ArrayList<>();

    public void addParticipant(final WorkflowStageParticipant participant) {
        participant.setStage(this);
        this.participants.add(participant);
    }

    public void addAction(final WorkflowStageAction action) {
        action.setStage(this);
        this.actions.add(action);
    }

    public boolean hasExpiry() {
        return this.expiryPeriodUnit != null && this.expiryPeriodValue != null;
    }
}
