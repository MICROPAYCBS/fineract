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
package org.apache.fineract.scoring.data;

import java.math.BigDecimal;
import java.util.List;

/**
 * Which stages of the active approval workflow a loan would pass now, and why.
 *
 * @param stages
 *            in workflow order; empty when no workflow is active for the task
 */
public record ApprovalRouteData(Long loanId, String taskPermissionCode, Long workflowDefinitionId, BigDecimal amount, BigDecimal exposure,
        String grade, String outcome, boolean overridden, List<Stage> stages) {

    /**
     * @param reason
     *            NO_AUTHORITY_RULES, RULE_MATCHED, NO_RULE_MATCHED or NO_SCORING_DECISION; KEPT_AS_ENTRY_STAGE when every
     *            stage would be skipped and the entry stage is kept
     */
    public record Stage(String stageCode, String name, boolean required, String reason, List<Long> matchedRuleIds) {}
}
