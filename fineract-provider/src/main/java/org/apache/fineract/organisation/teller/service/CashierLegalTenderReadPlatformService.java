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

import org.apache.fineract.organisation.teller.domain.CashLegalTenderLine;

import org.apache.fineract.organisation.teller.domain.CashLegalTenderLineRepository;

import org.apache.fineract.organisation.teller.domain.CashLegalTenderSourceType;

import org.apache.fineract.organisation.teller.domain.CashierTxnType;

import org.springframework.stereotype.Service;

import org.springframework.transaction.annotation.Transactional;



@Service

@RequiredArgsConstructor

public class CashierLegalTenderReadPlatformService {



    private final CashLegalTenderLineRepository cashLegalTenderLineRepository;



    @Transactional(readOnly = true)

    public void enrichTransactionsWithLegalTenderLines(final Collection<CashierTransactionData> transactions) {

        if (transactions == null || transactions.isEmpty()) {

            return;

        }

        final List<Long> cashierTxnIds = transactions.stream()

                .filter(txn -> txn.getTxnType() != null && isCashierFundMovement(txn.getTxnType().getId())).map(CashierTransactionData::getId)

                .filter(id -> id != null && id > 0).distinct().toList();

        final List<Long> savingsTxnIds = transactions.stream()

                .filter(txn -> txn.getTxnType() != null && isSavingsCashMovement(txn.getTxnType().getId())).map(CashierTransactionData::getId)

                .filter(id -> id != null && id > 0).distinct().toList();



        final Map<Long, List<CashierLegalTenderLineData>> linesByTxnId = loadLinesBySourceId(CashLegalTenderSourceType.CASHIER_TXN,

                cashierTxnIds);

        linesByTxnId.putAll(loadLinesBySourceId(CashLegalTenderSourceType.SAVINGS_TXN, savingsTxnIds));



        for (CashierTransactionData transaction : transactions) {

            if (transaction.getId() == null || transaction.getTxnType() == null) {

                continue;

            }

            final Integer txnTypeId = transaction.getTxnType().getId();

            if (isCashierFundMovement(txnTypeId) || isSavingsCashMovement(txnTypeId)) {

                transaction.setLegalTenderLines(linesByTxnId.getOrDefault(transaction.getId(), Collections.emptyList()));

            }

        }

    }



    private Map<Long, List<CashierLegalTenderLineData>> loadLinesBySourceId(final CashLegalTenderSourceType sourceType,

            final List<Long> sourceIds) {

        if (sourceIds.isEmpty()) {

            return Collections.emptyMap();

        }

        final List<CashLegalTenderLine> lines = this.cashLegalTenderLineRepository.findBySourceTypeAndSourceIds(sourceType.getId(),

                sourceIds);

        return lines.stream().collect(Collectors.groupingBy(CashLegalTenderLine::getSourceId,

                Collectors.mapping(this::toData, Collectors.toList())));

    }



    private CashierLegalTenderLineData toData(final CashLegalTenderLine line) {

        return CashierLegalTenderLineData.builder().legalTenderId(line.getLegalTender().getId()).quantity(line.getQuantity())

                .label(line.getLegalTender().getLabel()).tenderType(line.getLegalTender().getLegalTenderTypeEnum().getCode())

                .value(line.getLegalTender().getValue()).lineAmount(line.getLineAmount()).build();

    }



    private boolean isCashierFundMovement(final Integer txnTypeId) {

        return txnTypeId != null && (CashierTxnType.ALLOCATE.getId().equals(txnTypeId) || CashierTxnType.SETTLE.getId().equals(txnTypeId));

    }



    private boolean isSavingsCashMovement(final Integer txnTypeId) {

        return txnTypeId != null && (CashierTxnType.INWARD_CASH_TXN.getId().equals(txnTypeId)

                || CashierTxnType.OUTWARD_CASH_TXN.getId().equals(txnTypeId));

    }

}


