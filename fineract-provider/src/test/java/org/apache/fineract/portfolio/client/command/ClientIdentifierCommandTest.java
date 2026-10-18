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
package org.apache.fineract.portfolio.client.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.Test;

class ClientIdentifierCommandTest {

    @Test
    void createRequiresActiveOrInactiveStatus() {
        final PlatformApiDataValidationException missing = assertThrows(PlatformApiDataValidationException.class,
                () -> new ClientIdentifierCommand(1L, "CM123456", null, null).validateForCreate());
        assertEquals("validation.msg.clientIdentifier.status.cannot.be.blank",
                missing.getErrors().get(0).getUserMessageGlobalisationCode());

        final PlatformApiDataValidationException empty = assertThrows(PlatformApiDataValidationException.class,
                () -> new ClientIdentifierCommand(1L, "CM123456", "", null).validateForCreate());
        assertEquals("validation.msg.clientIdentifier.status.cannot.be.blank", empty.getErrors().get(0).getUserMessageGlobalisationCode());

        final PlatformApiDataValidationException unknown = assertThrows(PlatformApiDataValidationException.class,
                () -> new ClientIdentifierCommand(1L, "CM123456", "Valid", null).validateForCreate());
        assertEquals("validation.msg.clientIdentifier.status.is.not.one.of.expected.enumerations",
                unknown.getErrors().get(0).getUserMessageGlobalisationCode());

        assertDoesNotThrow(() -> new ClientIdentifierCommand(1L, "CM123456", "Active", null).validateForCreate());
        assertDoesNotThrow(() -> new ClientIdentifierCommand(1L, "CM123456", "ACTIVE", null).validateForCreate());
        assertDoesNotThrow(() -> new ClientIdentifierCommand(1L, "CM123456", "inactive", null).validateForCreate());
    }

    @Test
    void updateAllowsStatusOnly() {
        assertDoesNotThrow(() -> new ClientIdentifierCommand(null, null, "Inactive", null).validateForUpdate());
    }
}
