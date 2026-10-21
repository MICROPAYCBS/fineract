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

import java.util.Optional;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.workflow.domain.WorkflowInstance;

public interface WorkflowInstanceWritePlatformService {

    /**
     * Creates a workflow instance for a maker-checker held command when a matching active definition exists.
     *
     * @return the created instance, or empty when workflows are disabled or no definition matches
     */
    Optional<WorkflowInstance> createInstanceForHeldCommand(CommandSource commandSource, JsonCommand command);
}
