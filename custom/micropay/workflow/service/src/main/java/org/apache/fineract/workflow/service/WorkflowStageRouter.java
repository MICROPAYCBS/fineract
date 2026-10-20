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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.workflow.domain.WorkflowDefinition;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Walks a workflow's stages in transition order and skips the stages that every {@link WorkflowStageApplicability}
 * says a held command does not need. At least one stage always applies: when every stage would be skipped, the entry
 * stage is kept, so a held command is never approved without a checker.
 */
@Slf4j
@Component
public class WorkflowStageRouter {

    private final Supplier<List<WorkflowStageApplicability>> applicabilities;

    @Autowired
    public WorkflowStageRouter(final ObjectProvider<WorkflowStageApplicability> applicabilities) {
        this(() -> applicabilities.orderedStream().toList());
    }

    WorkflowStageRouter(final Supplier<List<WorkflowStageApplicability>> applicabilities) {
        this.applicabilities = applicabilities;
    }

    public static WorkflowStageRouter withoutSkips() {
        return new WorkflowStageRouter(List::of);
    }

    /**
     * @return the stage a new instance starts at: the first stage in order that applies, or the entry stage when none
     *         does; empty when the definition has no entry stage
     */
    public Optional<String> firstStage(final WorkflowDefinition definition, final CommandSource commandSource) {
        final List<String> chain = chain(definition);
        if (chain.isEmpty()) {
            return Optional.empty();
        }
        final Set<String> skipped = stagesToSkip(commandSource, definition);
        return Optional.of(chain.stream().filter(code -> !skipped.contains(code)).findFirst().orElse(chain.getFirst()));
    }

    /**
     * @return the next stage after {@code fromStageCode} that applies; empty when {@code fromStageCode} is the last stage
     *         that applies, which makes it the terminal stage for this command
     */
    public Optional<String> nextStage(final WorkflowDefinition definition, final String fromStageCode, final CommandSource commandSource) {
        final List<String> chain = chain(definition);
        final int index = chain.indexOf(fromStageCode);
        if (index < 0) {
            return definition.resolveNextStageCode(fromStageCode);
        }
        final Set<String> skipped = stagesToSkip(commandSource, definition);
        return chain.subList(index + 1, chain.size()).stream().filter(code -> !skipped.contains(code)).findFirst();
    }

    /**
     * @return the stage codes from the entry stage along the first outgoing transition of each stage
     */
    public List<String> chain(final WorkflowDefinition definition) {
        final List<String> chain = new ArrayList<>();
        final Set<String> visited = new HashSet<>();
        Optional<String> current = definition.findEntryStageCode();
        while (current.isPresent() && visited.add(current.get())) {
            chain.add(current.get());
            current = definition.resolveNextStageCode(current.get());
        }
        return chain;
    }

    public Set<String> stagesToSkip(final CommandSource commandSource, final WorkflowDefinition definition) {
        final Set<String> skipped = new LinkedHashSet<>();
        for (final WorkflowStageApplicability applicability : this.applicabilities.get()) {
            try {
                skipped.addAll(applicability.stagesToSkip(commandSource, definition));
            } catch (RuntimeException e) {
                log.error("Stage applicability {} failed for command {}; every stage of workflow {} applies",
                        applicability.getClass().getSimpleName(), commandSource.getId(), definition.getId(), e);
                return Set.of();
            }
        }
        return skipped;
    }
}
