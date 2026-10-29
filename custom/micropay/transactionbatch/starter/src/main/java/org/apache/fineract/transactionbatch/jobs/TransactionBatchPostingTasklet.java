/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.jobs;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.transactionbatch.service.TransactionBatchPostingService;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

@RequiredArgsConstructor
public class TransactionBatchPostingTasklet implements Tasklet {

    private final TransactionBatchPostingService postingService;

    @Override
    public RepeatStatus execute(final StepContribution contribution, final ChunkContext chunkContext) {
        this.postingService.postDueBatches();
        return RepeatStatus.FINISHED;
    }
}
