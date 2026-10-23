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
package org.apache.fineract.treasury.limits;

import java.util.EnumSet;
import java.util.Set;

/**
 * What a limit measures, the scopes and units it allows, and whether it can take its levels from what it measures
 * ({@code useOwnLevels}: a ratio's limit and warning, a counterparty's exposure limit and warning percentage, an
 * account's minimum and maximum balance). New metrics, such as the FX position, are added here with an evaluator.
 */
public enum LimitMetric {

    /** A ratio of a RATIOS run, by ratio code. */
    RATIO(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE), EnumSet.of(LimitUnit.PERCENT), true,
            true), //
    /** A bucket's net or cumulative gap of a LADDER run, by bucket code; an amount or a percentage. */
    BUCKET_GAP(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE),
            EnumSet.of(LimitUnit.AMOUNT, LimitUnit.PERCENT), false, true), //
    /** The top N depositors' share of deposits (savings and term deposits by client or group). */
    DEPOSITOR_CONCENTRATION(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE),
            EnumSet.of(LimitUnit.PERCENT), false, false), //
    /** The top N funding counterparties' share of borrowings (settled liability deals). */
    FUNDING_CONCENTRATION(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE),
            EnumSet.of(LimitUnit.PERCENT), false, false), //
    /** A counterparty's exposure as at deal capture: a percentage of its exposure limit, or an amount. */
    COUNTERPARTY_EXPOSURE(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.COUNTERPARTY),
            EnumSet.of(LimitUnit.PERCENT, LimitUnit.AMOUNT), true, false), //
    /** A treasury account's GL balance. */
    ACCOUNT_BALANCE(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE, LimitScopeType.ACCOUNT),
            EnumSet.of(LimitUnit.AMOUNT), true, false), //
    /**
     * The survival horizon of the scope's STRESS runs, in days: of the scenario named by {@code metricKey}, or the
     * shortest over every scenario. Beyond the horizon counts as the first day of the last bucket.
     */
    SURVIVAL_DAYS(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE), EnumSet.of(LimitUnit.DAYS), false,
            false), //
    /** A foreign currency's net open position, long or short, in base at MID, as a percentage of core capital. */
    FX_SINGLE_NOP(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY), EnumSet.of(LimitUnit.PERCENT), false, false), //
    /**
     * The aggregate net open position (shorthand: the greater of the longs and the shorts) as a percentage of core
     * capital.
     */
    FX_AGGREGATE_NOP(EnumSet.of(LimitScopeType.INSTITUTION), EnumSet.of(LimitUnit.PERCENT), false, false), //
    /**
     * A REPRICING run's gap as a percentage of total assets: the bucket named by {@code metricKey}, or the cumulative
     * gap of the buckets within one year.
     */
    REPRICING_GAP_PCT(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE), EnumSet.of(LimitUnit.PERCENT),
            false, false), //
    /**
     * Earnings at risk: a REPRICING run's fall in net interest income over twelve months as a percentage of core
     * capital, for the shock in basis points named by {@code metricKey}, or the worst of its shocks.
     */
    EAR_PCT(EnumSet.of(LimitScopeType.INSTITUTION, LimitScopeType.CURRENCY, LimitScopeType.OFFICE), EnumSet.of(LimitUnit.PERCENT), false,
            false), //
    /** A TRUST_RECON run's eligible trust assets as a percentage of e-money outstanding (NPS Act 2020 s.60). */
    TRUST_COVERAGE(EnumSet.of(LimitScopeType.INSTITUTION), EnumSet.of(LimitUnit.PERCENT), false, false), //
    /**
     * A TRUST_RECON run's trust money at each institution as a percentage of e-money, one observation per institution;
     * it does not apply while e-money is at most UGX 500m (NPS Regulations 2021 reg 15(2)-(3)).
     */
    TRUST_BANK_CONCENTRATION(EnumSet.of(LimitScopeType.INSTITUTION), EnumSet.of(LimitUnit.PERCENT), false, false), //
    /** A TRUST_RECON run's shortfall: e-money less the eligible trust assets, when positive (s.47(1)(a)). */
    TRUST_SHORTFALL(EnumSet.of(LimitScopeType.INSTITUTION), EnumSet.of(LimitUnit.AMOUNT), false, false); //

    private final Set<LimitScopeType> scopes;
    private final Set<LimitUnit> units;
    private final boolean ownLevels;
    private final boolean keyed;

    LimitMetric(final Set<LimitScopeType> scopes, final Set<LimitUnit> units, final boolean ownLevels, final boolean keyed) {
        this.scopes = scopes;
        this.units = units;
        this.ownLevels = ownLevels;
        this.keyed = keyed;
    }

    public boolean allows(final LimitScopeType scope) {
        return this.scopes.contains(scope);
    }

    public boolean allows(final LimitUnit unit) {
        return this.units.contains(unit);
    }

    public Set<LimitScopeType> scopes() {
        return this.scopes;
    }

    public Set<LimitUnit> units() {
        return this.units;
    }

    public boolean canUseOwnLevels() {
        return this.ownLevels;
    }

    /** The unit of the own levels. */
    public LimitUnit ownUnit() {
        return this == ACCOUNT_BALANCE ? LimitUnit.AMOUNT : LimitUnit.PERCENT;
    }

    /** Needs a {@code metricKey}: the ratio code or the bucket code. */
    public boolean isKeyed() {
        return this.keyed;
    }

    /** Takes a {@code metricKey}: needs one when keyed; SURVIVAL_DAYS may name a scenario. */
    public boolean acceptsKey() {
        return this.keyed || this == SURVIVAL_DAYS || this == REPRICING_GAP_PCT || this == EAR_PCT;
    }

    public boolean isConcentration() {
        return this == DEPOSITOR_CONCENTRATION || this == FUNDING_CONCENTRATION;
    }

    /** Read from a run of the scope rather than from live data. */
    public boolean readsRun() {
        return this == RATIO || this == BUCKET_GAP || this == SURVIVAL_DAYS || this == REPRICING_GAP_PCT || this == EAR_PCT;
    }
}
