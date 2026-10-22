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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAgentFloat;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatRepository;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatStatus;
import org.apache.fineract.treasury.domain.TreasuryFloatRequest;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestRepository;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestStatus;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestType;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreasuryFloatRequestWorkflowTest {

    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 10, 16);
    private static final long REQUESTER = 1L;
    private static final long APPROVER = 2L;
    private static final long EXECUTOR = 3L;

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryAgentFloatRepository floatRepository = mock(TreasuryAgentFloatRepository.class);
    private final TreasuryFloatRequestRepository requestRepository = mock(TreasuryFloatRequestRepository.class);
    private final SavingsAccountWritePlatformService savingsService = mock(SavingsAccountWritePlatformService.class);
    private final TreasuryAgentFloatWritePlatformServiceImpl service = new TreasuryAgentFloatWritePlatformServiceImpl(this.actor,
            new TreasuryWorkflowDataValidator(new FromJsonHelper()), this.floatRepository, this.requestRepository,
            mock(TreasuryAccountRepository.class), mock(TreasuryReferenceDataLookup.class), mock(TreasurySettingService.class),
            this.savingsService, new FromJsonHelper());

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, BUSINESS_DATE)));
        final TreasuryAgentFloat agentFloat = new TreasuryAgentFloat();
        agentFloat.setId(4L);
        agentFloat.setClientId(8L);
        agentFloat.setSavingsAccountId(9L);
        agentFloat.setStatus(TreasuryAgentFloatStatus.ACTIVE);
        when(this.floatRepository.findById(4L)).thenReturn(Optional.of(agentFloat));
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void requesterCannotApprove() {
        request(TreasuryFloatRequestStatus.REQUESTED);
        actAs(REQUESTER);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.approveFloatRequest(5L, JsonCommand.from("{}")));
    }

    @Test
    void approverCannotExecute() {
        request(TreasuryFloatRequestStatus.APPROVED);
        actAs(APPROVER);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.executeFloatRequest(5L, JsonCommand.from("{}")));
        verify(this.savingsService, never()).deposit(anyLong(), any());
    }

    @Test
    void executedRequestIsReversedByUndoingTheSavingsTransaction() {
        final TreasuryFloatRequest request = request(TreasuryFloatRequestStatus.EXECUTED);
        actAs(REQUESTER);

        this.service.reverseFloatRequest(5L, JsonCommand.from("{\"reason\": \"Paid twice\"}"));

        verify(this.savingsService).undoTransaction(9L, 77L, false);
        assertEquals(TreasuryFloatRequestStatus.REVERSED, request.getStatus());
        assertEquals("Paid twice", request.getReversalReason());
        assertEquals(BUSINESS_DATE, request.getReversedOnDate());
    }

    @Test
    void executorCannotReverse() {
        request(TreasuryFloatRequestStatus.EXECUTED);
        actAs(EXECUTOR);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.reverseFloatRequest(5L, JsonCommand.from("{\"reason\": \"x\"}")));
        verify(this.savingsService, never()).undoTransaction(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void floatReversalNeedsAReasonAndNoDate() {
        request(TreasuryFloatRequestStatus.EXECUTED);
        actAs(REQUESTER);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.reverseFloatRequest(5L, JsonCommand.from("{}")));
        assertThrows(TreasuryWorkflowException.class,
                () -> this.service.reverseFloatRequest(5L, JsonCommand.from("{\"reason\": \"x\", \"transactionDate\": \"2026-10-16\"}")));
        verify(this.savingsService, never()).undoTransaction(anyLong(), anyLong(), anyBoolean());
    }

    @Test
    void approvedRequestCannotBeReversed() {
        request(TreasuryFloatRequestStatus.APPROVED);
        actAs(EXECUTOR);

        assertThrows(TreasuryWorkflowException.class, () -> this.service.reverseFloatRequest(5L, JsonCommand.from("{\"reason\": \"x\"}")));
    }

    private TreasuryFloatRequest request(final TreasuryFloatRequestStatus status) {
        final TreasuryFloatRequest request = new TreasuryFloatRequest();
        request.setId(5L);
        request.setAgentFloatId(4L);
        request.setRequestType(TreasuryFloatRequestType.TOPUP);
        request.setAmount(new BigDecimal("1000"));
        request.setStatus(status);
        request.setRequestedBy(REQUESTER);
        request.setApprovedBy(status == TreasuryFloatRequestStatus.REQUESTED ? null : APPROVER);
        if (status == TreasuryFloatRequestStatus.EXECUTED) {
            request.setExecutedBy(EXECUTOR);
            request.setSavingsTransactionId(77L);
        }
        when(this.requestRepository.findById(5L)).thenReturn(Optional.of(request));
        return request;
    }

    private void actAs(final long userId) {
        when(this.actor.userId(any())).thenReturn(userId);
    }
}
