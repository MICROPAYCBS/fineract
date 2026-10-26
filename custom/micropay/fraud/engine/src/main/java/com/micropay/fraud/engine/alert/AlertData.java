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
package com.micropay.fraud.engine.alert;

import com.micropay.fraud.engine.event.AccountType;
import com.micropay.fraud.engine.event.TransactionKind;
import com.micropay.fraud.engine.rule.RuleHit;
import com.micropay.fraud.engine.scoring.Severity;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * An alert as the dashboard sees it. Transaction fields ({@code transactionRef} to {@code currency}) are null on a
 * watchlist alert, whose {@code occurredAt} is when the client was screened and whose matches are in
 * {@code watchlistMatches}.
 */
public record AlertData(Long id, String tenantId, AlertType type, Long transactionRef, AccountType accountType, Long accountId,
        Long clientId, TransactionKind kind, BigDecimal amount, String currency, Instant occurredAt, int riskScore, Severity severity,
        AlertStatus status, AlertVerdict verdict, String assignedTo, String note, Long fineractHoldId, Instant createdAt, Instant updatedAt,
        List<RuleHit> reasons, List<WatchlistMatchData> watchlistMatches) {

    public AlertData withDetails(List<RuleHit> newReasons, List<WatchlistMatchData> newMatches) {
        return new AlertData(id, tenantId, type, transactionRef, accountType, accountId, clientId, kind, amount, currency, occurredAt,
                riskScore, severity, status, verdict, assignedTo, note, fineractHoldId, createdAt, updatedAt, newReasons, newMatches);
    }
}
