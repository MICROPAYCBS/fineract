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
package org.apache.fineract.portfolio.loanaccount.data;

import com.google.gson.TypeAdapter;
import com.google.gson.annotations.JsonAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Sibling of a loan prepay template, present only when the product uses periodic accrual. {@code accruedTill} is
 * written even when it is null so a loan that has never been accrued is distinct from a missing breakdown.
 */
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@JsonAdapter(PeriodicAccrualPayoffData.Adapter.class)
public class PeriodicAccrualPayoffData {

    public static final String RESPONSE_PARAMETER = "periodicAccrualPayoff";

    private final LocalDate asOfDate;
    private final LocalDate accruedTill;
    private final boolean accrualPaused;
    private final BigDecimal principalOutstanding;
    private final BigDecimal feeChargesOutstanding;
    private final BigDecimal penaltyChargesOutstanding;
    private final BigDecimal interestOnContractOutstanding;
    private final BigDecimal interestEarnedOutstanding;
    private final BigDecimal interestPostedOutstanding;
    /** Interest recognized on the installments, including amounts already paid, waived, or written off. */
    private final BigDecimal interestBooked;
    private final BigDecimal contractPayoff;
    private final BigDecimal earnedPayoff;
    private final BigDecimal interestNotYetInBooks;

    /**
     * Writes {@code accruedTill} even when it is null. Gson's default writer drops nulls, including a null placed in a
     * {@code JsonObject} by a {@code JsonSerializer}.
     */
    public static final class Adapter extends TypeAdapter<PeriodicAccrualPayoffData> {

        @Override
        public void write(final JsonWriter out, final PeriodicAccrualPayoffData value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            final boolean previousNulls = out.getSerializeNulls();
            out.setSerializeNulls(true);
            try {
                out.beginObject();
                writeDate(out, "asOfDate", value.getAsOfDate());
                writeDate(out, "accruedTill", value.getAccruedTill());
                out.name("accrualPaused").value(value.isAccrualPaused());
                writeAmount(out, "principalOutstanding", value.getPrincipalOutstanding());
                writeAmount(out, "feeChargesOutstanding", value.getFeeChargesOutstanding());
                writeAmount(out, "penaltyChargesOutstanding", value.getPenaltyChargesOutstanding());
                writeAmount(out, "interestOnContractOutstanding", value.getInterestOnContractOutstanding());
                writeAmount(out, "interestEarnedOutstanding", value.getInterestEarnedOutstanding());
                writeAmount(out, "interestPostedOutstanding", value.getInterestPostedOutstanding());
                writeAmount(out, "interestBooked", value.getInterestBooked());
                writeAmount(out, "contractPayoff", value.getContractPayoff());
                writeAmount(out, "earnedPayoff", value.getEarnedPayoff());
                writeAmount(out, "interestNotYetInBooks", value.getInterestNotYetInBooks());
                out.endObject();
            } finally {
                out.setSerializeNulls(previousNulls);
            }
        }

        @Override
        public PeriodicAccrualPayoffData read(final JsonReader in) {
            throw new UnsupportedOperationException("Periodic accrual payoff is response-only");
        }

        private static void writeDate(final JsonWriter out, final String name, final LocalDate date) throws IOException {
            out.name(name);
            if (date == null) {
                out.nullValue();
                return;
            }
            out.beginArray();
            out.value(date.getYear());
            out.value(date.getMonthValue());
            out.value(date.getDayOfMonth());
            out.endArray();
        }

        private static void writeAmount(final JsonWriter out, final String name, final BigDecimal amount) throws IOException {
            out.name(name);
            if (amount == null) {
                out.nullValue();
            } else {
                out.value(amount);
            }
        }
    }
}
