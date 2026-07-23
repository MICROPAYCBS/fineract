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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRequest;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceStepRequest;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequence;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStatus;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStepRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStepType;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceDomainRuleException;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceNotFoundException;
import org.apache.fineract.infrastructure.jobs.sequence.serialization.JobSequenceCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class JobSequenceWritePlatformServiceImpl implements JobSequenceWritePlatformService {

    private final PlatformSecurityContext context;
    private final JobSequenceRepository jobSequenceRepository;
    private final JobSequenceStepRepository jobSequenceStepRepository;
    private final JobSequenceRunRepository jobSequenceRunRepository;
    private final JobSequenceCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final JobSequenceExecutionService executionService;

    @Override
    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        this.context.authenticatedUser();
        final JobSequenceRequest request = this.apiJsonDeserializer.validateAndParse(command.json());
        if (this.jobSequenceRepository.existsByNameIgnoreCase(request.getName())) {
            throw new PlatformDataIntegrityException("error.msg.job.sequence.duplicate.name",
                    "Job sequence with name `" + request.getName() + "` already exists.", "name", request.getName());
        }
        final JobSequence sequence = new JobSequence();
        applyRequest(sequence, request);
        this.jobSequenceRepository.saveAndFlush(sequence);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(sequence.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult update(final Long sequenceId, final JsonCommand command) {
        this.context.authenticatedUser();
        final JobSequenceRequest request = this.apiJsonDeserializer.validateAndParse(command.json());
        final JobSequence sequence = this.jobSequenceRepository.findByIdWithSteps(sequenceId)
                .orElseThrow(() -> new JobSequenceNotFoundException(sequenceId));
        if (this.jobSequenceRepository.existsByNameIgnoreCaseAndIdNot(request.getName(), sequenceId)) {
            throw new PlatformDataIntegrityException("error.msg.job.sequence.duplicate.name",
                    "Job sequence with name `" + request.getName() + "` already exists.", "name", request.getName());
        }
        if (this.jobSequenceRunRepository.existsBySequenceIdAndStatus(sequenceId, JobSequenceRunStatus.RUNNING)) {
            throw new JobSequenceDomainRuleException("cannot.update.while.running",
                    "Job sequence " + sequenceId + " cannot be updated while a run is in progress", sequenceId);
        }
        // Delete existing steps and flush before inserting replacements. Cascade orphanRemoval can INSERT
        // new rows before DELETE in one flush, violating uq_m_job_sequence_step_order.
        this.jobSequenceStepRepository.deleteBySequenceId(sequenceId);
        sequence.getSteps().clear();
        applyRequest(sequence, request);
        this.jobSequenceRepository.saveAndFlush(sequence);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(sequence.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult delete(final Long sequenceId) {
        this.context.authenticatedUser();
        final JobSequence sequence = this.jobSequenceRepository.findById(sequenceId)
                .orElseThrow(() -> new JobSequenceNotFoundException(sequenceId));
        if (this.jobSequenceRunRepository.existsBySequenceIdAndStatus(sequenceId, JobSequenceRunStatus.RUNNING)) {
            throw new JobSequenceDomainRuleException("cannot.delete.while.running",
                    "Job sequence " + sequenceId + " cannot be deleted while a run is in progress", sequenceId);
        }
        this.jobSequenceRepository.delete(sequence);
        return new CommandProcessingResultBuilder().withEntityId(sequenceId).build();
    }

    @Override
    public CommandProcessingResult execute(final Long sequenceId) {
        final AppUser user = this.context.authenticatedUser();
        final Long runId = this.executionService.startExecution(sequenceId, user.getId());
        return new CommandProcessingResultBuilder().withEntityId(sequenceId).withSubEntityId(runId).build();
    }

    private void applyRequest(final JobSequence sequence, final JobSequenceRequest request) {
        sequence.setName(request.getName());
        sequence.setDescription(request.getDescription());
        sequence.setActive(request.getActive() == null || request.getActive());
        final List<JobSequenceStepRequest> ordered = request.getSteps().stream()
                .sorted(Comparator.comparing(JobSequenceStepRequest::getStepOrder)).toList();
        final List<JobSequenceStep> steps = new ArrayList<>();
        for (final JobSequenceStepRequest stepRequest : ordered) {
            final JobSequenceStep step = new JobSequenceStep();
            step.setStepOrder(stepRequest.getStepOrder());
            step.setStepType(JobSequenceStepType.fromString(stepRequest.getStepType()));
            step.setJobShortName(stepRequest.getJobShortName());
            step.setOperationCode(stepRequest.getOperationCode());
            step.setEnabled(stepRequest.getEnabled() == null || stepRequest.getEnabled());
            step.setStopOnFailure(stepRequest.getStopOnFailure() == null || stepRequest.getStopOnFailure());
            steps.add(step);
        }
        sequence.replaceSteps(steps);
    }
}
