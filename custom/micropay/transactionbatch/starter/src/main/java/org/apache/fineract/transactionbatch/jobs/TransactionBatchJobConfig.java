/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.jobs;

import static org.apache.fineract.transactionbatch.api.TransactionBatchApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameData;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameProvider;
import org.apache.fineract.infrastructure.jobs.service.jobname.SimpleJobNameProvider;
import org.apache.fineract.transactionbatch.service.TransactionBatchPostingService;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TransactionBatchJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TransactionBatchPostingService postingService;

    @Bean
    public JobNameProvider transactionBatchJobNameProvider() {
        return new SimpleJobNameProvider(
                List.of(new JobNameData(TransactionBatchJobName.TXN_BATCH_POST.name(), TransactionBatchJobName.TXN_BATCH_POST.toString())));
    }

    @Bean
    protected Step transactionBatchPostingStep() {
        return new StepBuilder(TransactionBatchJobName.TXN_BATCH_POST.name(), this.jobRepository)
                .tasklet(new TransactionBatchPostingTasklet(this.postingService), this.transactionManager).build();
    }

    @Bean
    public Job transactionBatchPostingJob() {
        return new JobBuilder(TransactionBatchJobName.TXN_BATCH_POST.name(), this.jobRepository).start(transactionBatchPostingStep())
                .incrementer(new RunIdIncrementer()).build();
    }
}
