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

/**
 * Outcome of routing a checker approve/reject action through the approval workflow layer.
 */
public enum ApprovalWorkflowDecision {

    /** No workflow instance governs this command; use classic maker-checker. */
    NOT_APPLICABLE, //
    /** Stage action recorded; the command remains held and the instance advanced or unchanged. */
    STAGE_RECORDED, //
    /** All workflow stages complete; the underlying command may now execute. */
    PROCEED_TO_EXECUTE, //
    /** The workflow (and command) is rejected. */
    WORKFLOW_REJECTED;
}
