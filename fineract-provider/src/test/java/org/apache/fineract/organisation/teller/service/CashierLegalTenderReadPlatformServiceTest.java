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
package org.apache.fineract.organisation.teller.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import org.apache.fineract.organisation.teller.data.CashierTransactionData;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderLine;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderLineRepository;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderSourceType;
import org.apache.fineract.organisation.teller.domain.CashierTxnType;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTender;
import org.apache.fineract.organisation.teller.domain.LegalTenderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CashierLegalTenderReadPlatformServiceTest {

    private static final Integer CASHIER_SOURCE = CashLegalTenderSourceType.CASHIER_TXN.getId();
    private static final Integer SAVINGS_SOURCE = CashLegalTenderSourceType.SAVINGS_TXN.getId();

    @Mock
    private CashLegalTenderLineRepository cashLegalTenderLineRepository;

    @InjectMocks
    private CashierLegalTenderReadPlatformService service;

    @Test
    void savingsOnlyPageAttachesLines() {
        final CashierTransactionData deposit = cashRow(8L, CashierTxnType.INWARD_CASH_TXN, "savings");
        when(this.cashLegalTenderLineRepository.findBySourceTypeAndSourceIds(eq(SAVINGS_SOURCE), any()))
                .thenReturn(List.of(line(CashLegalTenderSourceType.SAVINGS_TXN, 8L, 18L, "Deposit note")));

        this.service.enrichTransactionsWithLegalTenderLines(List.of(deposit));

        assertEquals(18L, deposit.getLegalTenderLines().get(0).getLegalTenderId());
        verify(this.cashLegalTenderLineRepository, never()).findBySourceTypeAndSourceIds(eq(CASHIER_SOURCE), any());
    }

    @Test
    void collidingIdsKeepCashierAndSavingsLinesApart() {
        final CashierTransactionData allocate = cashRow(5L, CashierTxnType.ALLOCATE, null);
        final CashierTransactionData deposit = cashRow(5L, CashierTxnType.INWARD_CASH_TXN, "savings");
        when(this.cashLegalTenderLineRepository.findBySourceTypeAndSourceIds(eq(CASHIER_SOURCE), any()))
                .thenReturn(List.of(line(CashLegalTenderSourceType.CASHIER_TXN, 5L, 11L, "Allocate note")));
        when(this.cashLegalTenderLineRepository.findBySourceTypeAndSourceIds(eq(SAVINGS_SOURCE), any()))
                .thenReturn(List.of(line(CashLegalTenderSourceType.SAVINGS_TXN, 5L, 22L, "Deposit note")));

        this.service.enrichTransactionsWithLegalTenderLines(List.of(allocate, deposit));

        assertEquals(11L, allocate.getLegalTenderLines().get(0).getLegalTenderId());
        assertEquals(22L, deposit.getLegalTenderLines().get(0).getLegalTenderId());
    }

    @Test
    void loanRowIsNotLookedUpAsSavings() {
        final CashierTransactionData loan = cashRow(5L, CashierTxnType.OUTWARD_CASH_TXN, "loans");
        final CashierTransactionData deposit = cashRow(9L, CashierTxnType.INWARD_CASH_TXN, "savings");
        when(this.cashLegalTenderLineRepository.findBySourceTypeAndSourceIds(eq(SAVINGS_SOURCE), any())).thenReturn(List.of());

        this.service.enrichTransactionsWithLegalTenderLines(List.of(loan, deposit));

        assertNull(loan.getLegalTenderLines());
        @SuppressWarnings("unchecked")
        final ArgumentCaptor<List<Long>> savingsIds = ArgumentCaptor.forClass(List.class);
        verify(this.cashLegalTenderLineRepository).findBySourceTypeAndSourceIds(eq(SAVINGS_SOURCE), savingsIds.capture());
        assertTrue(savingsIds.getValue().contains(9L));
        assertFalse(savingsIds.getValue().contains(5L));
    }

    private static CashierTransactionData cashRow(final Long id, final CashierTxnType txnType, final String entityType) {
        return new CashierTransactionData().setId(id).setTxnType(txnType).setEntityType(entityType);
    }

    private static CashLegalTenderLine line(final CashLegalTenderSourceType sourceType, final Long sourceId, final Long legalTenderId,
            final String label) {
        final CurrencyLegalTender tender = new CurrencyLegalTender();
        tender.setId(legalTenderId);
        tender.setLabel(label);
        tender.setValue(new BigDecimal("100"));
        tender.setLegalTenderTypeEnum(LegalTenderType.NOTE);
        return CashLegalTenderLine.createNew(sourceType, sourceId, tender, 1, new BigDecimal("100"));
    }
}
