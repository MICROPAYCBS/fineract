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
package org.apache.fineract.portfolio.accounting.glbalance;

/**
 * Micropay GL balance snapshot grain and policy constants.
 *
 * <p>
 * Sparse rows: only non-zero closing balances are persisted. Department {@link #UNASSIGNED_DEPARTMENT_ID} maps journal
 * lines with {@code department_id IS NULL}. {@code gl_code} is not denormalized; join {@code acc_gl_account} at read
 * time. Base currency equals foreign when {@code currency_code} matches the organisation currency; cross-currency base
 * translation uses entry-date amounts until a dedicated FX policy is introduced.
 * </p>
 */
public final class GlBalanceSnapshotConstants {

    public static final String TABLE_NAME = "m_gl_balance_snapshot";
    public static final String TRACKING_TABLE_NAME = "m_gl_balance_snapshot_tracking";

    /** Sentinel for journal lines with {@code department_id IS NULL}. Not an FK to {@code m_department}. */
    public static final long UNASSIGNED_DEPARTMENT_ID = 0L;

    public static final String GRANULARITY_DAILY = "DAILY";
    public static final String GRANULARITY_MONTHLY = "MONTHLY";

    /** Daily snapshots are retained for the most recent N calendar days; older history uses month-end snapshots. */
    public static final int DAILY_RETENTION_DAYS = 90;

    public static final String CONFIG_DAILY_RETENTION_DAYS = "gl-balance-snapshot-daily-retention-days";

    private GlBalanceSnapshotConstants() {}
}
