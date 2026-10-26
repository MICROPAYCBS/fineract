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

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.List;
import org.apache.fineract.avro.BulkMessageItemV1;
import org.apache.fineract.avro.BulkMessagePayloadV1;
import org.apache.fineract.avro.MessageV1;
import org.apache.fineract.avro.client.v1.ClientDataV1;
import org.apache.fineract.avro.generic.v1.CurrencyDataV1;
import org.apache.fineract.avro.loan.v1.LoanTransactionDataV1;
import org.apache.fineract.avro.loan.v1.LoanTransactionEnumDataV1;
import org.apache.fineract.avro.savings.v1.SavingsAccountTransactionDataV1;
import org.apache.fineract.avro.savings.v1.SavingsAccountTransactionEnumDataV1;

/**
 * Builds Kafka records exactly as Fineract does: an Avro MessageV1 wrapping an Avro payload.
 */
public final class AvroMessages {

    private AvroMessages() {}

    public static byte[] savingsTransaction(String type, String key, long txnId, long accountId, boolean deposit, BigDecimal amount,
            String createdAt) throws IOException {
        final SavingsAccountTransactionDataV1 payload = SavingsAccountTransactionDataV1.newBuilder().setId(txnId).setAccountId(accountId)
                .setAccountNo("000" + accountId).setDate("2026-10-20").setAmount(amount).setReversed(false)
                .setCurrency(CurrencyDataV1.newBuilder().setCode("UGX").setDecimalPlaces(0).setInMultiplesOf(1).build())
                .setTransactionType(SavingsAccountTransactionEnumDataV1.newBuilder().setId(deposit ? 1L : 2L)
                        .setCode(deposit ? "savingsAccountTransactionType.deposit" : "savingsAccountTransactionType.withdrawal")
                        .setValue(deposit ? "Deposit" : "Withdrawal").setDeposit(deposit).setWithdrawal(!deposit).build())
                .build();
        return message(type, key, SavingsAccountTransactionDataV1.class.getName(), payload.toByteBuffer(), createdAt);
    }

    public static byte[] loanRepayment(String key, long txnId, long loanId, BigDecimal amount, String createdAt) throws IOException {
        final LoanTransactionDataV1 payload = LoanTransactionDataV1.newBuilder().setId(txnId).setLoanId(loanId).setDate("2026-10-20")
                .setAmount(amount).setManuallyReversed(false)
                .setCurrency(CurrencyDataV1.newBuilder().setCode("UGX").setDecimalPlaces(0).setInMultiplesOf(1).build())
                .setType(LoanTransactionEnumDataV1.newBuilder().setId(2).setCode("loanTransactionType.repayment").setValue("Repayment")
                        .setRepayment(true).build())
                .build();
        return message("LoanTransactionMakeRepaymentPostBusinessEvent", key, LoanTransactionDataV1.class.getName(), payload.toByteBuffer(),
                createdAt);
    }

    public static ClientDataV1 client(long clientId, String activationDate) {
        return ClientDataV1.newBuilder().setId(clientId).setActive(true).setActivationDate(activationDate).setOfficeId(1L)
                .setDisplayName("Jane Doe").build();
    }

    public static byte[] clientEvent(String key, ClientDataV1 client) throws IOException {
        return message("ClientActivateBusinessEvent", key, ClientDataV1.class.getName(), client.toByteBuffer(), "2026-10-01T08:00:00");
    }

    public static byte[] bulk(String key, List<BulkMessageItemV1> items) throws IOException {
        final BulkMessagePayloadV1 payload = BulkMessagePayloadV1.newBuilder().setDatas(items).build();
        return message("BulkBusinessEvent", key, BulkMessagePayloadV1.class.getName(), payload.toByteBuffer(), "2026-10-20T09:00:00");
    }

    public static BulkMessageItemV1 bulkItem(long id, String type, String schema, ByteBuffer data) {
        return BulkMessageItemV1.newBuilder().setId(id).setType(type).setCategory("SAVINGS").setDataschema(schema).setData(data).build();
    }

    private static byte[] message(String type, String key, String schema, ByteBuffer data, String createdAt) throws IOException {
        final MessageV1 message = MessageV1.newBuilder().setId(1L).setSource("fineract").setType(type).setCategory("TEST")
                .setCreatedAt(createdAt).setBusinessDate("2026-10-20").setTenantId("default").setIdempotencyKey(key).setDataschema(schema)
                .setData(data).build();
        final ByteBuffer buffer = message.toByteBuffer();
        final byte[] bytes = new byte[buffer.remaining()];
        buffer.get(bytes);
        return bytes;
    }
}
