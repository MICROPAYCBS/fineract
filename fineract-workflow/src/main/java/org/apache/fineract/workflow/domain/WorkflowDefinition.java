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
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * Master template for a configurable approval workflow. A definition is anchored to a maker-checker task - a Fineract
 * permission code such as CREATE_LOAN. When multiple ACTIVE definitions exist for the same task, the highest priority
 * wins.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_workflow_definition")
public class WorkflowDefinition extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "task_permission_code", nullable = false, length = 100)
    private String taskPermissionCode;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private WorkflowDefinitionStatus status;

    @Column(name = "priority", nullable = false)
    private Integer priority;

    @OneToMany(mappedBy = "workflowDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<WorkflowStage> stages = new ArrayList<>();

    @OneToMany(mappedBy = "workflowDefinition", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sequenceNo")
    private List<WorkflowTransition> transitions = new ArrayList<>();

    public static WorkflowDefinition create(final String taskPermissionCode, final String name, final String description,
            final Integer priority) {
        final WorkflowDefinition definition = new WorkflowDefinition();
        definition.setTaskPermissionCode(taskPermissionCode);
        definition.setName(name);
        definition.setDescription(description);
        definition.setStatus(WorkflowDefinitionStatus.DRAFT);
        definition.setPriority(priority);
        return definition;
    }

    public void addStage(final WorkflowStage stage) {
        stage.setWorkflowDefinition(this);
        this.stages.add(stage);
    }

    public void addTransition(final WorkflowTransition transition) {
        transition.setWorkflowDefinition(this);
        this.transitions.add(transition);
    }

    public void clearStructure() {
        this.stages.clear();
        this.transitions.clear();
    }

    public Optional<WorkflowStage> findStageByCode(final String stageCode) {
        return this.stages.stream().filter(stage -> stage.getStageCode().equals(stageCode)).findFirst();
    }

    public boolean isActive() {
        return this.status == WorkflowDefinitionStatus.ACTIVE;
    }

    public boolean isDraft() {
        return this.status == WorkflowDefinitionStatus.DRAFT;
    }

    /**
     * The entry stage is the sole stage with no incoming transitions (validated at activation).
     */
    public Optional<String> findEntryStageCode() {
        final Set<String> stagesWithIncoming = new HashSet<>();
        for (final WorkflowTransition transition : this.transitions) {
            stagesWithIncoming.add(transition.getToStage().getStageCode());
        }
        return this.stages.stream().map(WorkflowStage::getStageCode).filter(code -> !stagesWithIncoming.contains(code)).findFirst();
    }

    public boolean isTerminalStage(final String stageCode) {
        return this.transitions.stream().noneMatch(transition -> transition.getFromStage().getStageCode().equals(stageCode));
    }

    public Optional<String> resolveNextStageCode(final String fromStageCode) {
        return this.transitions.stream() //
                .filter(transition -> transition.getFromStage().getStageCode().equals(fromStageCode)) //
                .sorted(Comparator.comparing(WorkflowTransition::getSequenceNo)) //
                .map(transition -> transition.getToStage().getStageCode()) //
                .findFirst();
    }
}
