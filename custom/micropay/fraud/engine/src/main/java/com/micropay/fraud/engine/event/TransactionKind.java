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
package com.micropay.fraud.engine.event;

public enum TransactionKind {

    DEPOSIT(false), WITHDRAWAL(true), LOAN_DISBURSAL(false), LOAN_REPAYMENT(true), REVERSAL(false);

    private final boolean debit;

    TransactionKind(boolean debit) {
        this.debit = debit;
    }

    /**
     * True when money leaves the client: a withdrawal, or a loan repayment paid out of the client's funds.
     */
    public boolean isDebit() {
        return debit;
    }

    /**
     * True when money reaches the client: a deposit or a loan disbursal.
     */
    public boolean isCredit() {
        return this == DEPOSIT || this == LOAN_DISBURSAL;
    }
}
