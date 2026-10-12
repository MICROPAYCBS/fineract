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
package org.apache.fineract.commands.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.Test;

class AuditsApiResourceResolveProcessingResultTest {

    @Test
    void acceptsNumberNameAndDisplayText() {
        assertEquals(1, AuditsApiResource.resolveProcessingResult("status", "1"));
        assertEquals(0, AuditsApiResource.resolveProcessingResult("status", "0"));
        assertEquals(1, AuditsApiResource.resolveProcessingResult("processingResult", "Processed"));
        assertEquals(2, AuditsApiResource.resolveProcessingResult("processingResult", "awaiting approval"));
        assertEquals(4, AuditsApiResource.resolveProcessingResult("status", "UNDER_PROCESSING"));
        assertNull(AuditsApiResource.resolveProcessingResult("status", "  "));
        assertNull(AuditsApiResource.resolveProcessingResult("status", null));
    }

    @Test
    void rejectsUnknownValues() {
        final PlatformApiDataValidationException numeric = assertThrows(PlatformApiDataValidationException.class,
                () -> AuditsApiResource.resolveProcessingResult("status", "9"));
        assertEquals("validation.msg.audit.status.is.not.one.of.expected.enumerations",
                numeric.getErrors().get(0).getUserMessageGlobalisationCode());

        final PlatformApiDataValidationException named = assertThrows(PlatformApiDataValidationException.class,
                () -> AuditsApiResource.resolveProcessingResult("processingResult", "Done"));
        assertEquals("validation.msg.audit.processingResult.is.not.one.of.expected.enumerations",
                named.getErrors().get(0).getUserMessageGlobalisationCode());
    }
}
