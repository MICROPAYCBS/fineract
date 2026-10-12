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
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateWritePlatformService;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.businessdate.exception.BusinessDateActionException;
import org.apache.fineract.infrastructure.core.exception.MultiException;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetail;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobRunHistory;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobRunHistoryRepository;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.infrastructure.jobs.service.JobRegisterService;
import org.apache.fineract.infrastructure.jobs.service.SchedulerServiceConstants;
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
    private ScheduledJobRunHistoryRepository scheduledJobRunHistoryRepository;
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
                this.jobSequenceRunStepRepository, this.scheduledJobDetailRepository, this.scheduledJobRunHistoryRepository,
                this.jobRegisterService, this.businessDateWritePlatformService, this.taskExecutor, this.transactionManager);
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
    void executeRunSkipsInactiveSchedulerJobAndContinues() throws Exception {
        final JobSequence sequence = new JobSequence();
        sequence.setId(1L);
        sequence.setName("END_OF_DAY");
        sequence.setActive(true);
        final JobSequenceStep inactiveJob = new JobSequenceStep();
        inactiveJob.setId(2L);
        inactiveJob.setStepOrder(1);
        inactiveJob.setStepType(JobSequenceStepType.SCHEDULER_JOB);
        inactiveJob.setJobShortName("GLB_SNAP");
        inactiveJob.setEnabled(true);
        inactiveJob.setStopOnFailure(true);
        final JobSequenceStep op = new JobSequenceStep();
        op.setId(1L);
        op.setStepOrder(2);
        op.setStepType(JobSequenceStepType.OPERATION);
        op.setOperationCode("ADVANCE_BUSINESS_DATE");
        op.setEnabled(true);
        op.setStopOnFailure(true);
        sequence.setSteps(List.of(inactiveJob, op));

        final JobSequenceRun run = new JobSequenceRun();
        run.setId(10L);
        run.setSequence(sequence);
        run.setStatus(JobSequenceRunStatus.RUNNING);
        run.setSteps(new ArrayList<>());

        final ScheduledJobDetail inactiveDetail = new ScheduledJobDetail();
        inactiveDetail.setActiveSchedular(false);

        when(this.jobSequenceRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRunRepository.findByIdWithSteps(10L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(sequence));
        when(this.scheduledJobDetailRepository.findIdByShortName("GLB_SNAP")).thenReturn(Optional.of(99L));
        when(this.scheduledJobDetailRepository.findByJobId(99L)).thenReturn(inactiveDetail);
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

        this.underTest.executeRun(10L);

        verify(this.jobRegisterService, never()).executeJobWithParameters(anyLong(), any());
        verify(this.businessDateWritePlatformService).increaseDateByTypeByOneDay(BusinessDateType.BUSINESS_DATE);
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

    @Test
    void schedulerStepFailsWhenJobHistoryFailed() throws Exception {
        final JobSequence sequence = schedulerThenOperation();
        final JobSequenceRun run = runningRun(sequence);
        stubRun(run, sequence);
        final Date started = new Date();
        stubFinishedJob("LA_ECOB", 7L, started);
        final ScheduledJobRunHistory history = new ScheduledJobRunHistory().setStatus(SchedulerServiceConstants.STATUS_FAILED)
                .setErrorMessage("boom\n\tat x.y").setStartTime(started);
        when(this.scheduledJobRunHistoryRepository.findLatestByJobId(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(history));
        final List<JobSequenceRunStep> savedSteps = captureSavedSteps();

        this.underTest.executeRun(10L);

        assertThat(savedSteps).anyMatch(step -> step.getStatus() == JobSequenceRunStatus.FAILED
                && "Scheduler job LA_ECOB failed: boom".equals(step.getErrorMessage()));
        assertThat(run.getStatus()).isEqualTo(JobSequenceRunStatus.FAILED);
        assertThat(run.getErrorMessage()).doesNotContain("\n");
        verify(this.businessDateWritePlatformService, never()).increaseDateByTypeByOneDay(any());
    }

    @Test
    void schedulerStepCompletesWhenJobHistorySucceeded() {
        final JobSequence sequence = singleSchedulerStep();
        final JobSequenceRun run = runningRun(sequence);
        stubRun(run, sequence);
        final Date started = new Date();
        stubFinishedJob("LA_ECOB", 7L, started);
        when(this.scheduledJobRunHistoryRepository.findLatestByJobId(org.mockito.ArgumentMatchers.eq(7L), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(new ScheduledJobRunHistory().setStatus(SchedulerServiceConstants.STATUS_SUCCESS).setStartTime(started)));
        final List<JobSequenceRunStep> savedSteps = captureSavedSteps();

        this.underTest.executeRun(10L);

        assertThat(savedSteps).anyMatch(step -> step.getStatus() == JobSequenceRunStatus.COMPLETED);
        assertThat(run.getStatus()).isEqualTo(JobSequenceRunStatus.COMPLETED);
    }

    @Test
    void operationFailureMessageHasNoStackTrace() throws Exception {
        final JobSequence sequence = sequenceWithSteps();
        final JobSequenceRun run = runningRun(sequence);
        stubRun(run, sequence);
        final List<JobSequenceRunStep> savedSteps = captureSavedSteps();
        doThrow(new JobExecutionException(List.of(
                new BusinessDateActionException("business.date.is.not.enabled", "Business date functionality is not enabled"))))
                        .when(this.businessDateWritePlatformService).increaseDateByTypeByOneDay(any());

        this.underTest.executeRun(10L);

        final String expected = "Sequence operation ADVANCE_BUSINESS_DATE failed: Business date functionality is not enabled";
        assertThat(savedSteps).anyMatch(step -> expected.equals(step.getErrorMessage()) && !step.getErrorMessage().contains("\n"));
        assertThat(run.getErrorMessage()).contains(expected).doesNotContain("\n");
    }

    @Test
    void describeFailureFlattensMultiException() {
        final MultiException ex = new JobExecutionException(List.of(
                new BusinessDateActionException("business.date.is.not.enabled", "Business date functionality is not enabled"),
                new BusinessDateActionException("other", "Another reason")));

        assertThat(JobSequenceExecutionServiceImpl.describeFailure(ex))
                .isEqualTo("Business date functionality is not enabled; Another reason");
    }

    @Test
    void executeRunStopsWhenRunCancelled() throws Exception {
        final JobSequence sequence = twoOperations(true);
        final JobSequenceRun running = runningRun(sequence);
        final JobSequenceRun cancelled = runningRun(sequence);
        cancelled.setStatus(JobSequenceRunStatus.CANCELLED);
        final AtomicInteger finds = new AtomicInteger();
        when(this.jobSequenceRunRepository.findById(10L)).thenAnswer(inv -> finds.incrementAndGet() < 3 ? Optional.of(running) : Optional.of(cancelled));
        when(this.jobSequenceRunRepository.findByIdWithSteps(10L)).thenReturn(Optional.of(cancelled));
        when(this.jobSequenceRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(sequence));
        captureSavedSteps();

        this.underTest.executeRun(10L);

        verify(this.businessDateWritePlatformService, Mockito.times(1)).increaseDateByTypeByOneDay(any());
        assertThat(cancelled.getStatus()).isEqualTo(JobSequenceRunStatus.CANCELLED);
        verify(this.jobSequenceRunRepository, never()).saveAndFlush(cancelled);
    }

    @Test
    void stopOnFailureFalseMarksRunFailedAtEnd() throws Exception {
        final JobSequence sequence = twoOperations(false);
        final JobSequenceRun run = runningRun(sequence);
        stubRun(run, sequence);
        final List<JobSequenceRunStep> savedSteps = captureSavedSteps();
        doThrow(new RuntimeException("boom")).doNothing().when(this.businessDateWritePlatformService).increaseDateByTypeByOneDay(any());

        this.underTest.executeRun(10L);

        assertThat(savedSteps).anyMatch(step -> step.getStepOrder() == 1 && step.getStatus() == JobSequenceRunStatus.FAILED);
        assertThat(savedSteps).anyMatch(step -> step.getStepOrder() == 2 && step.getStatus() == JobSequenceRunStatus.COMPLETED);
        assertThat(run.getStatus()).isEqualTo(JobSequenceRunStatus.FAILED);
        verify(this.businessDateWritePlatformService, Mockito.times(2)).increaseDateByTypeByOneDay(any());
    }

    private void stubRun(final JobSequenceRun run, final JobSequence sequence) {
        when(this.jobSequenceRunRepository.findById(10L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRunRepository.findByIdWithSteps(10L)).thenReturn(Optional.of(run));
        when(this.jobSequenceRepository.findByIdWithSteps(1L)).thenReturn(Optional.of(sequence));
        when(this.jobSequenceRunRepository.saveAndFlush(any(JobSequenceRun.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private List<JobSequenceRunStep> captureSavedSteps() {
        final List<JobSequenceRunStep> savedSteps = new ArrayList<>();
        when(this.jobSequenceRunStepRepository.saveAndFlush(any(JobSequenceRunStep.class))).thenAnswer(inv -> {
            final JobSequenceRunStep step = inv.getArgument(0);
            if (step.getId() == null) {
                step.setId(100L + step.getStepOrder());
            }
            savedSteps.add(step);
            return step;
        });
        when(this.jobSequenceRunStepRepository.findById(anyLong())).thenAnswer(inv -> {
            final Long id = inv.getArgument(0);
            for (int i = savedSteps.size() - 1; i >= 0; i--) {
                if (id.equals(savedSteps.get(i).getId())) {
                    return Optional.of(savedSteps.get(i));
                }
            }
            final JobSequenceRunStep step = new JobSequenceRunStep();
            step.setId(id);
            step.setStatus(JobSequenceRunStatus.RUNNING);
            return Optional.of(step);
        });
        return savedSteps;
    }

    private void stubFinishedJob(final String shortName, final long jobId, final Date started) {
        final ScheduledJobDetail detail = new ScheduledJobDetail().setActiveSchedular(true).setCurrentlyRunning(false);
        when(this.scheduledJobDetailRepository.findIdByShortName(shortName)).thenReturn(Optional.of(jobId));
        when(this.scheduledJobDetailRepository.findByJobId(jobId)).thenReturn(detail);
        doAnswer(inv -> {
            detail.setPreviousRunStartTime(started);
            return null;
        }).when(this.jobRegisterService).executeJobWithParameters(jobId, null);
    }

    private static JobSequenceRun runningRun(final JobSequence sequence) {
        final JobSequenceRun run = new JobSequenceRun();
        run.setId(10L);
        run.setSequence(sequence);
        run.setStatus(JobSequenceRunStatus.RUNNING);
        run.setSteps(new ArrayList<>());
        return run;
    }

    private static JobSequence singleSchedulerStep() {
        final JobSequence sequence = new JobSequence();
        sequence.setId(1L);
        sequence.setName("CLOSE");
        sequence.setActive(true);
        sequence.setSteps(List.of(schedulerStep(1, true)));
        return sequence;
    }

    private static JobSequence schedulerThenOperation() {
        final JobSequence sequence = new JobSequence();
        sequence.setId(1L);
        sequence.setName("CLOSE");
        sequence.setActive(true);
        final JobSequenceStep op = new JobSequenceStep();
        op.setId(2L);
        op.setStepOrder(2);
        op.setStepType(JobSequenceStepType.OPERATION);
        op.setOperationCode("ADVANCE_BUSINESS_DATE");
        op.setEnabled(true);
        op.setStopOnFailure(true);
        sequence.setSteps(List.of(schedulerStep(1, true), op));
        return sequence;
    }

    private static JobSequenceStep schedulerStep(final int order, final boolean stopOnFailure) {
        final JobSequenceStep job = new JobSequenceStep();
        job.setId((long) order);
        job.setStepOrder(order);
        job.setStepType(JobSequenceStepType.SCHEDULER_JOB);
        job.setJobShortName("LA_ECOB");
        job.setEnabled(true);
        job.setStopOnFailure(stopOnFailure);
        return job;
    }

    private static JobSequence twoOperations(final boolean stopOnFailure) {
        final JobSequence sequence = new JobSequence();
        sequence.setId(1L);
        sequence.setName("END_OF_DAY");
        sequence.setActive(true);
        sequence.setSteps(List.of(operationStep(1, stopOnFailure), operationStep(2, true)));
        return sequence;
    }

    private static JobSequenceStep operationStep(final int order, final boolean stopOnFailure) {
        final JobSequenceStep op = new JobSequenceStep();
        op.setId((long) order);
        op.setStepOrder(order);
        op.setStepType(JobSequenceStepType.OPERATION);
        op.setOperationCode("ADVANCE_BUSINESS_DATE");
        op.setEnabled(true);
        op.setStopOnFailure(stopOnFailure);
        return op;
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
