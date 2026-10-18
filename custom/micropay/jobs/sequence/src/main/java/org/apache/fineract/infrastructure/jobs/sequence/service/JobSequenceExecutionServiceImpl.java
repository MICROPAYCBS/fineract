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
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.businessdate.service.BusinessDateWritePlatformService;
import org.apache.fineract.infrastructure.core.config.TaskExecutorConstant;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.domain.FineractContext;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformException;
import org.apache.fineract.infrastructure.core.exception.MultiException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetail;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobRunHistory;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequence;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceOperation;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRun;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunHistoryRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStatus;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceRunStepRepository;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStep;
import org.apache.fineract.infrastructure.jobs.sequence.domain.JobSequenceStepType;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceDomainRuleException;
import org.apache.fineract.infrastructure.jobs.sequence.exception.JobSequenceNotFoundException;
import org.apache.fineract.infrastructure.jobs.service.JobRegisterService;
import org.apache.fineract.infrastructure.jobs.service.SchedulerServiceConstants;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Service
public class JobSequenceExecutionServiceImpl implements JobSequenceExecutionService {

    private static final long JOB_WAIT_POLL_MS = 1000L;
    private static final long JOB_WAIT_TIMEOUT_MS = 6L * 60L * 60L * 1000L;

    private final JobSequenceRepository jobSequenceRepository;
    private final JobSequenceRunRepository jobSequenceRunRepository;
    private final JobSequenceRunStepRepository jobSequenceRunStepRepository;
    private final ScheduledJobDetailRepository scheduledJobDetailRepository;
    private final JobSequenceRunHistoryRepository scheduledJobRunHistoryRepository;
    private final JobRegisterService jobRegisterService;
    private final BusinessDateWritePlatformService businessDateWritePlatformService;
    private final ThreadPoolTaskExecutor taskExecutor;
    private final TransactionTemplate transactionTemplate;

