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
package org.apache.fineract.treasury.domain;

/**
 * The treasury events that post to the GL, as recorded in {@code m_treasury_posting}.
 */
public enum TreasuryEventType {

    TRANSFER_SEND("TFS"), //
    TRANSFER_RECEIVE("TFR"), //
    TRANSFER_SEND_REVERSAL("TFSX"), //
    TRANSFER_RECEIVE_REVERSAL("TFRX"), //
    STATEMENT_ADJUSTMENT("SA"), //
    STATEMENT_ADJUSTMENT_REVERSAL("SAX"), //
    DEAL_SETTLEMENT("DS"), //
    DEAL_ACCRUAL("DA"), //
    DEAL_CASHFLOW("DC"), //
    DEAL_TERMINATION("DT"), //
    DEAL_VALUATION("DV"), //
    DEAL_IMPAIRMENT("DE"), //
    FX_REVALUATION("FXR"), //
    FX_REVALUATION_REVERSAL("FXRX"); //

    private final String code;

    TreasuryEventType(final String code) {
        this.code = code;
    }

    /** Short code used in the journal transaction id. */
    public String code() {
        return this.code;
    }
}
