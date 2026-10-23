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
package org.apache.fineract.treasury.trust;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The trust reconciliation of a date: e-money outstanding against the assets held in trust (NPS Act 2020, NPS
 * Regulations 2021).
 * <ul>
 * <li>Coverage: the eligible assets, liquid and unencumbered (s.60), as a percentage of e-money; at least 100%.</li>
 * <li>Shortfall: e-money less the eligible assets, when positive (s.47(1)(a): e-money is issued only against an equal
 * amount in trust).</li>
 * <li>Concentration: what one institution holds (demand and term deposits) as a percentage of e-money; at most 20%,
 * unless the total e-money is at most UGX 500m (Reg 15(2)-(3)).</li>
 * <li>Difference: everything held in trust, at carrying value, less e-money.</li>
 * </ul>
 */
public final class TrustCalculator {

    /** Reg 15(3): below or at this total e-money the per-institution limit does not apply. */
    public static final BigDecimal CONCENTRATION_THRESHOLD = new BigDecimal("500000000");
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    private TrustCalculator() {}

    /** An asset held in trust; {@code institutionId} null for government securities. */
    public record Asset(TrustAssetType type, Long institutionId, String institutionName, Long trustAccountId, Long dealId, String label,
            BigDecimal amount, BigDecimal encumbered) {

        /** Liquid and unencumbered. */
        public BigDecimal eligible() {
            return this.type.liquid() ? this.amount.subtract(this.encumbered).max(BigDecimal.ZERO) : BigDecimal.ZERO;
        }
    }

    public record Concentration(Long institutionId, String institutionName, BigDecimal amount, BigDecimal percent) {
    }

    public record Result(BigDecimal emoney, BigDecimal trustTotal, BigDecimal eligible, BigDecimal encumbered, BigDecimal ineligible,
            BigDecimal difference, BigDecimal shortfall, BigDecimal coveragePercent, boolean concentrationApplies,
            List<Concentration> concentrations, BigDecimal maxConcentrationPercent) {
    }

    public static Result reconcile(final Map<EMoneyCategory, BigDecimal> emoney, final List<Asset> assets, final int scale) {
        final BigDecimal total = emoney.values().stream().filter(Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal trust = assets.stream().map(Asset::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal eligible = assets.stream().map(Asset::eligible).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal encumbered = assets.stream().filter(a -> a.type().liquid()).map(a -> a.encumbered().min(a.amount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal ineligible = trust.subtract(eligible).subtract(encumbered);
        final Map<Long, BigDecimal> byInstitution = new LinkedHashMap<>();
        final Map<Long, String> names = new LinkedHashMap<>();
        for (final Asset asset : assets) {
            if (asset.type().atAnInstitution()) {
                byInstitution.merge(asset.institutionId() == null ? 0L : asset.institutionId(), asset.amount(), BigDecimal::add);
                names.putIfAbsent(asset.institutionId() == null ? 0L : asset.institutionId(),
                        asset.institutionId() == null ? "Institution not set" : asset.institutionName());
            }
        }
        final List<Concentration> concentrations = new ArrayList<>();
        byInstitution.forEach(
                (id, amount) -> concentrations.add(new Concentration(id == 0L ? null : id, names.get(id), amount, percent(amount, total))));
        final BigDecimal max = concentrations.stream().map(Concentration::percent).filter(Objects::nonNull).max(BigDecimal::compareTo)
                .orElse(null);
        return new Result(total, trust, eligible, encumbered, ineligible.max(BigDecimal.ZERO), trust.subtract(total),
                total.subtract(eligible).max(BigDecimal.ZERO).setScale(scale, RoundingMode.HALF_UP), percent(eligible, total),
                total.compareTo(CONCENTRATION_THRESHOLD) > 0, concentrations, max);
    }

    /** A share of e-money in percent, four places; null without e-money. */
    public static BigDecimal percent(final BigDecimal part, final BigDecimal emoney) {
        return emoney == null || emoney.signum() == 0 ? null : part.multiply(HUNDRED).divide(emoney, 4, RoundingMode.HALF_UP);
    }

    /** The asset type of a deal held in trust, from its instrument kind; null when it cannot back e-money. */
    public static TrustAssetType assetType(final String instrumentKind) {
        return switch (instrumentKind) {
            case "TREASURY_BILL" -> TrustAssetType.TREASURY_BILL;
            case "GOVERNMENT_BOND" -> TrustAssetType.TREASURY_BOND;
            case "MONEY_MARKET_PLACEMENT", "FIXED_DEPOSIT_PLACED" -> TrustAssetType.TERM_DEPOSIT;
            default -> null;
        };
    }
}
