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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.data.CashierLegalTenderLineData;
import org.apache.fineract.organisation.teller.data.CashierTransactionData;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderLine;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderLineRepository;
import org.apache.fineract.organisation.teller.domain.CashLegalTenderSourceType;
import org.apache.fineract.organisation.teller.domain.CashierTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierLegalTenderReadPlatformService {

    private static final String SAVINGS_ENTITY_TYPE = "savings"; // set by savingsTxnSchema(): 'savings' as entity_type

    private final CashLegalTenderLineRepository cashLegalTenderLineRepository;

    @Transactional(readOnly = true)
    public void enrichTransactionsWithLegalTenderLines(final Collection<CashierTransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        final List<Long> cashierTxnIds = transactions.stream().filter(this::isCashierFundMovement).map(CashierTransactionData::getId)
                .filter(id -> id != null && id > 0).distinct().toList();
        final List<Long> savingsTxnIds = transactions.stream().filter(this::isSavingsCashMovement).map(CashierTransactionData::getId)
                .filter(id -> id != null && id > 0).distinct().toList();

        // Two maps: cashier and savings transaction ids come from different tables and can collide.
        final Map<Long, List<CashierLegalTenderLineData>> cashierLines = loadLinesBySourceId(CashLegalTenderSourceType.CASHIER_TXN,
                cashierTxnIds);
        final Map<Long, List<CashierLegalTenderLineData>> savingsLines = loadLinesBySourceId(CashLegalTenderSourceType.SAVINGS_TXN,
                savingsTxnIds);

        for (CashierTransactionData transaction : transactions) {
            if (transaction.getId() == null) {
                continue;
            }
            if (isCashierFundMovement(transaction)) {
                transaction.setLegalTenderLines(cashierLines.getOrDefault(transaction.getId(), Collections.emptyList()));
            } else if (isSavingsCashMovement(transaction)) {
                transaction.setLegalTenderLines(savingsLines.getOrDefault(transaction.getId(), Collections.emptyList()));
            }
        }
    }

    private Map<Long, List<CashierLegalTenderLineData>> loadLinesBySourceId(final CashLegalTenderSourceType sourceType,
            final List<Long> sourceIds) {
        if (sourceIds.isEmpty()) {
            return new HashMap<>();
        }
        final List<CashLegalTenderLine> lines = this.cashLegalTenderLineRepository.findBySourceTypeAndSourceIds(sourceType.getId(),
                sourceIds);
        return lines.stream().collect(Collectors.groupingBy(CashLegalTenderLine::getSourceId, HashMap::new,
                Collectors.mapping(this::toData, Collectors.toList())));
    }

    private CashierLegalTenderLineData toData(final CashLegalTenderLine line) {
        return CashierLegalTenderLineData.builder().legalTenderId(line.getLegalTender().getId()).quantity(line.getQuantity())
                .label(line.getLegalTender().getLabel()).tenderType(line.getLegalTender().getLegalTenderTypeEnum().getCode())
                .value(line.getLegalTender().getValue()).lineAmount(line.getLineAmount()).build();
    }

    private boolean isCashierFundMovement(final CashierTransactionData txn) {
        final Integer typeId = txn.getTxnType() == null ? null : txn.getTxnType().getId();
        return typeId != null && (CashierTxnType.ALLOCATE.getId().equals(typeId) || CashierTxnType.SETTLE.getId().equals(typeId));
    }

    private boolean isSavingsCashMovement(final CashierTransactionData txn) {
        final Integer typeId = txn.getTxnType() == null ? null : txn.getTxnType().getId();
        return typeId != null && SAVINGS_ENTITY_TYPE.equals(txn.getEntityType())
                && (CashierTxnType.INWARD_CASH_TXN.getId().equals(typeId) || CashierTxnType.OUTWARD_CASH_TXN.getId().equals(typeId));
    }
}
