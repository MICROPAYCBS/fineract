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
package org.apache.fineract.treasury.jobs;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameData;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameProvider;
import org.apache.fineract.infrastructure.jobs.service.jobname.SimpleJobNameProvider;
import org.apache.fineract.treasury.service.TreasuryDealLifecycleService;
import org.apache.fineract.treasury.service.TreasuryPositionSnapshotService;
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

/**
 * The day-end treasury jobs: the deal accrual, then the position snapshot. The snapshot belongs after the GL balance
 * snapshot in a job sequence, so both close the same day; the accrual belongs before both, so the day's accruals and
 * cash flows are in the balances they read.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final TreasuryPositionSnapshotService snapshotService;
    private final TreasuryDealLifecycleService lifecycleService;

    @Bean
    public JobNameProvider treasuryJobNameProvider() {
        return new SimpleJobNameProvider(
                Arrays.stream(TreasuryJobName.values()).map(jobName -> new JobNameData(jobName.name(), jobName.toString())).toList());
    }

    @Bean
    protected Step treasuryPositionSnapshotStep() {
        return new StepBuilder(TreasuryJobName.TREASURY_POSITION_SNAPSHOT.name(), jobRepository)
                .tasklet(new TreasuryPositionSnapshotTasklet(snapshotService), transactionManager).build();
    }

    @Bean
    public Job treasuryPositionSnapshotJob() {
        return new JobBuilder(TreasuryJobName.TREASURY_POSITION_SNAPSHOT.name(), jobRepository).start(treasuryPositionSnapshotStep())
                .incrementer(new RunIdIncrementer()).build();
    }

    @Bean
    protected Step treasuryAccrualStep() {
        return new StepBuilder(TreasuryJobName.TREASURY_ACCRUAL.name(), jobRepository)
                .tasklet(new TreasuryAccrualTasklet(lifecycleService), transactionManager).build();
    }

    @Bean
    public Job treasuryAccrualJob() {
        return new JobBuilder(TreasuryJobName.TREASURY_ACCRUAL.name(), jobRepository).start(treasuryAccrualStep())
                .incrementer(new RunIdIncrementer()).build();
    }
}
