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
package org.apache.fineract.infrastructure.jobs.sequence.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRequest;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceStepRequest;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceOperation;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStepType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JobSequenceCommandFromApiJsonDeserializer {

    public static final String NAME = "name";
    public static final String DESCRIPTION = "description";
    public static final String ACTIVE = "active";
    public static final String STEPS = "steps";
    public static final String STEP_ORDER = "stepOrder";
    public static final String STEP_TYPE = "stepType";
    public static final String JOB_SHORT_NAME = "jobShortName";
    public static final String OPERATION_CODE = "operationCode";
    public static final String ENABLED = "enabled";
    public static final String STOP_ON_FAILURE = "stopOnFailure";

    private static final Set<String> SUPPORTED = Set.of(NAME, DESCRIPTION, ACTIVE, STEPS);

    private final FromJsonHelper fromApiJsonHelper;
    private final ScheduledJobDetailRepository scheduledJobDetailRepository;

    public JobSequenceRequest validateAndParse(final String json) {
        if (StringUtils.isBlank(json)) {
            throw validationException("error.msg.job.sequence.json.required", "JSON body is required");
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SUPPORTED);

        final JsonElement element = this.fromApiJsonHelper.parse(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder base = new DataValidatorBuilder(dataValidationErrors).resource("jobsequence");

        final String name = this.fromApiJsonHelper.extractStringNamed(NAME, element);
        base.reset().parameter(NAME).value(name).notBlank().notExceedingLengthOf(100);

        final String description = this.fromApiJsonHelper.extractStringNamed(DESCRIPTION, element);
        base.reset().parameter(DESCRIPTION).value(description).ignoreIfNull().notExceedingLengthOf(500);

        final JobSequenceRequest request = new JobSequenceRequest();
        request.setName(name != null ? name.trim() : null);
        request.setDescription(description);
        if (this.fromApiJsonHelper.parameterExists(ACTIVE, element)) {
            request.setActive(this.fromApiJsonHelper.extractBooleanNamed(ACTIVE, element));
        } else {
            request.setActive(true);
        }

        final List<JobSequenceStepRequest> steps = new ArrayList<>();
        if (element.isJsonObject() && element.getAsJsonObject().has(STEPS) && element.getAsJsonObject().get(STEPS).isJsonArray()) {
            final JsonArray array = element.getAsJsonObject().getAsJsonArray(STEPS);
            final Set<Integer> orders = new HashSet<>();
            int index = 0;
            for (final JsonElement stepElement : array) {
                final JobSequenceStepRequest step = parseStep(stepElement.getAsJsonObject(), base, index++, orders);
                if (step != null) {
                    steps.add(step);
                }
            }
        }
        base.reset().parameter(STEPS).value(steps.isEmpty() ? null : steps).notNull();
        if (steps.isEmpty()) {
            base.reset().parameter(STEPS).failWithCode("must.not.be.empty");
        }
        request.setSteps(steps);

        throwIfErrors(dataValidationErrors);
        return request;
    }

    private JobSequenceStepRequest parseStep(final JsonObject json, final DataValidatorBuilder base, final int index,
            final Set<Integer> orders) {
        final String prefix = STEPS + "[" + index + "].";
        final Integer stepOrder = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(STEP_ORDER, json);
        base.reset().parameter(prefix + STEP_ORDER).value(stepOrder).notNull().integerGreaterThanZero();
        if (stepOrder != null && !orders.add(stepOrder)) {
            base.reset().parameter(prefix + STEP_ORDER).failWithCode("duplicate");
        }

        final String stepTypeRaw = this.fromApiJsonHelper.extractStringNamed(STEP_TYPE, json);
        base.reset().parameter(prefix + STEP_TYPE).value(stepTypeRaw).notBlank();
        JobSequenceStepType stepType = null;
        try {
            if (StringUtils.isNotBlank(stepTypeRaw)) {
                stepType = JobSequenceStepType.fromString(stepTypeRaw);
            }
        } catch (final IllegalArgumentException ex) {
            base.reset().parameter(prefix + STEP_TYPE).failWithCode("invalid");
        }

        final String jobShortName = this.fromApiJsonHelper.extractStringNamed(JOB_SHORT_NAME, json);
        final String operationCode = this.fromApiJsonHelper.extractStringNamed(OPERATION_CODE, json);

        if (stepType == JobSequenceStepType.SCHEDULER_JOB) {
            base.reset().parameter(prefix + JOB_SHORT_NAME).value(jobShortName).notBlank().notExceedingLengthOf(50);
            if (StringUtils.isNotBlank(jobShortName) && !this.scheduledJobDetailRepository.existsByShortName(jobShortName.trim())) {
                base.reset().parameter(prefix + JOB_SHORT_NAME).failWithCode("unknown.job");
            }
            if (StringUtils.isNotBlank(operationCode)) {
                base.reset().parameter(prefix + OPERATION_CODE).failWithCode("not.applicable");
            }
        } else if (stepType == JobSequenceStepType.OPERATION) {
            base.reset().parameter(prefix + OPERATION_CODE).value(operationCode).notBlank().notExceedingLengthOf(50);
            if (StringUtils.isNotBlank(operationCode) && !JobSequenceOperation.isAllowed(operationCode)) {
                base.reset().parameter(prefix + OPERATION_CODE).failWithCode("unknown.operation");
            }
            if (StringUtils.isNotBlank(jobShortName)) {
                base.reset().parameter(prefix + JOB_SHORT_NAME).failWithCode("not.applicable");
            }
        }

        final JobSequenceStepRequest step = new JobSequenceStepRequest();
        step.setStepOrder(stepOrder);
        step.setStepType(stepType != null ? stepType.name() : stepTypeRaw);
        step.setJobShortName(StringUtils.trimToNull(jobShortName));
        step.setOperationCode(StringUtils.trimToNull(operationCode));
        step.setEnabled(this.fromApiJsonHelper.parameterExists(ENABLED, json) ? this.fromApiJsonHelper.extractBooleanNamed(ENABLED, json)
                : Boolean.TRUE);
        step.setStopOnFailure(this.fromApiJsonHelper.parameterExists(STOP_ON_FAILURE, json)
                ? this.fromApiJsonHelper.extractBooleanNamed(STOP_ON_FAILURE, json)
                : Boolean.TRUE);
        return step;
    }

    private static void throwIfErrors(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }

    private static PlatformApiDataValidationException validationException(final String code, final String message) {
        final List<ApiParameterError> errors = List.of(ApiParameterError.generalError(code, message));
        return new PlatformApiDataValidationException(errors);
    }
}
