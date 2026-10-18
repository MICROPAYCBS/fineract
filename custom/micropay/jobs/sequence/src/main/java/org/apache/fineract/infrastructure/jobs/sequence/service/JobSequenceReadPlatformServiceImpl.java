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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceData;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRunData;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequence;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRun;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunRepository;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceNotFoundException;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceRunNotFoundException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class JobSequenceReadPlatformServiceImpl implements JobSequenceReadPlatformService {

    public static final String RESOURCE_NAME = "JOBSEQUENCE";

    private final PlatformSecurityContext context;
    private final JobSequenceRepository jobSequenceRepository;
    private final JobSequenceRunRepository jobSequenceRunRepository;

    @Override
    public List<JobSequenceData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        return this.jobSequenceRepository.findAll().stream() //
                .sorted(Comparator.comparing(JobSequence::getName, String.CASE_INSENSITIVE_ORDER)) //
                .map(sequence -> this.jobSequenceRepository.findByIdWithSteps(sequence.getId()).orElse(sequence)) //
                .map(JobSequenceMapper::toData) //
                .collect(Collectors.toList());
    }

    @Override
    public JobSequenceData retrieveOne(final Long sequenceId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final JobSequence sequence = this.jobSequenceRepository.findByIdWithSteps(sequenceId)
                .orElseThrow(() -> new JobSequenceNotFoundException(sequenceId));
        return JobSequenceMapper.toData(sequence);
    }

    @Override
    public List<JobSequenceRunData> retrieveRuns(final Long sequenceId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        if (!this.jobSequenceRepository.existsById(sequenceId)) {
            throw new JobSequenceNotFoundException(sequenceId);
        }
        return this.jobSequenceRunRepository.findBySequenceIdOrderByStartedAtDesc(sequenceId).stream() //
                .map(run -> this.jobSequenceRunRepository.findByIdWithSteps(run.getId()).orElse(run)) //
                .map(JobSequenceMapper::toRunData) //
                .collect(Collectors.toList());
    }

    @Override
    public JobSequenceRunData retrieveRun(final Long sequenceId, final Long runId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        if (!this.jobSequenceRepository.existsById(sequenceId)) {
            throw new JobSequenceNotFoundException(sequenceId);
        }
        final JobSequenceRun run = this.jobSequenceRunRepository.findByIdWithSteps(runId)
                .orElseThrow(() -> new JobSequenceRunNotFoundException(runId));
        if (!sequenceId.equals(run.getSequence().getId())) {
            throw new JobSequenceRunNotFoundException(runId);
        }
        return JobSequenceMapper.toRunData(run);
    }
}
