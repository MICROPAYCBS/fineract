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
package org.apache.fineract.commands.service;

import static org.apache.fineract.commands.domain.CommandProcessingResultType.UNDER_PROCESSING;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.ZoneId;
import java.util.Optional;
import org.apache.fineract.batch.exception.ErrorInfo;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.commands.domain.CommandSourceRepository;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.exception.RollbackTransactionNotApprovedException;
import org.apache.fineract.commands.handler.NewCommandSourceHandler;
import org.apache.fineract.infrastructure.codes.exception.CodeNotFoundException;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CommandSourceServiceTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private CommandSourceRepository commandSourceRepository;

    @Mock
    private ErrorHandler errorHandler;

    @Mock
    private ApprovalWorkflowHook approvalWorkflowHook;

    @InjectMocks
    private CommandSourceService underTest;

    @BeforeEach
    public void setup() {
        MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    public void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    public void testCreateFromWrapper() {
        CommandWrapper wrapper = CommandWrapper.wrap("act", "ent", 1L, 1L);
        JsonCommand jsonCommand = JsonCommand.from("{}");
        AppUser appUser = Mockito.mock(AppUser.class);

        FineractPlatformTenant ft = new FineractPlatformTenant(1L, "t1", "n1", ZoneId.systemDefault().toString(), null);
        ThreadLocalContextUtil.setTenant(ft);

        String idk = "idk";
        underTest.saveInitialNewTransaction(wrapper, jsonCommand, appUser, idk);

        ArgumentCaptor<CommandSource> commandSourceArgumentCaptor = ArgumentCaptor.forClass(CommandSource.class);
        Mockito.verify(commandSourceRepository).saveAndFlush(commandSourceArgumentCaptor.capture());

        CommandSource captured = commandSourceArgumentCaptor.getValue();
        Assertions.assertEquals(idk, captured.getIdempotencyKey());
        Assertions.assertEquals(UNDER_PROCESSING.getValue(), captured.getStatus());
    }

    @Test
    public void testCreateFromExisting() {
        long commandId = 1L;
        CommandSource commandMock = Mockito.mock(CommandSource.class);
        Mockito.when(commandSourceRepository.findById(commandId)).thenReturn(Optional.of(commandMock));

        CommandSource actual = underTest.getCommandSource(commandId);
        Assertions.assertEquals(commandMock, actual);
    }

    @Test
    public void testGenerateErrorException() {
        try (MockedStatic<ErrorHandler> mockedStatic = Mockito.mockStatic(ErrorHandler.class)) {
            mockedStatic.when(() -> ErrorHandler.getMappable(any(CodeNotFoundException.class))).thenAnswer(i -> i.getArguments()[0]);
        }
        Mockito.when(errorHandler.handle(any(CodeNotFoundException.class)))
                .thenReturn(new ErrorInfo(404, 1001, "Code with name `foo` does not exist", null));
        ErrorInfo result = underTest.generateErrorInfo(new CodeNotFoundException("foo"));
        Assertions.assertEquals(404, result.getStatusCode());
        Assertions.assertEquals(1001, result.getErrorCode());
        Assertions.assertTrue(result.getMessage().contains("Code with name `foo` does not exist"));
    }

    @Test
    public void processCommandStampsAuditFieldsBeforeMakerCheckerHold() {
        final NewCommandSourceHandler handler = Mockito.mock(NewCommandSourceHandler.class);
        final JsonCommand jsonCommand = JsonCommand.from("{}");
        final CommandSource commandSource = Mockito.mock(CommandSource.class);
        final AppUser maker = Mockito.mock(AppUser.class);
        final CommandProcessingResult handlerResult = new CommandProcessingResultBuilder() //
                .withEntityId(3L) //
                .withOfficeId(2L) //
                .withLoanId(3L) //
                .withClientId(1L) //
                .build();

        when(handler.processCommand(jsonCommand)).thenReturn(handlerResult);
        when(commandSource.getPermissionCode()).thenReturn("APPROVE_LOAN");
        when(commandSource.isSanitized()).thenReturn(false);
        when(commandSource.getId()).thenReturn(32L);
        when(commandSource.getResourceId()).thenReturn(3L);
        when(configurationDomainService.isMakerCheckerEnabledForTask("APPROVE_LOAN")).thenReturn(true);
        when(maker.isCheckerSuperUser()).thenReturn(false);

        assertThrows(RollbackTransactionNotApprovedException.class,
                () -> underTest.processCommand(handler, jsonCommand, commandSource, maker, false));

        final ArgumentCaptor<CommandProcessingResult> resultCaptor = ArgumentCaptor.forClass(CommandProcessingResult.class);
        verify(commandSource).updateForAudit(resultCaptor.capture());
        Assertions.assertEquals(2L, resultCaptor.getValue().getOfficeId());
        Assertions.assertEquals(3L, resultCaptor.getValue().getLoanId());
        verify(commandSource).markAsAwaitingApproval();
        verify(approvalWorkflowHook).onCommandAwaitingApproval(eq(commandSource), eq(jsonCommand));
    }
}
