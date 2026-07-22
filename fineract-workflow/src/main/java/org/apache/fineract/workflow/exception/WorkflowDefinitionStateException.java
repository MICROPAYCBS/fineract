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
package org.apache.fineract.workflow.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

/**
 * Raised when an operation is not allowed in the workflow definition's current lifecycle status, e.g. deleting a
 * definition which is not in DRAFT status, or updating while in-progress instances still reference the definition.
 */
public class WorkflowDefinitionStateException extends AbstractPlatformDomainRuleException {

    public WorkflowDefinitionStateException(final String action, final Long id, final String status) {
        super("error.msg.workflow.definition.invalid.state",
                "Workflow definition " + id + " cannot be " + action + " while in status " + status, action, id, status);
    }

    public static WorkflowDefinitionStateException cannotUpdateWithInProgressInstances(final Long id,
            final long inProgressCount) {
        return new WorkflowDefinitionStateException(id, inProgressCount);
    }

    private WorkflowDefinitionStateException(final Long id, final long inProgressCount) {
        super("error.msg.workflow.definition.cannot.be.updated.with.in.progress.instances",
                "Workflow definition " + id + " cannot be updated while " + inProgressCount
                        + " instance(s) are still IN_PROGRESS",
                id, inProgressCount);
    }
}
