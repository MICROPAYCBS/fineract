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

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.data.TreasuryStressData;
import org.apache.fineract.treasury.domain.TreasuryAssetClassRule;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryDealTypeRepository;
import org.apache.fineract.treasury.domain.TreasuryFundingLine;
import org.apache.fineract.treasury.domain.TreasuryFundingLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunLineRepository;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucket;
import org.apache.fineract.treasury.domain.TreasuryRunStressBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRunStressCapacity;
import org.apache.fineract.treasury.domain.TreasuryRunStressCapacityRepository;
import org.apache.fineract.treasury.domain.TreasuryRunStressLine;
import org.apache.fineract.treasury.domain.TreasuryRunStressLineRepository;
import org.apache.fineract.treasury.domain.TreasuryScenario;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.stress.LadderLine;
import org.apache.fineract.treasury.stress.Shock;
import org.apache.fineract.treasury.stress.ShockType;
import org.apache.fineract.treasury.stress.StressCalculator;
import org.apache.fineract.treasury.stress.StressCalculator.BucketResult;
import org.apache.fineract.treasury.stress.StressCalculator.FundingAccess;
import org.apache.fineract.treasury.stress.StressCalculator.Survival;
import org.apache.fineract.treasury.stress.StressLine;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Computes a STRESS run from a ladder run and a scenario version and stores the lines before and after the shocks, each
 * bucket's baseline and stressed gaps with the capacity, the capacity items and the survival horizon.
 *
 * <p>
 * Capacity: the liquid asset classes of the ladder run's rule set that hold instruments (instrument kinds or deal
 * types), at their P3.2 value (carrying amount, less holdings beyond the cut-off and the pledged share) after the
 * class's haircut and the scenario's further haircut; their deal types' inflows within the cut-off leave the stressed
 * ladder as they are sold instead. Treasury account classes are left out: the ladder's opening stock already holds
 * those balances. And, for an institution run, the active funding lines in the currency not expired on the date that
 * count as capacity (committed lines received by default), available from the first bucket ending on or after their
 * days to access, unless the scenario freezes them.
 * </p>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryStressRunService {

    private final TreasuryRuleSetService ruleSetService;
    private final TreasuryScenarioService scenarioService;
    private final TreasuryRatioInputs inputs;
    private final TreasuryDealTypeRepository dealTypeRepository;
    private final TreasuryFundingLineRepository fundingLineRepository;
    private final TreasuryRunLineRepository ladderLineRepository;
    private final TreasuryRunStressLineRepository lineRepository;
    private final TreasuryRunStressBucketRepository bucketRepository;
    private final TreasuryRunStressCapacityRepository capacityRepository;

    public Survival compute(final TreasuryRun run, final TreasuryRun ladderRun, final TreasuryScenario scenario, final int scale) {
        final List<Bucket> buckets = this.ruleSetService.buckets(ladderRun.getRuleSetId());
        final Map<Integer, Bucket> bySeq = buckets.stream().collect(Collectors.toMap(Bucket::seq, b -> b));
        final List<Shock> shocks = this.scenarioService.shocks(scenario.getId());
        final List<LadderLine> lines = this.ladderLineRepository.findByRunIdOrderByBucketSeqAscIdAsc(ladderRun.getId()).stream()
                .filter(l -> l.getSource() != LadderSourceCode.TOTAL).map(l -> new LadderLine(l.getBucketSeq(), l.getBucketCode(),
                        l.getSource(), l.getLineCode(), l.getProductId(), l.getProductName(), l.getContractual(), l.getBehavioural()))
                .toList();
        final List<TreasuryRunStressCapacity> items = new ArrayList<>();
        final Map<Long, Integer> monetised = new HashMap<>();
        BigDecimal liquid = BigDecimal.ZERO;
        BigDecimal baselineLiquid = BigDecimal.ZERO;
        final List<TreasuryDealType> dealTypes = this.dealTypeRepository.findAll();
        for (final TreasuryAssetClassRule rule : this.ruleSetService.assetClasses(ladderRun.getRuleSetId())) {
            final List<String> kinds = TreasuryRuleSetService.split(rule.getInstrumentKinds());
            final List<String> typeIds = TreasuryRuleSetService.split(rule.getDealTypeIds());
            if (!rule.isLiquid() || kinds.isEmpty() && typeIds.isEmpty()) {
                continue;
            }
            final TreasuryRatioInputs.AssetValue value = this.inputs.asset(instrumentsOnly(rule), run.getAsOfDate(), run.getCurrencyCode(),
                    run.getOfficeId(), scale);
            final BigDecimal shockHaircut = shocks.stream()
                    .filter(s -> s.type() == ShockType.HAIRCUT_LIQUID_ASSET && rule.getCode().equals(s.assetClass())).map(Shock::factor)
                    .reduce(BigDecimal.ZERO, BigDecimal::add).min(BigDecimal.valueOf(100));
            final BigDecimal excluded = value.excludedMaturity().add(value.excludedPledged());
            final BigDecimal eligible = StressCalculator.eligible(value.source(), excluded, rule.getHaircut(), shockHaircut, scale);
            liquid = liquid.add(eligible);
            baselineLiquid = baselineLiquid
                    .add(StressCalculator.eligible(value.source(), excluded, rule.getHaircut(), BigDecimal.ZERO, scale));
            items.add(capacity(run, "ASSET_CLASS", rule.getCode(), rule.getLabel(), value.source(), excluded, rule.getHaircut(),
                    shockHaircut, eligible, null, false, "Beyond the cut-off " + value.excludedMaturity().toPlainString() + ", pledged "
                            + value.excludedPledged().toPlainString()));
            for (final TreasuryDealType type : dealTypes) {
                if (kinds.contains(type.getInstrumentKind().name()) || typeIds.contains(String.valueOf(type.getId()))) {
                    final int cutoff = rule.getMaxMaturityDays() == null ? Integer.MAX_VALUE : rule.getMaxMaturityDays();
                    monetised.merge(type.getId(), cutoff, Math::max);
                }
            }
        }
        final List<FundingAccess> funding = new ArrayList<>();
        final List<FundingAccess> baselineFunding = new ArrayList<>();
        if (run.getOfficeId() == null) {
            for (final TreasuryFundingLine line : this.fundingLineRepository.findAllByOrderByCodeAsc()) {
                if (!line.isIncludeInCapacity()
                        || !TreasuryFundingLineService.availableOn(line, run.getCurrencyCode(), run.getAsOfDate())) {
                    continue;
                }
                final BigDecimal available = TreasuryFundingLineService.available(line);
                final boolean frozen = shocks.stream().anyMatch(s -> s.type() == ShockType.FREEZE_LINE
                        && (Objects.equals(s.fundingLineId(), line.getId()) || s.fundingSourceType() == line.getSourceType()));
                baselineFunding.add(new FundingAccess(available, line.getDaysToAccess()));
                if (!frozen) {
                    funding.add(new FundingAccess(available, line.getDaysToAccess()));
                }
                items.add(capacity(run, "FUNDING_LINE", line.getCode(), line.getName(), available, BigDecimal.ZERO, BigDecimal.ZERO,
                        BigDecimal.ZERO, frozen ? BigDecimal.ZERO : available, line.getDaysToAccess(), frozen,
                        line.getSourceType().name() + (frozen ? ", frozen by the scenario" : "")));
            }
        }
        final Predicate<LadderLine> isMonetised = line -> line.source() == LadderSourceCode.DEALS && line.productId() != null
                && monetised.containsKey(line.productId()) && bySeq.get(line.bucketSeq()).fromDay() <= monetised.get(line.productId());
        final List<StressLine> stressed = StressCalculator.shock(buckets, lines, shocks, isMonetised, scale);
        final List<BucketResult> results = StressCalculator.buckets(buckets, stressed, liquid, funding, baselineLiquid, baselineFunding);
        final Survival survival = StressCalculator.survival(results, true);
        final Survival baseline = StressCalculator.survival(results, false);
        for (final StressLine line : stressed) {
            final TreasuryRunStressLine row = new TreasuryRunStressLine();
            row.setRunId(run.getId());
            row.setBucketSeq(line.getBucketSeq());
            row.setBucketCode(line.getBucketCode());
            row.setSource(line.getSource());
            row.setLineCode(line.getLineCode());
            row.setProductId(line.getProductId());
            row.setProductName(line.getProductName());
            row.setBaselineAmount(line.getBaseline());
            row.setStressedAmount(line.getStressed());
            row.setShocks(line.isShocked() ? cut(line.shockLabels(), 1000) : null);
            this.lineRepository.save(row);
        }
        for (final BucketResult r : results) {
            final TreasuryRunStressBucket row = new TreasuryRunStressBucket();
            row.setRunId(run.getId());
            row.setBucketSeq(r.bucket().seq());
            row.setBucketCode(r.bucket().code());
            row.setFromDay(r.bucket().fromDay());
            row.setToDay(r.bucket().toDay());
            row.setBaselineNet(r.baselineNet());
            row.setBaselineCumulative(r.baselineCumulative());
            row.setStressedNet(r.stressedNet());
            row.setStressedCumulative(r.stressedCumulative());
            row.setLiquidCapacity(r.liquidCapacity());
            row.setFundingCapacity(r.fundingCapacity());
            row.setBaselineCapacity(r.baselineCapacity());
            row.setPosition(r.position());
            this.bucketRepository.save(row);
        }
        items.forEach(this.capacityRepository::save);
        this.lineRepository.flush();
        this.bucketRepository.flush();
        this.capacityRepository.flush();
        run.setScenarioId(scenario.getId());
        run.setScenarioCode(scenario.getCode());
        run.setScenarioVersion(scenario.getVersion());
        run.setSurvivalDays(survival.days());
        run.setSurvivalBeyondHorizon(survival.beyondHorizon());
        run.setBaselineSurvivalDays(baseline.days());
        run.setConfigSnapshot(snapshot(scenario, shocks));
        return survival;
    }

    public void deleteRun(final Long runId) {
        this.lineRepository.deleteByRunId(runId);
        this.bucketRepository.deleteByRunId(runId);
        this.capacityRepository.deleteByRunId(runId);
        this.lineRepository.flush();
        this.bucketRepository.flush();
        this.capacityRepository.flush();
    }

    public TreasuryStressData data(final TreasuryRun run) {
        return new TreasuryStressData(run.getId(), run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(), run.getSourceRunId(),
                run.getScenarioId(), run.getScenarioCode(), run.getScenarioVersion(), run.getSurvivalDays(), run.isSurvivalBeyondHorizon(),
                run.getBaselineSurvivalDays(),
                this.bucketRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.getId()).stream()
                        .map(b -> new TreasuryStressData.Bucket(b.getBucketSeq(), b.getBucketCode(), b.getFromDay(), b.getToDay(),
                                b.getBaselineNet(), b.getBaselineCumulative(), b.getStressedNet(), b.getStressedCumulative(),
                                b.getLiquidCapacity(), b.getFundingCapacity(), b.getBaselineCapacity(), b.getPosition()))
                        .toList(),
                this.capacityRepository.findByRunIdOrderByIdAsc(run.getId()).stream()
                        .map(c -> new TreasuryStressData.Capacity(c.getKind(), c.getReference(), c.getLabel(), c.getSourceAmount(),
                                c.getExcludedAmount(), c.getHaircut(), c.getShockHaircut(), c.getEligibleAmount(), c.getDaysToAccess(),
                                c.isFrozen(), c.getNote()))
                        .toList(),
                this.lineRepository.findByRunIdOrderByBucketSeqAscIdAsc(run.getId()).stream()
                        .map(l -> new TreasuryStressData.Line(l.getBucketSeq(), l.getBucketCode(), l.getSource().name(), l.getLineCode(),
                                l.getProductId(), l.getProductName(), l.getBaselineAmount(), l.getStressedAmount(), l.getShocks()))
                        .toList());
    }

    /** The class without its treasury account types: those balances are the ladder's opening stock. */
    private static TreasuryAssetClassRule instrumentsOnly(final TreasuryAssetClassRule rule) {
        final TreasuryAssetClassRule copy = new TreasuryAssetClassRule();
        copy.setRuleSetId(rule.getRuleSetId());
        copy.setCode(rule.getCode());
        copy.setLabel(rule.getLabel());
        copy.setLiquid(rule.isLiquid());
        copy.setInstrumentKinds(rule.getInstrumentKinds());
        copy.setDealTypeIds(rule.getDealTypeIds());
        copy.setMaxMaturityDays(rule.getMaxMaturityDays());
        copy.setHaircut(rule.getHaircut());
        return copy;
    }

    private static TreasuryRunStressCapacity capacity(final TreasuryRun run, final String kind, final String reference, final String label,
            final BigDecimal source, final BigDecimal excluded, final BigDecimal haircut, final BigDecimal shockHaircut,
            final BigDecimal eligible, final Integer days, final boolean frozen, final String note) {
        final TreasuryRunStressCapacity item = new TreasuryRunStressCapacity();
        item.setRunId(run.getId());
        item.setKind(kind);
        item.setReference(cut(reference, 50));
        item.setLabel(cut(label, 150));
        item.setSourceAmount(source);
        item.setExcludedAmount(excluded);
        item.setHaircut(haircut);
        item.setShockHaircut(shockHaircut);
        item.setEligibleAmount(eligible);
        item.setDaysToAccess(days);
        item.setFrozen(frozen);
        item.setNote(cut(note, 500));
        return item;
    }

    private static String snapshot(final TreasuryScenario scenario, final List<Shock> shocks) {
        final JsonObject json = new JsonObject();
        json.addProperty("scenario", scenario.getCode());
        json.addProperty("version", scenario.getVersion());
        json.addProperty("type", scenario.getScenarioType().name());
        json.addProperty("severity", scenario.getSeverity());
        final JsonArray array = new JsonArray();
        for (final Shock shock : shocks) {
            final JsonObject s = new JsonObject();
            s.addProperty("type", shock.type().name());
            s.addProperty("label", shock.label());
            s.addProperty("source", shock.source() == null ? null : shock.source().name());
            s.addProperty("lineCode", shock.lineCode());
            s.addProperty("productId", shock.productId());
            s.addProperty("bucketCode", shock.bucketCode());
            s.addProperty("side", shock.side() == null ? null : shock.side().name());
            s.addProperty("factor", shock.factor() == null ? null : shock.factor().toPlainString());
            s.addProperty("assetClass", shock.assetClass());
            s.addProperty("fundingSourceType", shock.fundingSourceType() == null ? null : shock.fundingSourceType().name());
            s.addProperty("fundingLineId", shock.fundingLineId());
            array.add(s);
        }
        json.add("shocks", array);
        return json.toString();
    }

    private static String cut(final String text, final int length) {
        return text == null || text.length() <= length ? text : text.substring(0, length);
    }
}
