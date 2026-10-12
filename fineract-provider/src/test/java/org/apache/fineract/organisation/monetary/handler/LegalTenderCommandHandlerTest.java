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
package org.apache.fineract.organisation.monetary.handler;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.organisation.monetary.service.LegalTenderWritePlatformService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegalTenderCommandHandlerTest {

    @Mock
    private LegalTenderWritePlatformService writePlatformService;

    @Test
    void createUsesCurrencyFromTransactionId() {
        final JsonCommand command = mock(JsonCommand.class);
        when(command.getTransactionId()).thenReturn("USD");
        when(this.writePlatformService.createLegalTender("USD", command)).thenReturn(CommandProcessingResult.empty());

        new CreateLegalTenderCommandHandler(this.writePlatformService).processCommand(command);

        verify(this.writePlatformService).createLegalTender("USD", command);
    }

    @Test
    void updateUsesCurrencyAndEntityId() {
        final JsonCommand command = mock(JsonCommand.class);
        when(command.getTransactionId()).thenReturn("USD");
        when(command.entityId()).thenReturn(18L);
        when(this.writePlatformService.updateLegalTender("USD", 18L, command)).thenReturn(CommandProcessingResult.empty());

        new UpdateLegalTenderCommandHandler(this.writePlatformService).processCommand(command);

        verify(this.writePlatformService).updateLegalTender("USD", 18L, command);
    }

    @Test
    void deleteUsesCurrencyAndEntityId() {
        final JsonCommand command = mock(JsonCommand.class);
        when(command.getTransactionId()).thenReturn("USD");
        when(command.entityId()).thenReturn(18L);
        when(this.writePlatformService.deleteLegalTender("USD", 18L, command)).thenReturn(CommandProcessingResult.empty());

        new DeleteLegalTenderCommandHandler(this.writePlatformService).processCommand(command);

        verify(this.writePlatformService).deleteLegalTender("USD", 18L, command);
    }
}
