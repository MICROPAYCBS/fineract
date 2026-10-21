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
package org.apache.fineract.commands.service;

import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.useradministration.domain.AppUser;

/**
 * Extension point for multi-stage approval workflows layered on maker-checker held commands. The default no-op
 * implementation is always available; the workflow module supplies the active implementation when enabled.
 */
public interface ApprovalWorkflowHook {

    /**
     * Invoked after a command has been marked {@code AWAITING_APPROVAL} and before the transaction is rolled back.
     * Implementations should persist workflow state in a separate transaction so it survives the command rollback.
     */
    void onCommandAwaitingApproval(CommandSource commandSource, JsonCommand command);

    /**
     * Invoked when a checker approves a held command via {@code /makercheckers/{id}?command=approve}.
     */
    ApprovalWorkflowDecision onCheckerApprove(CommandSource commandSource, AppUser checker);

    /**
     * Invoked when a checker rejects a held command via {@code /makercheckers/{id}?command=reject}.
     */
    ApprovalWorkflowDecision onCheckerReject(CommandSource commandSource, AppUser checker);
}
