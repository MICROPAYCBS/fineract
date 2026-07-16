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
package org.apache.fineract.portfolio.accounting.glbalance.jobs;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.jobs.service.JobName;
import org.apache.fineract.portfolio.accounting.glbalance.service.GlBalanceSnapshotWritePlatformService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
public class GlBalanceSnapshotJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final GlBalanceSnapshotWritePlatformService glBalanceSnapshotWritePlatformService;

    @Bean
    protected Step glBalanceSnapshotUpdateStep() {
        return new StepBuilder(JobName.UPDATE_GL_BALANCE_SNAPSHOTS.name(), jobRepository)
                .tasklet(glBalanceSnapshotUpdateTasklet(), transactionManager).build();
    }

    @Bean
    public Job updateGlBalanceSnapshotsJob() {
        return new JobBuilder(JobName.UPDATE_GL_BALANCE_SNAPSHOTS.name(), jobRepository).start(glBalanceSnapshotUpdateStep())
                .incrementer(new RunIdIncrementer()).build();
    }

    @Bean
    protected Step glBalanceSnapshotBackfillStep() {
        return new StepBuilder(JobName.GL_BALANCE_SNAPSHOT_BACKFILL.name(), jobRepository)
                .tasklet(glBalanceSnapshotBackfillTasklet(), transactionManager).build();
    }

    @Bean
    public Job glBalanceSnapshotBackfillJob() {
        return new JobBuilder(JobName.GL_BALANCE_SNAPSHOT_BACKFILL.name(), jobRepository).start(glBalanceSnapshotBackfillStep())
                .incrementer(new RunIdIncrementer()).build();
    }

    @Bean
    public GlBalanceSnapshotUpdateTasklet glBalanceSnapshotUpdateTasklet() {
        return new GlBalanceSnapshotUpdateTasklet(glBalanceSnapshotWritePlatformService);
    }

    @Bean
    public GlBalanceSnapshotBackfillTasklet glBalanceSnapshotBackfillTasklet() {
        return new GlBalanceSnapshotBackfillTasklet(glBalanceSnapshotWritePlatformService);
    }
}
