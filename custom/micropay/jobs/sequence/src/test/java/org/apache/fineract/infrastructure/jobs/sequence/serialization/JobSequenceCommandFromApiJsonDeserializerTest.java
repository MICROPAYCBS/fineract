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
package org.apache.fineract.infrastructure.jobs.sequence.serialization;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.domain.ScheduledJobDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobSequenceCommandFromApiJsonDeserializerTest {

    @Mock
    private ScheduledJobDetailRepository scheduledJobDetailRepository;

    private JobSequenceCommandFromApiJsonDeserializer deserializer;

    @BeforeEach
    void setUp() {
        this.deserializer = new JobSequenceCommandFromApiJsonDeserializer(new FromJsonHelper(), this.scheduledJobDetailRepository);
    }

    @Test
    void rejectsUnknownSchedulerJobShortName() {
        when(this.scheduledJobDetailRepository.existsByShortName("NOPE")).thenReturn(false);
        final String json = """
                {
                  "name": "CUSTOM",
                  "steps": [
                    { "stepOrder": 1, "stepType": "SCHEDULER_JOB", "jobShortName": "NOPE" }
                  ]
                }
                """;
        assertThatThrownBy(() -> this.deserializer.validateAndParse(json)).isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsUnknownOperation() {
        final String json = """
                {
                  "name": "CUSTOM",
                  "steps": [
                    { "stepOrder": 1, "stepType": "OPERATION", "operationCode": "NOT_A_REAL_OP" }
                  ]
                }
                """;
        assertThatThrownBy(() -> this.deserializer.validateAndParse(json)).isInstanceOf(PlatformApiDataValidationException.class);
    }
}
