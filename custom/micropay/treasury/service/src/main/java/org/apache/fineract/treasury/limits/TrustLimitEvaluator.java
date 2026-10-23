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

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRunTrust;
import org.apache.fineract.treasury.domain.TreasuryRunTrustLine;
import org.apache.fineract.treasury.domain.TreasuryRunTrustLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunTrustRepository;
import org.apache.fineract.treasury.trust.TrustCalculator;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The latest TRUST_RECON run of the institution on or before the date: its coverage, its shortfall, or the share of
 * e-money at each institution.
 */
@RequiredArgsConstructor
abstract class TrustLimitEvaluator implements LimitEvaluator {

    private final JdbcTemplate jdbcTemplate;
    private final TreasuryRunTrustRepository trustRepository;
    private final TreasuryRunTrustLineRepository lineRepository;

    @Override
    public List<Observation> observe(final TreasuryLimit limit, final EvaluationContext context) {
        if (!context.isInstitution()) {
            return List.of();
        }
        final List<Long> runs = this.jdbcTemplate.queryForList("""
                SELECT id FROM m_treasury_run WHERE run_type = 'TRUST_RECON' AND office_id IS NULL AND currency_code = ? AND as_of_date <= ?
                ORDER BY as_of_date DESC, id DESC LIMIT 1
                """, Long.class, context.currencyCode(), context.asOfDate());
        final Optional<TreasuryRunTrust> trust = runs.isEmpty() ? Optional.empty() : this.trustRepository.findByRunId(runs.getFirst());
        if (trust.isEmpty()) {
            return List.of(
                    LimitSubjects.unavailable(limit, context, "RUN", null, "No trust reconciliation on or before " + context.asOfDate()));
        }
        final TreasuryRunTrust t = trust.get();
        return switch (metric()) {
            case TRUST_COVERAGE -> List.of(t.getCoveragePercent() == null
                    ? LimitSubjects.unavailable(limit, context, "RUN", t.getRunId(), "No e-money outstanding (run " + t.getRunId() + ")")
                    : LimitSubjects.observe(limit, context, t.getCoveragePercent(), "RUN", t.getRunId(),
                            "Eligible trust assets " + LimitSubjects.amount(t.getEligibleTotal()) + " against e-money "
                                    + LimitSubjects.amount(t.getEmoneyTotal()) + " (run " + t.getRunId() + ")"));
            case TRUST_SHORTFALL -> List.of(LimitSubjects.observe(limit, context, t.getShortfall(), "RUN", t.getRunId(),
                    "E-money " + LimitSubjects.amount(t.getEmoneyTotal()) + ", eligible trust assets "
                            + LimitSubjects.amount(t.getEligibleTotal()) + " (run " + t.getRunId() + ")"));
            default -> concentration(limit, context, t);
        };
    }

    private List<Observation> concentration(final TreasuryLimit limit, final EvaluationContext context, final TreasuryRunTrust t) {
        final Map<Long, BigDecimal> amounts = new LinkedHashMap<>();
        final Map<Long, String> names = new LinkedHashMap<>();
        for (final TreasuryRunTrustLine line : this.lineRepository.findByRunIdOrderByIdAsc(t.getRunId())) {
            if ("ASSET".equals(line.getLineType()) && line.getAssetType() != null && line.getAssetType().atAnInstitution()) {
                final Long id = line.getInstitutionId() == null ? 0L : line.getInstitutionId();
                amounts.merge(id, line.getAmount(), BigDecimal::add);
                names.putIfAbsent(id, line.getInstitutionName());
            }
        }
        final List<Observation> observations = new ArrayList<>();
        for (final Map.Entry<Long, BigDecimal> e : amounts.entrySet()) {
            final Long institution = e.getKey() == 0L ? null : e.getKey();
            final BigDecimal percent = TrustCalculator.percent(e.getValue(), t.getEmoneyTotal());
            final String label = (names.get(e.getKey()) == null ? "Institution not set" : names.get(e.getKey())) + ", "
                    + context.currencyCode();
            final String detail = "Trust money " + LimitSubjects.amount(e.getValue()) + " of e-money "
                    + LimitSubjects.amount(t.getEmoneyTotal()) + " (run " + t.getRunId() + ")";
            if (percent == null) {
                observations.add(Observation.unavailable(LimitScopeType.COUNTERPARTY, institution, label, limit.getComparison(),
                        limit.getWarningLevel(), limit.getHardLevel(), "RUN", t.getRunId(), detail + ": no e-money"));
                continue;
            }
            final Observation observation = Observation.of(LimitScopeType.COUNTERPARTY, institution, label, percent, limit.getComparison(),
                    limit.getWarningLevel(), limit.getHardLevel(), "RUN", t.getRunId(), detail);
            observations.add(t.isConcentrationApplies() ? observation
                    : new Observation(observation.subjectType(), observation.subjectId(), observation.subjectLabel(), observation.value(),
                            observation.comparison(), observation.warning(), observation.hard(), null, observation.sourceType(),
                            observation.sourceId(),
                            detail + ": total e-money at most " + LimitSubjects.amount(TrustCalculator.CONCENTRATION_THRESHOLD)
                                    + ", the limit does not apply (Reg 15(3))"));
        }
        return observations;
    }
}
