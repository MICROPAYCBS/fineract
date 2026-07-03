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

/**
 * Routing between two stages. Transitions are evaluated in {@code sequenceNo} order; an optional amount range makes a
 * transition conditional (first match wins), enabling mid-flow branching such as routing only very large amounts to an
 * extra authorisation stage. A transition without an amount range is unconditional and acts as the default branch.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_workflow_transition")
public class WorkflowTransition extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "workflow_definition_id", nullable = false)
    private WorkflowDefinition workflowDefinition;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "from_stage_id", nullable = false)
    private WorkflowStage fromStage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "to_stage_id", nullable = false)
    private WorkflowStage toStage;

    @Column(name = "sequence_no", nullable = false)
    private Integer sequenceNo;

    @Column(name = "min_amount", precision = 19, scale = 6)
    private BigDecimal minAmount;

    @Column(name = "max_amount", precision = 19, scale = 6)
    private BigDecimal maxAmount;

    public static WorkflowTransition create(final WorkflowStage fromStage, final WorkflowStage toStage, final Integer sequenceNo,
            final BigDecimal minAmount, final BigDecimal maxAmount) {
        final WorkflowTransition transition = new WorkflowTransition();
        transition.setFromStage(fromStage);
        transition.setToStage(toStage);
        transition.setSequenceNo(sequenceNo);
        transition.setMinAmount(minAmount);
        transition.setMaxAmount(maxAmount);
        return transition;
    }

    public boolean matches(final BigDecimal amount) {
        if (this.minAmount == null && this.maxAmount == null) {
            return true;
        }
        if (amount == null) {
            return false;
        }
        if (this.minAmount != null && amount.compareTo(this.minAmount) < 0) {
            return false;
        }
        return this.maxAmount == null || amount.compareTo(this.maxAmount) <= 0;
    }
}
