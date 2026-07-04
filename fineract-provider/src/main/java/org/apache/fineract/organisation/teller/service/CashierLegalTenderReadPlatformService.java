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
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.organisation.monetary.data.CashierLegalTenderLineData;
import org.apache.fineract.organisation.teller.data.CashierTransactionData;
import org.apache.fineract.organisation.teller.domain.CashierTransactionLegalTender;
import org.apache.fineract.organisation.teller.domain.CashierTransactionLegalTenderRepository;
import org.apache.fineract.organisation.teller.domain.CashierTxnType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CashierLegalTenderReadPlatformService {

    private final CashierTransactionLegalTenderRepository cashierTransactionLegalTenderRepository;

    @Transactional(readOnly = true)
    public void enrichTransactionsWithLegalTenderLines(final Collection<CashierTransactionData> transactions) {
        if (transactions == null || transactions.isEmpty()) {
            return;
        }
        final List<Long> cashierTxnIds = transactions.stream()
                .filter(txn -> txn.getTxnType() != null && isFundMovement(txn.getTxnType().getId())).map(CashierTransactionData::getId)
                .filter(id -> id != null && id > 0).distinct().toList();
        if (cashierTxnIds.isEmpty()) {
            return;
        }
        final List<CashierTransactionLegalTender> lines = this.cashierTransactionLegalTenderRepository
                .findByCashierTransactionIds(cashierTxnIds);
        final Map<Long, List<CashierLegalTenderLineData>> linesByTxnId = lines.stream()
                .collect(Collectors.groupingBy(line -> line.getCashierTransaction().getId(), Collectors.mapping(this::toData, Collectors.toList())));
        for (CashierTransactionData transaction : transactions) {
            if (transaction.getId() != null && isFundMovement(transaction.getTxnType() != null ? transaction.getTxnType().getId() : null)) {
                transaction.setLegalTenderLines(linesByTxnId.getOrDefault(transaction.getId(), Collections.emptyList()));
            }
        }
    }

    private CashierLegalTenderLineData toData(final CashierTransactionLegalTender line) {
        return CashierLegalTenderLineData.builder().legalTenderId(line.getLegalTender().getId()).quantity(line.getQuantity())
                .label(line.getLegalTender().getLabel()).tenderType(line.getLegalTender().getLegalTenderTypeEnum().getCode())
                .value(line.getLegalTender().getValue()).lineAmount(line.getLineAmount()).build();
    }

    private boolean isFundMovement(final Integer txnTypeId) {
        return txnTypeId != null && (CashierTxnType.ALLOCATE.getId().equals(txnTypeId) || CashierTxnType.SETTLE.getId().equals(txnTypeId));
    }
}
