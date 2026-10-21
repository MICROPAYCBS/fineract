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
package org.apache.fineract.scoring.jobs;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.Arrays;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameData;
import org.apache.fineract.infrastructure.jobs.service.jobname.JobNameProvider;
import org.apache.fineract.infrastructure.jobs.service.jobname.SimpleJobNameProvider;
import org.apache.fineract.scoring.limit.BehaviouralScoringService;
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
 * The nightly behavioural scoring job. Each client is scored and written in its own transaction by
 * {@code ClientLimitWriter}, so the tasklet's own transaction holds nothing.
 */
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class ScoringJobConfig {

    private final JobRepository jobRepository;
    private final PlatformTransactionManager transactionManager;
    private final BehaviouralScoringService behaviouralScoringService;

    @Bean
    public JobNameProvider scoringJobNameProvider() {
        return new SimpleJobNameProvider(
                Arrays.stream(ScoringJobName.values()).map(jobName -> new JobNameData(jobName.name(), jobName.toString())).toList());
    }

    @Bean
    protected Step behaviouralScoringStep() {
        return new StepBuilder(ScoringJobName.BEHAVIOURAL_SCORING.name(), jobRepository)
                .tasklet(new BehaviouralScoringTasklet(behaviouralScoringService), transactionManager).build();
    }

    @Bean
    public Job behaviouralScoringJob() {
        return new JobBuilder(ScoringJobName.BEHAVIOURAL_SCORING.name(), jobRepository).start(behaviouralScoringStep())
                .incrementer(new RunIdIncrementer()).build();
    }
}
