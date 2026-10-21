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
package org.apache.fineract.infrastructure.jobs.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JobDetailDataValidatorTest {

    private JobDetailDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new JobDetailDataValidator(new FromJsonHelper());
    }

    @Test
    void acceptsDescriptionOnlyUpdate() {
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"description\":\"Rebuilds daily GL balance snapshots.\"}"));
    }

    @Test
    void acceptsEmptyDescriptionToClear() {
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"description\":\"\"}"));
    }

    @Test
    void rejectsDescriptionLongerThan500() {
        final String tooLong = "x".repeat(501);
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> validator.validateForUpdate("{\"description\":\"" + tooLong + "\"}"));
        assertTrue(ex.getErrors().stream().anyMatch(error -> error.getUserMessageGlobalisationCode().contains("exceeds.max.length")));
    }

    @Test
    void acceptsDescriptionAtMaxLength() {
        assertDoesNotThrow(() -> validator.validateForUpdate("{\"description\":\"" + "x".repeat(500) + "\"}"));
    }
}
