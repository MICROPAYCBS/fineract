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
package com.micropay.fraud.engine.ingest;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.ClientEvent;
import com.micropay.fraud.engine.event.FineractEvent;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Optional;
import java.util.Set;
import org.apache.fineract.avro.client.v1.ClientDataV1;
import org.apache.fineract.avro.loan.v1.LoanTransactionAdjustmentDataV1;
import org.apache.fineract.avro.loan.v1.LoanTransactionDataV1;
import org.apache.fineract.avro.loan.v1.LoanTransactionEnumDataV1;
import org.apache.fineract.avro.payment.v1.PaymentDetailDataV1;
import org.apache.fineract.avro.savings.v1.AccountTransferDataV1;
import org.apache.fineract.avro.savings.v1.SavingsAccountTransactionDataV1;
import org.apache.fineract.avro.savings.v1.SavingsAccountTransactionEnumDataV1;
import org.springframework.stereotype.Component;

/**
 * Maps the Fineract event types switched on by the fraud module (micropay_fraud_0001_external_events.xml) to engine
 * events. Anything else, such as interest postings or fee deductions, is ignored.
 */
@Component
public class FineractEventMapper {

    static final Set<String> CLIENT_EVENTS = Set.of("ClientCreateBusinessEvent", "ClientActivateBusinessEvent");
    static final Set<String> SAVINGS_EVENTS = Set.of("SavingsDepositBusinessEvent", "SavingsWithdrawalBusinessEvent",
            "SavingsAccountForceWithdrawalBusinessEvent");
    static final Set<String> LOAN_EVENTS = Set.of("LoanDisbursalTransactionBusinessEvent", "LoanTransactionMakeRepaymentPostBusinessEvent");
    static final String LOAN_ADJUSTMENT_EVENT = "LoanAdjustTransactionBusinessEvent";

    public Optional<FineractEvent> map(String tenantId, String eventKey, String type, Object payload, String createdAt,
            String businessDate) {
        if (CLIENT_EVENTS.contains(type) && payload instanceof ClientDataV1 client) {
            return Optional.of(new ClientEvent(tenantId, eventKey, type, client.getId(), parseDate(client.getActivationDate()),
                    client.getOfficeId(), client.getDisplayName()));
        }
        final Instant occurredAt = parseTimestamp(createdAt);
        final LocalDate business = parseDate(businessDate);
        if (SAVINGS_EVENTS.contains(type) && payload instanceof SavingsAccountTransactionDataV1 txn) {
            return mapSavings(tenantId, eventKey, type, txn, occurredAt, business);
        }
        if (LOAN_EVENTS.contains(type) && payload instanceof LoanTransactionDataV1 txn) {
            return mapLoan(tenantId, eventKey, type, txn, occurredAt, business);
        }
        if (LOAN_ADJUSTMENT_EVENT.equals(type) && payload instanceof LoanTransactionAdjustmentDataV1 adjustment
                && adjustment.getTransactionToAdjust() != null) {
            final LoanTransactionDataV1 txn = adjustment.getTransactionToAdjust();
            return Optional.of(new TransactionEvent(tenantId, eventKey, type, TransactionKind.REVERSAL, AccountType.LOAN, txn.getLoanId(),
                    null, txn.getId(), orZero(txn.getAmount()), currency(txn.getCurrency()), occurredAt, business,
                    channel(txn.getPaymentDetailData()), txn.getTransfer() != null));
        }
        return Optional.empty();
    }

    private Optional<FineractEvent> mapSavings(String tenantId, String eventKey, String type, SavingsAccountTransactionDataV1 txn,
            Instant occurredAt, LocalDate business) {
        final SavingsAccountTransactionEnumDataV1 txnType = txn.getTransactionType();
        final TransactionKind kind;
        if (Boolean.TRUE.equals(txn.getReversed()) || Boolean.TRUE.equals(txn.getIsReversal())) {
            kind = TransactionKind.REVERSAL;
        } else if (txnType != null && Boolean.TRUE.equals(txnType.getDeposit())) {
            kind = TransactionKind.DEPOSIT;
        } else if (txnType != null && Boolean.TRUE.equals(txnType.getWithdrawal())) {
            kind = TransactionKind.WITHDRAWAL;
        } else {
            return Optional.empty();
        }
        final AccountTransferDataV1 transfer = txn.getTransfer();
        Long clientId = null;
        if (transfer != null) {
            // The transfer names both sides; the owner of this account is the receiving side for a deposit
            final ClientDataV1 owner = kind == TransactionKind.DEPOSIT ? transfer.getToClient() : transfer.getFromClient();
            clientId = owner == null ? null : owner.getId();
        }
        return Optional.of(new TransactionEvent(tenantId, eventKey, type, kind, AccountType.SAVINGS, txn.getAccountId(), clientId,
                txn.getId(), orZero(txn.getAmount()), currency(txn.getCurrency()), occurredAt, business,
                channel(txn.getPaymentDetailData()), transfer != null));
    }

    private Optional<FineractEvent> mapLoan(String tenantId, String eventKey, String type, LoanTransactionDataV1 txn, Instant occurredAt,
            LocalDate business) {
        final LoanTransactionEnumDataV1 txnType = txn.getType();
        final TransactionKind kind;
        if (Boolean.TRUE.equals(txn.getReversed()) || Boolean.TRUE.equals(txn.getManuallyReversed())) {
            kind = TransactionKind.REVERSAL;
        } else if (txnType != null && Boolean.TRUE.equals(txnType.getDisbursement())) {
            kind = TransactionKind.LOAN_DISBURSAL;
        } else if (txnType != null && Boolean.TRUE.equals(txnType.getRepayment())) {
            kind = TransactionKind.LOAN_REPAYMENT;
        } else {
            return Optional.empty();
        }
        return Optional.of(new TransactionEvent(tenantId, eventKey, type, kind, AccountType.LOAN, txn.getLoanId(), null, txn.getId(),
                orZero(txn.getAmount()), currency(txn.getCurrency()), occurredAt, business, channel(txn.getPaymentDetailData()),
                txn.getTransfer() != null));
    }

    private static String currency(org.apache.fineract.avro.generic.v1.CurrencyDataV1 currency) {
        return currency == null ? null : currency.getCode();
    }

    private static String channel(PaymentDetailDataV1 paymentDetail) {
        return paymentDetail == null || paymentDetail.getPaymentType() == null ? null : paymentDetail.getPaymentType().getName();
    }

    private static BigDecimal orZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    static Instant parseTimestamp(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return LocalDateTime.parse(value).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException e) {
            return Instant.parse(value);
        }
    }

    static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
