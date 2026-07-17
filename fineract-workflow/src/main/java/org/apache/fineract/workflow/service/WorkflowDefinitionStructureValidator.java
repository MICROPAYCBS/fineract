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

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.apache.fineract.workflow.domain.WorkflowRejectionPolicy;
import org.apache.fineract.workflow.domain.WorkflowStage;
import org.apache.fineract.workflow.domain.WorkflowTransition;
import org.apache.fineract.workflow.exception.WorkflowConfigurationException;
import org.springframework.stereotype.Component;

/**
 * Enforces the structural rules a workflow definition must satisfy before it can be activated:
 *
 * <ul>
 * <li>at least one stage; every stage has at least one enabled action including APPROVE</li>
 * <li>stage codes are unique within the definition</li>
 * <li>exactly one entry stage (a stage with no incoming transitions)</li>
 * <li>every stage is reachable from the entry stage</li>
 * <li>no circular transition routes</li>
 * <li>escalation targets reference existing stages (and not the stage itself) when escalation is enabled</li>
 * <li>rejection thresholds are consistent with the rejection policy</li>
 * </ul>
 */
@Component
public class WorkflowDefinitionStructureValidator {

    public void validateForActivation(final WorkflowDefinition definition) {
        final List<WorkflowStage> stages = definition.getStages();
        if (stages.isEmpty()) {
            throw new WorkflowConfigurationException("no.stages", "A workflow definition requires at least one stage");
        }

        final Set<String> stageCodes = new HashSet<>();
        for (final WorkflowStage stage : stages) {
            if (!stageCodes.add(stage.getStageCode())) {
                throw new WorkflowConfigurationException("duplicate.stage.code",
                        "Stage code " + stage.getStageCode() + " is used more than once", stage.getStageCode());
            }
        }

        for (final WorkflowStage stage : stages) {
            validateStage(definition, stage);
        }

        validateTransitions(definition);
    }

    private void validateStage(final WorkflowDefinition definition, final WorkflowStage stage) {
        if (stage.hasRoleRestriction() && Boolean.TRUE.equals(stage.getRole().isDisabled())) {
            throw new WorkflowConfigurationException("role.disabled",
                    "Stage " + stage.getStageCode() + " references disabled role " + stage.getRole().getName(), stage.getStageCode(),
                    stage.getRole().getName());
        }
        if (stage.getActions().isEmpty()) {
            throw new WorkflowConfigurationException("stage.without.actions", "Stage " + stage.getStageCode() + " has no enabled actions",
                    stage.getStageCode());
        }
        final boolean approveEnabled = stage.getActions().stream().anyMatch(action -> action.getAction() == WorkflowApprovalAction.APPROVE);
        if (!approveEnabled) {
            throw new WorkflowConfigurationException("stage.without.approve.action",
                    "Stage " + stage.getStageCode() + " must have the APPROVE action enabled", stage.getStageCode());
        }

        if (stage.getRejectionPolicy() == WorkflowRejectionPolicy.THRESHOLD && stage.getRejectionThreshold() == null) {
            throw new WorkflowConfigurationException("rejection.threshold.missing",
                    "Stage " + stage.getStageCode() + " uses the THRESHOLD rejection policy but defines no rejection threshold",
                    stage.getStageCode());
        }
        if (stage.getRejectionPolicy() != WorkflowRejectionPolicy.THRESHOLD && stage.getRejectionThreshold() != null) {
            throw new WorkflowConfigurationException("rejection.threshold.not.applicable",
                    "Stage " + stage.getStageCode() + " defines a rejection threshold but does not use the THRESHOLD rejection policy",
                    stage.getStageCode());
        }

        if (stage.isEscalationEnabled()) {
            if (!stage.hasExpiry()) {
                throw new WorkflowConfigurationException("escalation.without.expiry",
                        "Stage " + stage.getStageCode() + " enables escalation but defines no expiry period", stage.getStageCode());
            }
            final String targetCode = stage.getEscalationTargetStageCode();
            if (targetCode == null) {
                throw new WorkflowConfigurationException("escalation.target.missing",
                        "Stage " + stage.getStageCode() + " enables escalation but defines no escalation target stage",
                        stage.getStageCode());
            }
            if (targetCode.equals(stage.getStageCode())) {
                throw new WorkflowConfigurationException("escalation.target.self",
                        "Stage " + stage.getStageCode() + " cannot escalate to itself", stage.getStageCode());
            }
            if (definition.findStageByCode(targetCode).isEmpty()) {
                throw new WorkflowConfigurationException("escalation.target.unknown",
                        "Stage " + stage.getStageCode() + " escalates to unknown stage " + targetCode, stage.getStageCode(), targetCode);
            }
        }
    }

