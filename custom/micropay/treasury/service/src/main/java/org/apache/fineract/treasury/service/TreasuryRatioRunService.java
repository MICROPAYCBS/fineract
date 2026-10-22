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
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.data.TreasuryReturnData;
import org.apache.fineract.treasury.data.TreasuryRunData;
import org.apache.fineract.treasury.domain.TreasuryAssetClassRule;
import org.apache.fineract.treasury.domain.TreasuryRatioComponent;
import org.apache.fineract.treasury.domain.TreasuryRatioDefinition;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.domain.TreasuryRunComponent;
import org.apache.fineract.treasury.domain.TreasuryRunComponentRepository;
import org.apache.fineract.treasury.domain.TreasuryRunRatio;
import org.apache.fineract.treasury.domain.TreasuryRunRatioRepository;
import org.apache.fineract.treasury.domain.TreasuryRunReturnLine;
import org.apache.fineract.treasury.domain.TreasuryRunReturnLineRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.ComponentType;
import org.apache.fineract.treasury.liquidity.RatioCalculator;
import org.apache.fineract.treasury.liquidity.RatioPart;
import org.apache.fineract.treasury.liquidity.ReturnBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Computes and stores the ratios of a RATIOS run, and the lines of a RETURN run. Everything a stored run shows is read
 * back from what was stored, never recomputed, so a run reproduces exactly after the configuration changes; the
 * configuration used is also kept on the run as a JSON snapshot.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryRatioRunService {

    private final TreasuryRuleSetService ruleSetService;
    private final TreasuryRatioInputs inputs;
    private final TreasuryRunRatioRepository ratioRepository;
    private final TreasuryRunComponentRepository componentRepository;
    private final TreasuryRunReturnLineRepository returnLineRepository;

    public boolean hasRatios(final Long ruleSetId) {
        return !this.ruleSetService.ratios(ruleSetId).isEmpty();
    }

    /** Computes the ratios of the run's rule set from the ladder run and the GL, and keeps the configuration used. */
    void computeRatios(final TreasuryRun run, final TreasuryRun ladderRun, final int scale) {
        final List<TreasuryRatioDefinition> ratios = this.ruleSetService.ratios(run.getRuleSetId());
        if (ratios.isEmpty()) {
            throw new TreasuryWorkflowException("run", "no.ratios", "Rule set " + run.getRuleSetId() + " defines no ratios",
                    run.getRuleSetId());
        }
        final Map<String, TreasuryAssetClassRule> classes = new HashMap<>();
        this.ruleSetService.assetClasses(run.getRuleSetId()).forEach(c -> classes.put(c.getCode(), c));
        final List<Bucket> buckets = this.ruleSetService.buckets(ladderRun.getRuleSetId());
        final Map<String, TreasuryRatioInputs.AssetValue> values = new HashMap<>();
        final JsonArray ratioSnapshot = new JsonArray();
        for (final TreasuryRatioDefinition ratio : ratios) {
            BigDecimal numerator = BigDecimal.ZERO;
            BigDecimal denominator = BigDecimal.ZERO;
            final JsonArray componentSnapshot = new JsonArray();
            for (final TreasuryRatioComponent c : this.ruleSetService.components(ratio.getId())) {
                BigDecimal source;
                BigDecimal excludedMaturity = BigDecimal.ZERO;
                BigDecimal excludedPledged = BigDecimal.ZERO;
                BigDecimal haircut = BigDecimal.ZERO;
                if (c.getComponentType() == ComponentType.GL_ACCOUNT) {
                    source = this.inputs.gl(c, run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId());
                } else if (c.getComponentType() == ComponentType.ASSET_CLASS) {
                    final TreasuryAssetClassRule rule = classes.get(c.getAssetClass());
                    final TreasuryRatioInputs.AssetValue value = values.computeIfAbsent(c.getAssetClass(),
                            k -> this.inputs.asset(rule, run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(), scale));
                    source = value.source();
                    excludedMaturity = value.excludedMaturity();
                    excludedPledged = value.excludedPledged();
                    haircut = rule.getHaircut();
                } else {
                    source = this.inputs.metric(c, ladderRun.getId(), buckets);
                }
                final BigDecimal eligible = RatioCalculator.eligible(source, excludedMaturity.add(excludedPledged), haircut, c.getWeight(),
                        c.getSign(), scale);
                if (c.getPart() == RatioPart.NUMERATOR) {
                    numerator = numerator.add(eligible);
                } else {
                    denominator = denominator.add(eligible);
                }
                final TreasuryRunComponent row = new TreasuryRunComponent();
                row.setRunId(run.getId());
                row.setRatioCode(ratio.getCode());
                row.setPart(c.getPart());
                row.setSequenceNumber(c.getSequenceNumber());
                row.setComponentType(c.getComponentType());
                row.setLabel(c.getLabel());
                row.setReference(TreasuryRatioInputs.reference(c));
                row.setSourceAmount(source);
                row.setExcludedPledged(excludedPledged);
                row.setExcludedMaturity(excludedMaturity);
                row.setHaircut(haircut);
                row.setWeight(c.getWeight());
                row.setSign(c.getSign());
                row.setEligibleAmount(eligible);
                this.componentRepository.save(row);
                componentSnapshot.add(component(c, classes.get(c.getAssetClass())));
            }
            final BigDecimal result = RatioCalculator.ratio(numerator, denominator);
            final TreasuryRunRatio row = new TreasuryRunRatio();
            row.setRunId(run.getId());
            row.setSequenceNumber(ratio.getSequenceNumber());
            row.setCode(ratio.getCode());
            row.setName(ratio.getName());
            row.setDirection(ratio.getDirection());
            row.setNumerator(numerator);
            row.setDenominator(denominator);
            row.setResult(result);
            row.setLimitValue(ratio.getLimitValue());
            row.setWarningValue(ratio.getWarningValue());
            row.setStatus(RatioCalculator.status(ratio.getDirection(), result, ratio.getLimitValue(), ratio.getWarningValue()));
            row.setInReturn(ratio.isInReturn());
            row.setNumeratorLabel(ratio.getNumeratorLabel());
            row.setDenominatorLabel(ratio.getDenominatorLabel());
            this.ratioRepository.save(row);
            final JsonObject r = new JsonObject();
            r.addProperty("code", ratio.getCode());
            r.addProperty("name", ratio.getName());
            r.addProperty("direction", ratio.getDirection().name());
            r.addProperty("limit", ratio.getLimitValue().toPlainString());
            r.addProperty("warning", ratio.getWarningValue() == null ? null : ratio.getWarningValue().toPlainString());
            r.addProperty("inReturn", ratio.isInReturn());
            r.add("components", componentSnapshot);
            ratioSnapshot.add(r);
        }
        this.componentRepository.flush();
        this.ratioRepository.flush();
        final JsonObject snapshot = new JsonObject();
        snapshot.addProperty("ruleSetId", run.getRuleSetId());
        snapshot.addProperty("ruleSetVersion", run.getRuleSetVersion());
        snapshot.addProperty("ladderRunId", ladderRun.getId());
        snapshot.add("ratios", ratioSnapshot);
        run.setConfigSnapshot(snapshot.toString());
    }

    /** The return's lines from the stored ratios of the ratios run. */
    void buildReturn(final TreasuryRun run, final TreasuryRun ratiosRun) {
        final List<ReturnBuilder.Ratio> ratios = new ArrayList<>();
        final List<TreasuryRunComponent> components = this.componentRepository.findByRunIdOrderByIdAsc(ratiosRun.getId());
        for (final TreasuryRunRatio ratio : this.ratioRepository.findByRunIdOrderBySequenceNumberAsc(ratiosRun.getId())) {
            if (!ratio.isInReturn()) {
                continue;
            }
            final List<ReturnBuilder.Component> numerator = new ArrayList<>();
            final List<ReturnBuilder.Component> denominator = new ArrayList<>();
            for (final TreasuryRunComponent c : components) {
                if (c.getRatioCode().equals(ratio.getCode())) {
                    (c.getPart() == RatioPart.NUMERATOR ? numerator : denominator)
                            .add(new ReturnBuilder.Component(c.getLabel(), c.getEligibleAmount()));
                }
            }
            ratios.add(new ReturnBuilder.Ratio(ratio.getCode(), ratio.getName(), ratio.getNumeratorLabel(), ratio.getDenominatorLabel(),
                    ratio.getNumerator(), ratio.getDenominator(), ratio.getResult(), ratio.getLimitValue(), ratio.getStatus().name(),
                    numerator, denominator));
        }
        if (ratios.isEmpty()) {
            throw new TreasuryWorkflowException("run", "no.return.ratios",
                    "Ratios run " + ratiosRun.getId() + " has no ratio reported in the return", ratiosRun.getId());
        }
        for (final ReturnBuilder.Line line : ReturnBuilder.lines(ratios)) {
            final TreasuryRunReturnLine row = new TreasuryRunReturnLine();
            row.setRunId(run.getId());
            row.setSequenceNumber(line.seq());
            row.setSection(line.section());
            row.setLineCode(line.lineCode());
            row.setLabel(line.label());
            row.setAmount(line.amount());
            row.setValueText(line.text());
            this.returnLineRepository.save(row);
        }
        this.returnLineRepository.flush();
    }

    List<TreasuryRunData.Ratio> ratios(final Long runId) {
        final List<TreasuryRunComponent> components = this.componentRepository.findByRunIdOrderByIdAsc(runId);
        return this.ratioRepository.findByRunIdOrderBySequenceNumberAsc(runId).stream().map(r -> new TreasuryRunData.Ratio(
                r.getSequenceNumber(), r.getCode(), r.getName(), r.getDirection().name(), r.getNumerator(), r.getDenominator(),
                r.getResult(), r.getLimitValue(), r.getWarningValue(), r.getStatus().name(), r.isInReturn(),
                components.stream().filter(c -> c.getRatioCode().equals(r.getCode()))
                        .map(c -> new TreasuryRunData.Component(c.getPart().name(), c.getSequenceNumber(), c.getComponentType().name(),
                                c.getLabel(), c.getReference(), c.getSourceAmount(), c.getExcludedPledged(), c.getExcludedMaturity(),
                                c.getHaircut(), c.getWeight(), c.getSign(), c.getEligibleAmount()))
                        .toList()))
                .toList();
    }

    TreasuryReturnData returnData(final TreasuryRun run, final TreasuryRun ratiosRun) {
        final TreasuryRuleSet ruleSet = this.ruleSetService.find(run.getRuleSetId());
        return new TreasuryReturnData(run.getId(), ReturnBuilder.LAYOUT, true, run.getAsOfDate(), run.getCurrencyCode(), run.getOfficeId(),
                ruleSet.getCode(), run.getRuleSetVersion(), run.getAssumptionSetVersion(), run.getSourceRunId(),
                ratiosRun == null ? null : ratiosRun.getSourceRunId(), run.getStatus(), run.getSubmittedBy(), run.getSubmittedOnDate(),
                this.returnLineRepository.findByRunIdOrderBySequenceNumberAsc(run.getId()).stream()
                        .map(l -> new TreasuryReturnData.Line(l.getSequenceNumber(), l.getSection(), l.getLineCode(), l.getLabel(),
                                l.getAmount(), l.getValueText()))
                        .toList());
    }

    /**
     * The CSV of a stored return: fixed header fields (no status, which changes on submission) and the stored lines.
     */
    static String csv(final TreasuryReturnData data, final int scale) {
        final List<String[]> header = List.of(new String[] { "Return", "Treasury regulatory liquidity return" },
                new String[] { "Layout", data.layout() }, new String[] { "Run", String.valueOf(data.runId()) },
                new String[] { "As of", String.valueOf(data.asOfDate()) }, new String[] { "Currency", data.currencyCode() },
                new String[] { "Office", data.officeId() == null ? "Institution" : String.valueOf(data.officeId()) },
                new String[] { "Rule set", data.ruleSetCode() + " v" + data.ruleSetVersion() },
                new String[] { "Assumption set version",
                        data.assumptionSetVersion() == null ? "none" : String.valueOf(data.assumptionSetVersion()) },
                new String[] { "Ratios run", String.valueOf(data.ratiosRunId()) },
                new String[] { "Ladder run", String.valueOf(data.ladderRunId()) });
        return ReturnBuilder.csv(
                header, data.lines().stream()
                        .map(l -> new ReturnBuilder.Line(l.seq(), l.section(), l.lineCode(), l.label(), l.amount(), l.text())).toList(),
                scale);
    }

    void deleteRun(final Long runId) {
        this.componentRepository.deleteByRunId(runId);
        this.ratioRepository.deleteByRunId(runId);
        this.returnLineRepository.deleteByRunId(runId);
        this.componentRepository.flush();
        this.ratioRepository.flush();
        this.returnLineRepository.flush();
    }

    private static JsonObject component(final TreasuryRatioComponent c, final TreasuryAssetClassRule rule) {
        final JsonObject o = new JsonObject();
        o.addProperty("part", c.getPart().name());
        o.addProperty("seq", c.getSequenceNumber());
        o.addProperty("type", c.getComponentType().name());
        o.addProperty("label", c.getLabel());
        o.addProperty("reference", TreasuryRatioInputs.reference(c));
        o.addProperty("sign", c.getSign());
        o.addProperty("weight", c.getWeight().toPlainString());
        if (rule != null) {
            final JsonObject a = new JsonObject();
            a.addProperty("code", rule.getCode());
            a.addProperty("accountTypes", rule.getAccountTypes());
            a.addProperty("instrumentKinds", rule.getInstrumentKinds());
            a.addProperty("dealTypeIds", rule.getDealTypeIds());
            a.addProperty("maxMaturityDays", rule.getMaxMaturityDays());
            a.addProperty("haircut", rule.getHaircut().toPlainString());
            o.add("assetClass", a);
        }
        return o;
    }
}
