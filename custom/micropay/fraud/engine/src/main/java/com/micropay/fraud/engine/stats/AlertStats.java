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
package com.micropay.fraud.engine.stats;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Figures for the dashboard overview and reports.
 *
 * @param open
 *            the current backlog (alerts not closed), whatever the period
 * @param byType
 *            alerts raised in the period, by type; likewise {@code bySeverity}, {@code byStatus} and {@code byVerdict}
 *            ({@code NONE} for alerts without a verdict)
 * @param daily
 *            alerts raised per day (UTC) in the period
 * @param reasons
 *            per reason code, how many alerts in the period carried it and how analysts judged them, which shows how
 *            precise each rule is
 */
public record AlertStats(Instant from, Instant to, long total, long transactionsScored, Backlog open, Map<String, Long> byType,
        Map<String, Long> bySeverity, Map<String, Long> byStatus, Map<String, Long> byVerdict, List<Day> daily, List<ReasonStats> reasons) {

    public record Backlog(long total, long high, long medium, long low, long unassigned) {
    }

    public record Day(LocalDate date, long transaction, long watchlist) {
    }

    public record ReasonStats(String code, long alerts, long fraud, long notFraud) {
    }
}