    public JobSequenceExecutionServiceImpl(final JobSequenceRepository jobSequenceRepository,
            final JobSequenceRunRepository jobSequenceRunRepository, final JobSequenceRunStepRepository jobSequenceRunStepRepository,
            final ScheduledJobDetailRepository scheduledJobDetailRepository,
            final JobSequenceRunHistoryRepository scheduledJobRunHistoryRepository, final JobRegisterService jobRegisterService,
            final BusinessDateWritePlatformService businessDateWritePlatformService,
            @Qualifier(TaskExecutorConstant.DEFAULT_TASK_EXECUTOR_BEAN_NAME) final ThreadPoolTaskExecutor taskExecutor,
            final PlatformTransactionManager transactionManager) {
        this.jobSequenceRepository = jobSequenceRepository;
        this.jobSequenceRunRepository = jobSequenceRunRepository;
        this.jobSequenceRunStepRepository = jobSequenceRunStepRepository;
        this.scheduledJobDetailRepository = scheduledJobDetailRepository;
        this.scheduledJobRunHistoryRepository = scheduledJobRunHistoryRepository;
        this.jobRegisterService = jobRegisterService;
        this.businessDateWritePlatformService = businessDateWritePlatformService;
        this.taskExecutor = taskExecutor;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long startExecution(final Long sequenceId, final Long triggeredByUserId) {
        final JobSequence sequence = this.jobSequenceRepository.findByIdWithSteps(sequenceId)
                .orElseThrow(() -> new JobSequenceNotFoundException(sequenceId));
        if (!sequence.isActive()) {
            throw new JobSequenceDomainRuleException("inactive", "Job sequence " + sequenceId + " is inactive", sequenceId);
        }
        if (this.jobSequenceRunRepository.existsBySequenceIdAndStatus(sequenceId, JobSequenceRunStatus.RUNNING)) {
            throw new JobSequenceDomainRuleException("already.running", "Job sequence " + sequenceId + " already has a run in progress",
                    sequenceId);
        }

        final JobSequenceRun run = new JobSequenceRun();
        run.setSequence(sequence);
        run.setTriggeredByUserId(triggeredByUserId);
        run.setStatus(JobSequenceRunStatus.RUNNING);
        run.setStartedAt(DateUtils.getAuditOffsetDateTime());
        this.jobSequenceRunRepository.saveAndFlush(run);

        final Long runId = run.getId();
        final FineractContext context = ThreadLocalContextUtil.getContext();
        this.taskExecutor.execute(() -> {
            try {
                ThreadLocalContextUtil.init(context);
                executeRun(runId);
            } catch (final Exception ex) {
                log.error("Job sequence run {} failed unexpectedly", runId, ex);
                markRunFailed(runId, describeFailure(ex));
            } finally {
                ThreadLocalContextUtil.reset();
            }
        });
        return runId;
    }

    @Override
    public void executeRun(final Long runId) {
        final List<JobSequenceStep> enabledSteps = this.transactionTemplate.execute(status -> {
            final JobSequenceRun run = loadRun(runId);
            final JobSequence sequence = this.jobSequenceRepository.findByIdWithSteps(run.getSequence().getId())
                    .orElseThrow(() -> new JobSequenceNotFoundException(run.getSequence().getId()));
            return sequence.getSteps().stream() //
                    .filter(JobSequenceStep::isEnabled) //
                    .sorted(Comparator.comparingInt(JobSequenceStep::getStepOrder)) //
                    .toList();
        });

        if (enabledSteps == null || enabledSteps.isEmpty()) {
            completeRun(runId);
            return;
        }

        boolean anyFailed = false;
        String lastFailure = null;
        for (final JobSequenceStep step : enabledSteps) {
            if (!isStillRunning(runId)) {
                return; // cancelled elsewhere; that code already closed the run
            }
            final Long runStepId = beginStep(runId, step);
            try {
                if (skipInactiveSchedulerJobIfNeeded(step, runStepId)) {
                    continue;
                }
                executeStep(runId, step);
                completeStep(runStepId);
            } catch (final RunNoLongerActiveException stopped) {
                return;
            } catch (final Exception ex) {
                final String reason = describeFailure(ex);
                log.error("Job sequence step {} failed for run {}", step.getStepOrder(), runId, ex);
                failStep(runStepId, reason);
                anyFailed = true;
                lastFailure = "Step " + step.getStepOrder() + ": " + reason;
                if (step.isStopOnFailure()) {
                    failRun(runId, lastFailure);
                    return;
                }
            }
        }
        if (anyFailed) {
            failRun(runId, lastFailure);
        } else {
            completeRun(runId);
        }
    }

    /**
     * Inactive or missing scheduler jobs do not fail the sequence — they are recorded as SKIPPED and the run continues.
     *
     * @return true when the step was skipped
     */
    private boolean skipInactiveSchedulerJobIfNeeded(final JobSequenceStep step, final Long runStepId) {
        if (step.getStepType() != JobSequenceStepType.SCHEDULER_JOB) {
            return false;
        }
        final String shortName = step.getJobShortName();
        if (shortName == null || shortName.isBlank()) {
            skipStep(runStepId, "Skipped, inactive");
            return true;
        }
        final Long jobId = this.scheduledJobDetailRepository.findIdByShortName(shortName).orElse(null);
        if (jobId == null) {
            log.warn("Skipping sequence step {}: scheduler job {} not found", step.getStepOrder(), shortName);
            skipStep(runStepId, "Skipped, inactive");
            return true;
        }
        final ScheduledJobDetail jobDetail = this.scheduledJobDetailRepository.findByJobId(jobId);
        if (jobDetail == null || !jobDetail.isActiveSchedular()) {
            log.warn("Skipping sequence step {}: scheduler job {} is inactive", step.getStepOrder(), shortName);
            skipStep(runStepId, "Skipped, inactive");
            return true;
        }
        return false;
    }

    private void executeStep(final Long runId, final JobSequenceStep step) {
        if (step.getStepType() == JobSequenceStepType.OPERATION) {
            executeOperation(JobSequenceOperation.fromString(step.getOperationCode()));
            return;
        }
        executeSchedulerJob(runId, step.getJobShortName());
    }

    private void executeOperation(final JobSequenceOperation operation) {
        try {
            if (operation == JobSequenceOperation.ADVANCE_BUSINESS_DATE) {
                this.businessDateWritePlatformService.increaseDateByTypeByOneDay(BusinessDateType.BUSINESS_DATE);
                return;
            }
        } catch (final Exception ex) {
            throw new JobSequenceDomainRuleException("operation.failed",
                    "Sequence operation " + operation + " failed: " + describeFailure(ex), operation.name());
        }
        throw new JobSequenceDomainRuleException("unknown.operation", "Unsupported sequence operation " + operation, operation.name());
    }

    private void executeSchedulerJob(final Long runId, final String shortName) {
        final Long jobId = this.scheduledJobDetailRepository.findIdByShortName(shortName).orElseThrow(
                () -> new JobSequenceDomainRuleException("unknown.job", "Unknown scheduler job short name " + shortName, shortName));
        final ScheduledJobDetail jobDetail = this.scheduledJobDetailRepository.findByJobId(jobId);
        if (jobDetail == null || !jobDetail.isActiveSchedular()) {
            throw new JobSequenceDomainRuleException("job.inactive", "Scheduler job " + shortName + " is missing or inactive", shortName);
        }
        final Date previousStart = jobDetail.getPreviousRunStartTime();
        this.jobRegisterService.executeJobWithParameters(jobId, null);
        final Date runStart = awaitJobCompletion(runId, jobId, previousStart, shortName);
        verifyJobSucceeded(jobId, runStart, shortName);
    }

    private void verifyJobSucceeded(final Long jobId, final Date runStart, final String shortName) {
        final ScheduledJobRunHistory last = this.scheduledJobRunHistoryRepository.findLatestByJobId(jobId, PageRequest.of(0, 1)).stream()
                .findFirst().orElse(null);
        if (last == null || (runStart != null && last.getStartTime() != null && last.getStartTime().before(runStart))) {
            throw new JobSequenceDomainRuleException("job.result.missing",
                    "Scheduler job " + shortName + " finished but recorded no result", shortName);
        }
        if (!SchedulerServiceConstants.STATUS_SUCCESS.equalsIgnoreCase(last.getStatus())) {
            final String reason = last.getErrorMessage() == null ? "" : ": " + firstLine(last.getErrorMessage());
            throw new JobSequenceDomainRuleException("job.failed", "Scheduler job " + shortName + " failed" + reason, shortName);
        }
    }

    private Date awaitJobCompletion(final Long runId, final Long jobId, final Date previousStart, final String shortName) {
        final long deadline = System.currentTimeMillis() + JOB_WAIT_TIMEOUT_MS;
        boolean started = false;
        while (System.currentTimeMillis() < deadline) {
            if (!isStillRunning(runId)) {
                throw new RunNoLongerActiveException();
            }
            final ScheduledJobDetail current = this.scheduledJobDetailRepository.findByJobId(jobId);
            if (current == null) {
                throw new JobSequenceDomainRuleException("job.missing", "Scheduler job " + shortName + " disappeared during execution",
                        shortName);
            }
            final Date currentStart = current.getPreviousRunStartTime();
            final boolean startChanged = previousStart == null ? currentStart != null
                    : currentStart != null && currentStart.after(previousStart);
            if (current.isCurrentlyRunning()) {
                started = true;
            }
            if ((started || startChanged) && !current.isCurrentlyRunning() && startChanged) {
                return currentStart;
            }
            try {
                Thread.sleep(JOB_WAIT_POLL_MS);
            } catch (final InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new JobSequenceDomainRuleException("job.interrupted", "Interrupted while waiting for job " + shortName, shortName);
            }
        }
        throw new JobSequenceDomainRuleException("job.timeout", "Timed out waiting for scheduler job " + shortName, shortName);
    }

    private boolean isStillRunning(final Long runId) {
        return this.jobSequenceRunRepository.findById(runId).map(r -> r.getStatus() == JobSequenceRunStatus.RUNNING).orElse(false);
    }

    /** Thrown internally when the run was cancelled while a step was in progress. */
    private static final class RunNoLongerActiveException extends RuntimeException {

        RunNoLongerActiveException() {
            super(null, null, false, false);
        }
    }

    /** One-line, user-facing reason. Never includes stack traces; the full exception is in the server log. */
    static String describeFailure(final Throwable ex) {
        if (ex instanceof MultiException multi) {
            return multi.getCauses().stream().map(JobSequenceExecutionServiceImpl::describeFailure).distinct()
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof PlatformApiDataValidationException validation && !validation.getErrors().isEmpty()) {
            return validation.getErrors().stream().map(ApiParameterError::getDefaultUserMessage).distinct()
                    .collect(Collectors.joining("; "));
        }
        if (ex instanceof AbstractPlatformException platform && platform.getDefaultUserMessage() != null) {
            return firstLine(platform.getDefaultUserMessage());
        }
        final String message = ex.getMessage();
        return message == null || message.isBlank() ? ex.getClass().getSimpleName() : firstLine(message);
    }

    private static String firstLine(final String text) {
        final int newline = text.indexOf('\n');
        return newline < 0 ? text.trim() : text.substring(0, newline).trim();
    }

    private Long beginStep(final Long runId, final JobSequenceStep step) {
        return this.transactionTemplate.execute(status -> {
            final JobSequenceRun run = this.jobSequenceRunRepository.findById(runId).orElseThrow(
                    () -> new JobSequenceDomainRuleException("run.not.found", "Job sequence run " + runId + " not found", runId));
            final JobSequenceRunStep runStep = new JobSequenceRunStep();
            runStep.setRun(run);
            runStep.setSequenceStepId(step.getId());
            runStep.setStepOrder(step.getStepOrder());
            runStep.setStepType(step.getStepType());
            runStep.setJobShortName(step.getJobShortName());
            runStep.setOperationCode(step.getOperationCode());
            runStep.setStatus(JobSequenceRunStatus.RUNNING);
            runStep.setStartedAt(DateUtils.getAuditOffsetDateTime());
            if (step.getStepType() == JobSequenceStepType.SCHEDULER_JOB && step.getJobShortName() != null) {
                this.scheduledJobDetailRepository.findIdByShortName(step.getJobShortName()).ifPresent(runStep::setSchedulerJobId);
            }
            // Persist the step directly so IDENTITY id is assigned (cascade via parent can leave getId() null).
            final JobSequenceRunStep saved = this.jobSequenceRunStepRepository.saveAndFlush(runStep);
            if (saved.getId() == null) {
                throw new JobSequenceDomainRuleException("run.step.id.missing",
                        "Failed to allocate id for job sequence run step at order " + step.getStepOrder(), step.getStepOrder());
            }
            return saved.getId();
        });
    }

    private void completeStep(final Long runStepId) {
        if (runStepId == null) {
            throw new JobSequenceDomainRuleException("run.step.id.missing", "Job sequence run step id is required");
        }
        this.transactionTemplate.executeWithoutResult(status -> {
            final JobSequenceRunStep runStep = this.jobSequenceRunStepRepository.findById(runStepId)
                    .orElseThrow(() -> new JobSequenceDomainRuleException("run.step.not.found",
                            "Job sequence run step " + runStepId + " not found", runStepId));
            if (runStep.getStatus() != JobSequenceRunStatus.RUNNING) {
                return;
            }
            runStep.setStatus(JobSequenceRunStatus.COMPLETED);
            runStep.setFinishedAt(DateUtils.getAuditOffsetDateTime());
            this.jobSequenceRunStepRepository.saveAndFlush(runStep);
        });
    }

    private void skipStep(final Long runStepId, final String message) {
        if (runStepId == null) {
            throw new JobSequenceDomainRuleException("run.step.id.missing", "Job sequence run step id is required");
        }
        this.transactionTemplate.executeWithoutResult(status -> {
            final JobSequenceRunStep runStep = this.jobSequenceRunStepRepository.findById(runStepId)
                    .orElseThrow(() -> new JobSequenceDomainRuleException("run.step.not.found",
                            "Job sequence run step " + runStepId + " not found", runStepId));
            runStep.setStatus(JobSequenceRunStatus.SKIPPED);
            runStep.setFinishedAt(DateUtils.getAuditOffsetDateTime());
            runStep.setErrorMessage(trimMessage(message));
            this.jobSequenceRunStepRepository.saveAndFlush(runStep);
        });
    }

    private void failStep(final Long runStepId, final String message) {
        if (runStepId == null) {
            throw new JobSequenceDomainRuleException("run.step.id.missing", "Job sequence run step id is required");
        }
        this.transactionTemplate.executeWithoutResult(status -> {
            final JobSequenceRunStep runStep = this.jobSequenceRunStepRepository.findById(runStepId)
                    .orElseThrow(() -> new JobSequenceDomainRuleException("run.step.not.found",
                            "Job sequence run step " + runStepId + " not found", runStepId));
            if (runStep.getStatus() != JobSequenceRunStatus.RUNNING) {
                return;
            }
            runStep.setStatus(JobSequenceRunStatus.FAILED);
            runStep.setFinishedAt(DateUtils.getAuditOffsetDateTime());
            runStep.setErrorMessage(trimMessage(message));
            this.jobSequenceRunStepRepository.saveAndFlush(runStep);
        });
    }

    private void completeRun(final Long runId) {
        this.transactionTemplate.executeWithoutResult(status -> {
            final JobSequenceRun run = loadRun(runId);
            if (run.getStatus() == JobSequenceRunStatus.RUNNING) {
                run.setStatus(JobSequenceRunStatus.COMPLETED);
                run.setFinishedAt(DateUtils.getAuditOffsetDateTime());
                this.jobSequenceRunRepository.saveAndFlush(run);
            }
        });
    }

    private void failRun(final Long runId, final String message) {
        this.transactionTemplate.executeWithoutResult(status -> {
            final JobSequenceRun run = loadRun(runId);
            if (run.getStatus() != JobSequenceRunStatus.RUNNING) {
                return;
            }
            run.setStatus(JobSequenceRunStatus.FAILED);
            run.setFinishedAt(DateUtils.getAuditOffsetDateTime());
            run.setErrorMessage(trimMessage(message));
            this.jobSequenceRunRepository.saveAndFlush(run);
        });
    }

    private void markRunFailed(final Long runId, final String message) {
        failRun(runId, message);
    }

    private JobSequenceRun loadRun(final Long runId) {
        return this.jobSequenceRunRepository.findByIdWithSteps(runId)
                .orElseThrow(() -> new JobSequenceDomainRuleException("run.not.found", "Job sequence run " + runId + " not found", runId));
    }

    private static String trimMessage(final String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 2000 ? message : message.substring(0, 2000);
    }
}
