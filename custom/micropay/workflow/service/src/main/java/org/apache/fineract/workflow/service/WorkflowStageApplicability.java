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

import java.util.Set;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.workflow.domain.WorkflowDefinition;

/**
 * Lets another module decide that some stages of a workflow do not apply to one held command, for example a loan
 * approval that is small enough to skip the credit committee. It is consulted when the instance is created and at each
 * transition, so the answer can change while the command is in progress.
 *
 * <p>
 * With no implementation every stage applies, which is the behaviour of workflows before this interface existed. If an
 * implementation throws, the router ignores every skip for that call, so a failure never removes an approval stage.
 */
public interface WorkflowStageApplicability {

    /**
     * @return the codes of the stages of {@code definition} that the command does not need to pass; empty when all
     *         apply
     */
    Set<String> stagesToSkip(CommandSource commandSource, WorkflowDefinition definition);
}
