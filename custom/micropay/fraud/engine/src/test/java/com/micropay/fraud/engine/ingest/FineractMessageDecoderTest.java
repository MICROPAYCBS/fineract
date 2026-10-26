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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.ClientEvent;
import com.micropay.fraud.engine.event.FineractEvent;
import com.micropay.fraud.engine.event.TransactionEvent;
import com.micropay.fraud.engine.event.TransactionKind;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.avro.client.v1.ClientDataV1;
import org.junit.jupiter.api.Test;

class FineractMessageDecoderTest {

    private final FineractMessageDecoder decoder = new FineractMessageDecoder(new FineractEventMapper());

    @Test
    void decodesSavingsWithdrawal() throws IOException {
        final List<FineractEvent> events = decoder.decode(AvroMessages.savingsTransaction("SavingsWithdrawalBusinessEvent", "k1", 77L, 12L,
                false, new BigDecimal("250000.00000000"), "2026-10-20T09:15:00"));

        assertThat(events).singleElement().isInstanceOfSatisfying(TransactionEvent.class, txn -> {
            assertThat(txn.tenantId()).isEqualTo("default");
            assertThat(txn.eventKey()).isEqualTo("k1");
            assertThat(txn.kind()).isEqualTo(TransactionKind.WITHDRAWAL);
            assertThat(txn.accountType()).isEqualTo(AccountType.SAVINGS);
            assertThat(txn.accountId()).isEqualTo(12L);
            assertThat(txn.transactionId()).isEqualTo(77L);
            assertThat(txn.amount()).isEqualByComparingTo("250000");
            assertThat(txn.currency()).isEqualTo("UGX");
            assertThat(txn.occurredAt()).isEqualTo(Instant.parse("2026-10-20T09:15:00Z"));
            assertThat(txn.clientId()).isNull();
        });
    }

    @Test
    void decodesLoanRepaymentAndClientActivation() throws IOException {
        assertThat(decoder.decode(AvroMessages.loanRepayment("k2", 5L, 9L, new BigDecimal("100000"), "2026-10-20T10:00:00")))
                .singleElement().isInstanceOfSatisfying(TransactionEvent.class, txn -> {
                    assertThat(txn.kind()).isEqualTo(TransactionKind.LOAN_REPAYMENT);
                    assertThat(txn.accountType()).isEqualTo(AccountType.LOAN);
                    assertThat(txn.accountId()).isEqualTo(9L);
                });
        assertThat(decoder.decode(AvroMessages.clientEvent("k3", AvroMessages.client(3L, "2026-10-01")))).singleElement()
                .isInstanceOfSatisfying(ClientEvent.class, client -> {
                    assertThat(client.clientId()).isEqualTo(3L);
                    assertThat(client.activationDate()).isEqualTo(LocalDate.of(2026, 10, 1));
                });
    }

    @Test
    void splitsBulkEventsAndKeysEachPart() throws IOException {
        final ClientDataV1 client = AvroMessages.client(4L, "2026-10-02");
        final byte[] bulk = AvroMessages.bulk("bulk-1",
                List.of(AvroMessages.bulkItem(1L, "ClientCreateBusinessEvent", ClientDataV1.class.getName(), client.toByteBuffer()),
                        AvroMessages.bulkItem(2L, "LoanBalanceChangedBusinessEvent", ClientDataV1.class.getName(), client.toByteBuffer())));

        final List<FineractEvent> events = decoder.decode(bulk);

        assertThat(events).singleElement().extracting(FineractEvent::eventKey).isEqualTo("bulk-1#1");
    }

    @Test
    void ignoresEventTypesFraudDoesNotUse() throws IOException {
        assertThat(decoder.decode(AvroMessages.savingsTransaction("SavingsPostInterestBusinessEvent", "k4", 1L, 1L, true, BigDecimal.TEN,
                "2026-10-20T10:00:00"))).isEmpty();
    }

    @Test
    void rejectsGarbage() {
        assertThatThrownBy(() -> decoder.decode(new byte[] { 1, 2, 3 })).isInstanceOf(IOException.class);
    }

    @Test
    void unknownPayloadSchemaIsAnError() throws IOException {
        final byte[] bulk = AvroMessages.bulk("bulk-2", List
                .of(AvroMessages.bulkItem(1L, "ClientCreateBusinessEvent", "org.apache.fineract.avro.NoSuchV1", ByteBuffer.allocate(0))));
        assertThatThrownBy(() -> decoder.decode(bulk)).isInstanceOf(IOException.class);
    }
}
