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
package org.apache.fineract.workflow.data;

import java.math.BigDecimal;
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WorkflowStageData {

    private final Long id;
    private final String stageCode;
    private final String name;
    private final String stageType;
    private final Integer requiredApprovals;
    private final String rejectionPolicy;
    private final Integer rejectionThreshold;
    private final String expiryPeriodUnit;
    private final Integer expiryPeriodValue;
    private final Boolean escalationEnabled;
    private final String escalationTargetStageCode;
    private final Boolean allowCrossBranchAccess;
    private final Boolean requireDistinctApprover;
    private final BigDecimal approvalLimitAmount;
    private final String approvalLimitCurrency;
    private final List<String> actions;
}