    private void validateTransitions(final WorkflowDefinition definition) {
        final List<WorkflowStage> stages = definition.getStages();
        final List<WorkflowTransition> transitions = definition.getTransitions();

        final Map<String, Set<String>> outgoing = new HashMap<>();
        final Set<String> hasIncoming = new HashSet<>();
        for (final WorkflowTransition transition : transitions) {
            final String from = transition.getFromStage().getStageCode();
            final String to = transition.getToStage().getStageCode();
            if (from.equals(to)) {
                throw new WorkflowConfigurationException("transition.self.reference", "Stage " + from + " cannot transition to itself",
                        from);
            }
            outgoing.computeIfAbsent(from, key -> new HashSet<>()).add(to);
            hasIncoming.add(to);
        }

        final List<WorkflowStage> entryStages = stages.stream().filter(stage -> !hasIncoming.contains(stage.getStageCode())).toList();
        if (entryStages.isEmpty()) {
            throw new WorkflowConfigurationException("no.entry.stage",
                    "The workflow has no entry stage; every stage has an incoming transition, which implies a cycle");
        }
        if (entryStages.size() > 1) {
            final List<String> codes = entryStages.stream().map(WorkflowStage::getStageCode).toList();
            throw new WorkflowConfigurationException("multiple.entry.stages",
                    "The workflow has multiple entry stages: " + String.join(", ", codes), codes);
        }

        final String entryStageCode = entryStages.get(0).getStageCode();
        final Set<String> reachable = new HashSet<>();
        final Deque<String> toVisit = new ArrayDeque<>();
        toVisit.push(entryStageCode);
        while (!toVisit.isEmpty()) {
            final String current = toVisit.pop();
            if (reachable.add(current)) {
                outgoing.getOrDefault(current, Set.of()).forEach(toVisit::push);
            }
        }
        for (final WorkflowStage stage : stages) {
            if (!reachable.contains(stage.getStageCode())) {
                throw new WorkflowConfigurationException("unreachable.stage",
                        "Stage " + stage.getStageCode() + " is not reachable from the entry stage " + entryStageCode, stage.getStageCode(),
                        entryStageCode);
            }
        }

        detectCycle(stages, outgoing);
    }

    private void detectCycle(final List<WorkflowStage> stages, final Map<String, Set<String>> outgoing) {
        final Set<String> visited = new HashSet<>();
        final Set<String> inProgress = new HashSet<>();
        for (final WorkflowStage stage : stages) {
            visit(stage.getStageCode(), outgoing, visited, inProgress);
        }
    }

    private void visit(final String stageCode, final Map<String, Set<String>> outgoing, final Set<String> visited,
            final Set<String> inProgress) {
        if (visited.contains(stageCode)) {
            return;
        }
        if (!inProgress.add(stageCode)) {
            throw new WorkflowConfigurationException("circular.transitions",
                    "The workflow transitions contain a cycle involving stage " + stageCode, stageCode);
        }
        for (final String next : outgoing.getOrDefault(stageCode, Set.of())) {
            visit(next, outgoing, visited, inProgress);
        }
        inProgress.remove(stageCode);
        visited.add(stageCode);
    }

    /**
     * Guards against two active workflows for the same maker-checker task at the same priority, which would make
     * runtime selection ambiguous.
     */
    public void validateNoAmbiguousSelection(final WorkflowDefinition candidate, final List<WorkflowDefinition> activeDefinitions) {
        for (final WorkflowDefinition existing : activeDefinitions) {
            if (existing.getId().equals(candidate.getId())) {
                continue;
            }
            if (!existing.getPriority().equals(candidate.getPriority())) {
                continue;
            }
            throw new WorkflowConfigurationException("duplicate.priority.for.task",
                    "Workflow " + candidate.getName() + " shares priority " + candidate.getPriority() + " with active workflow "
                            + existing.getName() + " for task " + candidate.getTaskPermissionCode(),
                    candidate.getName(), existing.getName(), candidate.getTaskPermissionCode(), candidate.getPriority());
        }
    }
}
