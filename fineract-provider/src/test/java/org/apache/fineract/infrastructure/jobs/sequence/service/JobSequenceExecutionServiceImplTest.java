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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateWritePlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.apache.fineract.infrastructure.jobs.service.JobRegisterService;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequence;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRun;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStatus;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStepRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStepType;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceDomainRuleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class JobSequenceExecutionServiceImplTest {

    @Mock
    private JobSequenceRepository jobSequenceRepository;
    @Mock
    private JobSequenceRunRepository jobSequenceRunRepository;
    @Mock
    private JobSequenceRunStepRepository jobSequenceRunStepRepository;
    @Mock
    private ScheduledJobDetailRepository scheduledJobDetailRepository;
    @Mock
    private JobRegisterService jobRegisterService;
    @Mock
    private BusinessDateWritePlatformService businessDateWritePlatformService;
    @Mock
    private ThreadPoolTaskExecutor taskExecutor;
    @Mock
    private PlatformTransactionManager transactionManager;

    private JobSequenceExecutionServiceImpl underTest;
    private MockedStatic<ThreadLocalContextUtil> threadLocal;

    @BeforeEach
    void setUp() {
        lenient().when(this.transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        lenient().doNothing().when(this.transactionManager).commit(any());
        lenient().doNothing().when(this.transactionManager).rollback(any());
        this.underTest = new JobSequenceExecutionServiceImpl(this.jobSequenceRepository, this.jobSequenceRunRepository,
                this.jobSequenceRunStepRepository, this.scheduledJobDetailRepository, this.jobRegisterService,
                this.businessDateWritePlatformService, this.taskExecutor, this.transactionManager);
        this.threadLocal = Mockito.mockStatic(ThreadLocalContextUtil.class);
        this.threadLocal.when(ThreadLocalContextUtil::getContext).thenReturn(Mockito.mock(FineractContext.class));
    }

    @AfterEach
    void tearDown() {
        this.threadLocal.close();
    }

    @Test
    void startExecutionRejectsWhenAlreadyRunning() {
        final JobSequence sequence = sequenceWithSteps();
        when(this.jobSequenceRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(sequence));
        when(this.jobSequenceRunRepository.existsBySequenceIdAndStatus(1L, JobSequenceRunStatus.RUNNING)).thenReturn(true);

        assertThatThrownBy(() -> this.underTest.startExecution(1L, 9L)).isInstanceOf(JobSequenceDomainRuleException.class)
                .hasMessageContaining("already has a run in progress");
        verify(this.taskExecutor, never()).execute(any());
    }

    @Test
    void executeRunStopsOnFailure() throws Exception {
        final JobSequence sequence = sequenceWithSteps();
        final JobSequenceRun run = new JobSequenceRun();
        run.setId(10L);
        run.setSequence(sequence);
        run.setStatus(JobSequenceRunStatus.RUNNING);
        run.setSteps(new ArrayList<>());

        when(this.jobSequenceRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRunRepository.findByIdWithSteps(10L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(sequence));
        when(this.jobSequenceRunStepRepository.saveAndFlush(any(JobSequenceRunStep.class))).thenAnswer(inv -> {
            final JobSequenceRunStep step = inv.getArgument(0);
            if (step.getId() == null) {
                step.setId(100L + step.getStepOrder());
            }
            return step;
        });
        when(this.jobSequenceRunStepRepository.findById(anyLong())).thenAnswer(inv -> {
            final JobSequenceRunStep step = new JobSequenceRunStep();
            step.setId(inv.getArgument(0));
            step.setRun(run);
            step.setStatus(JobSequenceRunStatus.RUNNING);
            return Optional.of(step);
        });
        when(this.jobSequenceRunRepository.saveAndFlush(any(JobSequenceRun.class))).thenAnswer(inv -> inv.getArgument(0));

        doThrow(new RuntimeException("boom")).when(this.businessDateWritePlatformService)
                .increaseDateByTypeByOneDay(any());

        this.underTest.executeRun(10L);

        verify(this.jobRegisterService, never()).executeJobWithParameters(anyLong(), any());
        verify(this.jobSequenceRunRepository, Mockito.atLeastOnce()).saveAndFlush(any(JobSequenceRun.class));
    }

    @Test
    void startExecutionQueuesAsyncWorker() {
        final JobSequence sequence = sequenceWithSteps();
        when(this.jobSequenceRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(sequence));
        when(this.jobSequenceRunRepository.existsBySequenceIdAndStatus(1L, JobSequenceRunStatus.RUNNING)).thenReturn(false);
        when(this.jobSequenceRunRepository.saveAndFlush(any(JobSequenceRun.class))).thenAnswer(inv -> {
            final JobSequenceRun run = inv.getArgument(0);
            run.setId(55L);
            return run;
        });
        final AtomicReference<Runnable> queued = new AtomicReference<>();
        doAnswer(inv -> {
            queued.set(inv.getArgument(0));
            return null;
        }).when(this.taskExecutor).execute(any(Runnable.class));

        final Long runId = this.underTest.startExecution(1L, 9L);

        assertThat(runId).isEqualTo(55L);
        assertThat(queued.get()).isNotNull();
    }

    private static JobSequence sequenceWithSteps() {
        final JobSequence sequence = new JobSequence();
        sequence.setId(1L);
        sequence.setName("END_OF_DAY");
        sequence.setActive(true);
        final JobSequenceStep op = new JobSequenceStep();
        op.setId(1L);
        op.setStepOrder(1);
        op.setStepType(JobSequenceStepType.OPERATION);
        op.setOperationCode("ADVANCE_BUSINESS_DATE");
        op.setEnabled(true);
        op.setStopOnFailure(true);
        final JobSequenceStep job = new JobSequenceStep();
        job.setId(2L);
        job.setStepOrder(2);
        job.setStepType(JobSequenceStepType.SCHEDULER_JOB);
        job.setJobShortName("LA_ECOB");
        job.setEnabled(true);
        job.setStopOnFailure(true);
        sequence.setSteps(List.of(op, job));
        return sequence;
    }
}
