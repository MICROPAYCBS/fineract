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
package org.apache.fineract.treasury.service;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.sql.Types;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.data.TreasuryRepricingData;
import org.apache.fineract.treasury.domain.TreasuryCoreCapital;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunLine;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingBucket;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingShock;
import org.apache.fineract.treasury.domain.TreasuryRunRepricingShockRepository;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.repricing.RateType;
import org.apache.fineract.treasury.repricing.RepricingBuilder;
import org.apache.fineract.treasury.repricing.RepricingPosition;
import org.apache.fineract.treasury.repricing.RepricingSide;
import org.apache.fineract.treasury.repricing.RepricingSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Computes a REPRICING run: every {@link RepricingSource}'s positions bucketed by repricing date with the rule set's
 * buckets, the GL totals of the currency and scope, the gaps and, per rate shock, the change in net interest income
 * over twelve months (earnings at risk, as a percentage of the core capital in effect). Lines are stored as run lines
 * (inflow RSA, outflow RSL, signed contractual, the rate type; non-sensitive ones in bucket 0, {@code NON_SENSITIVE}).
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryRepricingRunService {

    static final String NON_SENSITIVE_BUCKET = "NON_SENSITIVE";
    public static final List<Integer> DEFAULT_SHOCKS = List.of(200, -200);

    private static final String TOTALS_SQL = """
            SELECT g.classification_enum, SUM(CASE WHEN je.type_enum = 2 THEN je.amount ELSE -je.amount END) AS dc
            FROM acc_gl_journal_entry je JOIN acc_gl_account g ON g.id = je.account_id
            WHERE je.currency_code = :currency AND je.entry_date <= :asOf AND g.classification_enum IN (1, 2)
              AND (:officeId IS NULL OR je.office_id = :officeId)
            GROUP BY g.classification_enum
            """;

    private final List<RepricingSource> sources;
    private final TreasuryRunLineRepository lineRepository;
    private final TreasuryRunRepricingBucketRepository bucketRepository;
    private final TreasuryRunRepricingShockRepository shockRepository;
    private final TreasuryFxPositionService fxPositionService;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RepricingBuilder.Gap compute(final TreasuryRun run, final LadderContext context, final List<Integer> shocks) {
        final List<RepricingPosition> positions = new ArrayList<>();
        for (final RepricingSource source : this.sources) {
            positions.addAll(source.positions(context));
        }
        final BigDecimal[] totals = { BigDecimal.ZERO, BigDecimal.ZERO };
        this.jdbcTemplate.query(TOTALS_SQL, new MapSqlParameterSource("currency", run.getCurrencyCode()).addValue("asOf", run.getAsOfDate())
                .addValue("officeId", run.getOfficeId(), Types.BIGINT), rs -> {
                    if (rs.getInt("classification_enum") == 1) {
                        totals[0] = totals[0].add(rs.getBigDecimal("dc"));
                    } else {
                        totals[1] = totals[1].add(rs.getBigDecimal("dc").negate());
                    }
                });
        final RepricingBuilder.Gap gap = RepricingBuilder.build(context, positions, totals[0]);
        for (final RepricingBuilder.Line line : gap.lines()) {
            final TreasuryRunLine row = new TreasuryRunLine();
            row.setRunId(run.getId());
            row.setBucketSeq(line.bucket() == null ? 0 : line.bucket().seq());
            row.setBucketCode(line.bucket() == null ? NON_SENSITIVE_BUCKET : line.bucket().code());
            row.setSource(line.source());
            row.setLineCode(line.lineCode());
            row.setProductId(line.productId());
            row.setProductName(line.productName() == null || line.productName().length() <= 150 ? line.productName()
                    : line.productName().substring(0, 150));
            final boolean asset = line.side() == RepricingSide.ASSET;
            row.setInflow(asset ? line.amount() : BigDecimal.ZERO);
            row.setOutflow(asset ? BigDecimal.ZERO : line.amount());
            row.setContractual(asset ? line.amount() : line.amount().negate());
            row.setBehavioural(BigDecimal.ZERO);
            row.setRateType(line.rateType());
            this.lineRepository.save(row);
        }
        for (final RepricingBuilder.BucketGap b : gap.buckets()) {
            final TreasuryRunRepricingBucket row = new TreasuryRunRepricingBucket();
            row.setRunId(run.getId());
            row.setBucketSeq(b.bucket().seq());
            row.setBucketCode(b.bucket().code());
            row.setFromDay(b.bucket().fromDay());
            row.setToDay(b.bucket().toDay());
            row.setRsaFixed(b.assets().getOrDefault(RateType.FIXED, BigDecimal.ZERO));
            row.setRsaFloating(b.assets().getOrDefault(RateType.FLOATING, BigDecimal.ZERO));
            row.setRsaAdministered(b.assets().getOrDefault(RateType.ADMINISTERED, BigDecimal.ZERO));
            row.setRslFixed(b.liabilities().getOrDefault(RateType.FIXED, BigDecimal.ZERO));
            row.setRslFloating(b.liabilities().getOrDefault(RateType.FLOATING, BigDecimal.ZERO));
            row.setRslAdministered(b.liabilities().getOrDefault(RateType.ADMINISTERED, BigDecimal.ZERO));
            row.setRsa(b.rsa());
            row.setRsl(b.rsl());
            row.setGap(b.gap());
            row.setCumulativeGap(b.cumulativeGap());
            row.setGapPercent(b.gapPercent());
            row.setCumulativePercent(b.cumulativePercent());
            row.setRsaRslRatio(b.ratio());
            this.bucketRepository.save(row);
        }
        final BigDecimal capital = this.fxPositionService.coreCapital(run.getAsOfDate()).map(TreasuryCoreCapital::getAmount).orElse(null);
        for (final Integer shock : shocks) {
            final TreasuryRunRepricingShock row = new TreasuryRunRepricingShock();
            row.setRunId(run.getId());
            row.setShockBp(shock);
            row.setDeltaNii(RepricingBuilder.deltaNii(gap.buckets(), shock, context.scale()));
            row.setEarPercent(RepricingBuilder.earPercent(row.getDeltaNii(), capital));
            this.shockRepository.save(row);
        }
        this.lineRepository.flush();
        this.bucketRepository.flush();
        this.shockRepository.flush();
        run.setTotalAssets(totals[0]);
        run.setTotalLiabilities(totals[1]);
        run.setCoreCapital(capital);
        return gap;
    }

    public void deleteRun(final Long runId) {
        this.bucketRepository.deleteByRunId(runId);
        this.shockRepository.deleteByRunId(runId);
        this.bucketRepository.flush();
        this.shockRepository.flush();
    }

    public TreasuryRepricingData data(final TreasuryRun run) {
        final List<TreasuryRunRepricingBucket> buckets = this.bucketRepository.findByRunIdOrderByBucketSeqAsc(run.getId());
        final List<TreasuryRunLine> lines = this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.getId());
        final Map<String, BigDecimal> breakdown = new LinkedHashMap<>();
        for (final TreasuryRunLine l : lines) {
            final boolean asset = l.getInflow().signum() != 0;
            breakdown.merge(
                    String.join("|", l.getBucketCode(), l.getSource().name(), asset ? "ASSET" : "LIABILITY",
                            l.getRateType() == null ? "" : l.getRateType().name()),
                    asset ? l.getInflow() : l.getOutflow(), BigDecimal::add);
        }
        final Optional<TreasuryRunRepricingBucket> year = buckets.stream()
                .filter(b -> b.getToDay() != null && b.getToDay() <= RepricingBuilder.YEAR_DAYS).reduce((a, b) -> b);
        final BigDecimal nsAssets = sum(lines, true);
        final BigDecimal nsLiabilities = sum(lines, false);
        return new TreasuryRepricingData(run.getId(), run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(), run.getRuleSetId(),
                run.getRuleSetVersion(), run.getSourceRunId(), run.getTotalAssets(), run.getTotalLiabilities(),
                buckets.stream().map(TreasuryRunRepricingBucket::getRsa).reduce(BigDecimal.ZERO, BigDecimal::add),
                buckets.stream().map(TreasuryRunRepricingBucket::getRsl).reduce(BigDecimal.ZERO, BigDecimal::add), nsAssets, nsLiabilities,
                year.map(TreasuryRunRepricingBucket::getCumulativeGap).orElse(BigDecimal.ZERO),
                year.map(TreasuryRunRepricingBucket::getCumulativePercent).orElse(null), run.getCoreCapital(),
                buckets.stream()
                        .map(b -> new TreasuryRepricingData.Bucket(b.getBucketSeq(), b.getBucketCode(), b.getFromDay(), b.getToDay(),
                                b.getRsaFixed(), b.getRsaFloating(), b.getRsaAdministered(), b.getRslFixed(), b.getRslFloating(),
                                b.getRslAdministered(), b.getRsa(), b.getRsl(), b.getGap(), b.getCumulativeGap(), b.getGapPercent(),
                                b.getCumulativePercent(), b.getRsaRslRatio()))
                        .toList(),
                this.shockRepository.findByRunIdOrderByShockBpDesc(run.getId()).stream()
                        .map(s -> new TreasuryRepricingData.Shock(s.getShockBp(), s.getDeltaNii(), s.getEarPercent())).toList(),
                breakdown.entrySet().stream().map(e -> {
                    final String[] k = e.getKey().split("\\|", -1);
                    return new TreasuryRepricingData.Breakdown(NON_SENSITIVE_BUCKET.equals(k[0]) ? null : k[0], k[1], k[2], k[3],
                            e.getValue());
                }).toList());
    }

    private static BigDecimal sum(final List<TreasuryRunLine> lines, final boolean assets) {
        return lines.stream().filter(l -> NON_SENSITIVE_BUCKET.equals(l.getBucketCode())).map(l -> assets ? l.getInflow() : l.getOutflow())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
