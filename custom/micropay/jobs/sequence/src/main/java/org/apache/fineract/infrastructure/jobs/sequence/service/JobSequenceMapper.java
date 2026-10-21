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
package org.apache.fineract.infrastructure.jobs.sequence.service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceData;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRunData;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRunStepData;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceStepData;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequence;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRun;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStep;

public final class JobSequenceMapper {

    private JobSequenceMapper() {}

    public static JobSequenceData toData(final JobSequence sequence) {
        final List<JobSequenceStepData> steps = sequence.getSteps().stream() //
                .sorted(Comparator.comparingInt(JobSequenceStep::getStepOrder)) //
                .map(JobSequenceMapper::toStepData) //
                .collect(Collectors.toList());
        return JobSequenceData.builder() //
                .id(sequence.getId()) //
                .name(sequence.getName()) //
                .description(sequence.getDescription()) //
                .active(sequence.isActive()) //
                .steps(steps) //
                .build();
    }

    public static JobSequenceStepData toStepData(final JobSequenceStep step) {
        return JobSequenceStepData.builder() //
                .id(step.getId()) //
                .stepOrder(step.getStepOrder()) //
                .stepType(step.getStepType().name()) //
                .jobShortName(step.getJobShortName()) //
                .operationCode(step.getOperationCode()) //
                .enabled(step.isEnabled()) //
                .stopOnFailure(step.isStopOnFailure()) //
                .build();
    }

    public static JobSequenceRunData toRunData(final JobSequenceRun run) {
        final List<JobSequenceRunStepData> steps = run.getSteps().stream() //
                .sorted(Comparator.comparingInt(JobSequenceRunStep::getStepOrder)) //
                .map(JobSequenceMapper::toRunStepData) //
                .collect(Collectors.toList());
        return JobSequenceRunData.builder() //
                .id(run.getId()) //
                .sequenceId(run.getSequence().getId()) //
                .sequenceName(run.getSequence().getName()) //
                .triggeredByUserId(run.getTriggeredByUserId()) //
                .status(run.getStatus().name()) //
                .startedAt(run.getStartedAt()) //
                .finishedAt(run.getFinishedAt()) //
                .errorMessage(run.getErrorMessage()) //
                .steps(steps) //
                .build();
    }

    public static JobSequenceRunStepData toRunStepData(final JobSequenceRunStep step) {
        return JobSequenceRunStepData.builder() //
                .id(step.getId()) //
                .stepOrder(step.getStepOrder()) //
                .stepType(step.getStepType().name()) //
                .jobShortName(step.getJobShortName()) //
                .operationCode(step.getOperationCode()) //
                .status(step.getStatus().name()) //
                .startedAt(step.getStartedAt()) //
                .finishedAt(step.getFinishedAt()) //
                .schedulerJobId(step.getSchedulerJobId()) //
                .errorMessage(step.getErrorMessage()) //
                .build();
    }
}
