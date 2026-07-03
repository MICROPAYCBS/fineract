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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;
import org.apache.fineract.useradministration.domain.Role;

/**
 * Defines who may act at a stage: users holding the referenced Fineract role, optionally capped by a currency qualified
 * approval authority limit. Users whose limit is below the instance amount are not eligible.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_workflow_stage_participant")
public class WorkflowStageParticipant extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_stage_id", nullable = false)
    private WorkflowStage stage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "approval_limit_amount", precision = 19, scale = 6)
    private BigDecimal approvalLimitAmount;

    @Column(name = "approval_limit_currency", length = 3)
    private String approvalLimitCurrency;

    public static WorkflowStageParticipant create(final Role role, final BigDecimal approvalLimitAmount,
            final String approvalLimitCurrency) {
        final WorkflowStageParticipant participant = new WorkflowStageParticipant();
        participant.setRole(role);
        participant.setApprovalLimitAmount(approvalLimitAmount);
        participant.setApprovalLimitCurrency(approvalLimitCurrency);
        return participant;
    }
}
