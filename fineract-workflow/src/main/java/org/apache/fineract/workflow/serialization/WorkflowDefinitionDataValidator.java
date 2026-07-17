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
package org.apache.fineract.workflow.serialization;

import static org.apache.fineract.workflow.api.WorkflowApiConstants.ACTIONS_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.EXPIRY_PERIOD_UNIT_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.EXPIRY_PERIOD_VALUE_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.FROM_STAGE_CODE_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.NAME_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.PRIORITY_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.REJECTION_POLICY_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.REJECTION_THRESHOLD_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.REQUIRED_APPROVALS_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.ROLE_ID_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.SEQUENCE_NO_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.STAGES_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.STAGE_CODE_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.STAGE_TYPE_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.TASK_PERMISSION_CODE_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.TO_STAGE_CODE_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.TRANSITIONS_PARAM;
import static org.apache.fineract.workflow.api.WorkflowApiConstants.WORKFLOW_DEFINITION_RESOURCE_NAME;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.workflow.data.WorkflowDefinitionRequest;
import org.apache.fineract.workflow.data.WorkflowStageRequest;
import org.apache.fineract.workflow.data.WorkflowTransitionRequest;
import org.apache.fineract.workflow.domain.WorkflowApprovalAction;
import org.apache.fineract.workflow.domain.WorkflowExpiryPeriodUnit;
import org.apache.fineract.workflow.domain.WorkflowRejectionPolicy;
import org.apache.fineract.workflow.domain.WorkflowStageType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Field-level validation of workflow definition create/update payloads. Structural (graph) rules are enforced
 * separately at activation time by the structure validator.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowDefinitionDataValidator {

    private static final List<String> STAGE_TYPES = Arrays.stream(WorkflowStageType.values()).map(Enum::name).toList();
    private static final List<String> REJECTION_POLICIES = Arrays.stream(WorkflowRejectionPolicy.values()).map(Enum::name).toList();
    private static final List<String> EXPIRY_PERIOD_UNITS = Arrays.stream(WorkflowExpiryPeriodUnit.values()).map(Enum::name).toList();
    private static final List<String> APPROVAL_ACTIONS = Arrays.stream(WorkflowApprovalAction.values()).map(Enum::name).toList();

    private final FromJsonHelper fromApiJsonHelper;

    public WorkflowDefinitionRequest validateAndParse(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }

        final WorkflowDefinitionRequest request = this.fromApiJsonHelper.fromJson(json, WorkflowDefinitionRequest.class);

        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors)
                .resource(WORKFLOW_DEFINITION_RESOURCE_NAME);

        baseDataValidator.reset().parameter(TASK_PERMISSION_CODE_PARAM).value(request.getTaskPermissionCode()).notBlank()
                .notExceedingLengthOf(100);
        baseDataValidator.reset().parameter(NAME_PARAM).value(request.getName()).notBlank().notExceedingLengthOf(255);
        baseDataValidator.reset().parameter(PRIORITY_PARAM).value(request.getPriority()).ignoreIfNull().zeroOrPositiveAmount();

        if (request.getStages() != null) {
            for (int i = 0; i < request.getStages().size(); i++) {
                validateStage(request.getStages().get(i), i, baseDataValidator);
            }
        }
        if (request.getTransitions() != null) {
            for (int i = 0; i < request.getTransitions().size(); i++) {
                validateTransition(request.getTransitions().get(i), i, baseDataValidator);
            }
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        return request;
    }

    private void validateStage(final WorkflowStageRequest stage, final int index, final DataValidatorBuilder baseDataValidator) {
        final String prefix = STAGES_PARAM + "[" + index + "].";

        baseDataValidator.reset().parameter(prefix + STAGE_CODE_PARAM).value(stage.getStageCode()).notBlank().notExceedingLengthOf(100);
        baseDataValidator.reset().parameter(prefix + STAGE_TYPE_PARAM).value(stage.getStageType()).notBlank()
                .isOneOfTheseStringValues(STAGE_TYPES);
        baseDataValidator.reset().parameter(prefix + REQUIRED_APPROVALS_PARAM).value(stage.getRequiredApprovals()).notNull()
                .integerGreaterThanZero();
        baseDataValidator.reset().parameter(prefix + REJECTION_POLICY_PARAM).value(stage.getRejectionPolicy()).ignoreIfNull()
                .isOneOfTheseStringValues(REJECTION_POLICIES);
        baseDataValidator.reset().parameter(prefix + REJECTION_THRESHOLD_PARAM).value(stage.getRejectionThreshold()).ignoreIfNull()
                .integerGreaterThanZero();
        baseDataValidator.reset().parameter(prefix + EXPIRY_PERIOD_UNIT_PARAM).value(stage.getExpiryPeriodUnit()).ignoreIfNull()
                .isOneOfTheseStringValues(EXPIRY_PERIOD_UNITS);
        baseDataValidator.reset().parameter(prefix + EXPIRY_PERIOD_VALUE_PARAM).value(stage.getExpiryPeriodValue()).ignoreIfNull()
                .integerGreaterThanZero();
        baseDataValidator.reset().parameter(prefix + ROLE_ID_PARAM).value(stage.getRoleId()).ignoreIfNull().longGreaterThanZero();

        if (stage.getActions() != null) {
            for (final String action : stage.getActions()) {
                baseDataValidator.reset().parameter(prefix + ACTIONS_PARAM).value(action).notBlank()
                        .isOneOfTheseStringValues(APPROVAL_ACTIONS);
            }
        }
    }

    private void validateTransition(final WorkflowTransitionRequest transition, final int index,
            final DataValidatorBuilder baseDataValidator) {
        final String prefix = TRANSITIONS_PARAM + "[" + index + "].";

        baseDataValidator.reset().parameter(prefix + FROM_STAGE_CODE_PARAM).value(transition.getFromStageCode()).notBlank();
        baseDataValidator.reset().parameter(prefix + TO_STAGE_CODE_PARAM).value(transition.getToStageCode()).notBlank();
        baseDataValidator.reset().parameter(prefix + SEQUENCE_NO_PARAM).value(transition.getSequenceNo()).notNull()
                .integerGreaterThanZero();
    }
}
