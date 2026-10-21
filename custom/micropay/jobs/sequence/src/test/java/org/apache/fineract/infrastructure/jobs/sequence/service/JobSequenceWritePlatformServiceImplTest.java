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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequence;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRun;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStatus;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStepRepository;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceDomainRuleException;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceRunNotFoundException;
import org.apache.fineract.infrastructure.jobs.sequence.serialization.JobSequenceCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSequenceWritePlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private JobSequenceRepository jobSequenceRepository;
    @Mock
    private JobSequenceStepRepository jobSequenceStepRepository;
    @Mock
    private JobSequenceRunRepository jobSequenceRunRepository;
    @Mock
    private JobSequenceCommandFromApiJsonDeserializer apiJsonDeserializer;
    @Mock
    private JobSequenceExecutionService executionService;
    @Mock
    private AppUser user;

    private JobSequenceWritePlatformServiceImpl underTest;

    @BeforeEach
    void setUp() {
        this.underTest = new JobSequenceWritePlatformServiceImpl(this.context, this.jobSequenceRepository, this.jobSequenceStepRepository,
                this.jobSequenceRunRepository, this.apiJsonDeserializer, this.executionService);
        when(this.context.authenticatedUser()).thenReturn(this.user);
        lenient().when(this.user.getUsername()).thenReturn("operator");
    }

    @Test
    void cancelRunMarksRunningRunAndItsRunningStepCancelled() {
        final JobSequence sequence = sequence(1L);
        final JobSequenceRun run = run(sequence, JobSequenceRunStatus.RUNNING);
        final JobSequenceRunStep running = step(JobSequenceRunStatus.RUNNING);
        final JobSequenceRunStep done = step(JobSequenceRunStatus.COMPLETED);
        run.getSteps().add(running);
        run.getSteps().add(done);
        when(this.jobSequenceRunRepository.findByIdWithSteps(8L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRunRepository.saveAndFlush(run)).thenReturn(run);

        final CommandProcessingResult result = this.underTest.cancelRun(1L, 8L);

        assertThat(result.getResourceId()).isEqualTo(1L);
        assertThat(run.getStatus()).isEqualTo(JobSequenceRunStatus.CANCELLED);
        assertThat(run.getErrorMessage()).isEqualTo("Cancelled by operator");
        assertThat(run.getFinishedAt()).isNotNull();
        assertThat(running.getStatus()).isEqualTo(JobSequenceRunStatus.CANCELLED);
        assertThat(running.getErrorMessage()).isEqualTo("Cancelled by operator");
        assertThat(done.getStatus()).isEqualTo(JobSequenceRunStatus.COMPLETED);
    }

    @Test
    void cancelRunRejectsAFinishedRun() {
        final JobSequenceRun run = run(sequence(1L), JobSequenceRunStatus.COMPLETED);
        when(this.jobSequenceRunRepository.findByIdWithSteps(8L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> this.underTest.cancelRun(1L, 8L)).isInstanceOf(JobSequenceDomainRuleException.class)
                .extracting("globalisationMessageCode").isEqualTo("error.msg.job.sequence.run.not.running");
    }

    @Test
    void cancelRunRejectsARunOfAnotherSequence() {
        final JobSequenceRun run = run(sequence(2L), JobSequenceRunStatus.RUNNING);
        when(this.jobSequenceRunRepository.findByIdWithSteps(8L)).thenReturn(Optional.of(run));

        assertThatThrownBy(() -> this.underTest.cancelRun(1L, 8L)).isInstanceOf(JobSequenceRunNotFoundException.class);
    }

    private static JobSequence sequence(final long id) {
        final JobSequence sequence = new JobSequence();
        sequence.setId(id);
        return sequence;
    }

    private static JobSequenceRun run(final JobSequence sequence, final JobSequenceRunStatus status) {
        final JobSequenceRun run = new JobSequenceRun();
        run.setId(8L);
        run.setSequence(sequence);
        run.setStatus(status);
        run.setSteps(new ArrayList<>());
        return run;
    }

    private static JobSequenceRunStep step(final JobSequenceRunStatus status) {
        final JobSequenceRunStep step = new JobSequenceRunStep();
        step.setStatus(status);
        return step;
    }
}
