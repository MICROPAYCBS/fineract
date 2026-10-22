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
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSET_CLASSES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.BUCKETS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.IS_DEFAULT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.RATIOS_PARAM;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryRuleSetData;
import org.apache.fineract.treasury.domain.TreasuryAssetClassRule;
import org.apache.fineract.treasury.domain.TreasuryAssetClassRuleRepository;
import org.apache.fineract.treasury.domain.TreasuryBucket;
import org.apache.fineract.treasury.domain.TreasuryBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRatioComponent;
import org.apache.fineract.treasury.domain.TreasuryRatioComponentRepository;
import org.apache.fineract.treasury.domain.TreasuryRatioDefinition;
import org.apache.fineract.treasury.domain.TreasuryRatioDefinitionRepository;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRuleSetRepository;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.ComponentType;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.liquidity.RatioPart;
import org.apache.fineract.treasury.serialization.RatioConfigParser;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RuleSetRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule sets with their ladder buckets, ratios (with their components) and asset classes, versioned by code. A new
 * version is a DRAFT, created empty or as a copy of another version ({@code copyFromId}), edited and deleted freely;
 * activating it (by someone other than who created or last edited it) supersedes the code's active version, which
 * passes on the default flag. Active and superseded versions cannot change, except that an active one can be made the
 * default.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryRuleSetService {

    static final String ENTITY = "rule.set";

    private final TreasuryLiquidityDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryRuleSetRepository ruleSetRepository;
    private final TreasuryBucketRepository bucketRepository;
    private final TreasuryRatioDefinitionRepository ratioRepository;
    private final TreasuryRatioComponentRepository componentRepository;
    private final TreasuryAssetClassRuleRepository assetClassRepository;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final RuleSetRequest request = this.validator.validateRuleSet(command.json(), true);
        final List<TreasuryRuleSet> versions = this.ruleSetRepository.findByCodeOrderByVersionAsc(request.code());
        if (versions.stream().anyMatch(v -> v.getStatus() == ConfigStatus.DRAFT)) {
            throw new TreasuryWorkflowException(ENTITY, "draft.exists", "Rule set " + request.code() + " already has a draft version",
                    request.code());
        }
        final TreasuryRuleSet source = request.copyFromId() == null ? null : find(request.copyFromId());
        final Long userId = this.actor.userId(command);
        final TreasuryRuleSet set = new TreasuryRuleSet();
        set.setCode(request.code());
        set.setName(request.name());
        set.setVersion(versions.isEmpty() ? 1 : versions.getLast().getVersion() + 1);
        set.setEffectiveDate(request.effectiveDate());
        set.setDescription(request.description() != null || source == null ? request.description() : source.getDescription());
        set.setStatus(ConfigStatus.DRAFT);
        set.setCreatedBy(userId);
        set.setLastEditedBy(userId);
        set.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.ruleSetRepository.saveAndFlush(set);
        saveBuckets(set, request.buckets() != null ? request.buckets() : buckets(source.getId()));
        if (request.ratios() != null) {
            saveRatios(set, request.ratios());
        } else if (source != null) {
            copyRatios(source, set);
        }
        if (request.assetClasses() != null) {
            saveAssetClasses(set, request.assetClasses());
        } else if (source != null) {
            copyAssetClasses(source, set);
        }
        checkReferences(set.getId());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("version", set.getVersion());
        changes.put("status", set.getStatus().name());
        if (source != null) {
            changes.put("copiedFrom", source.getCode() + " v" + source.getVersion());
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(set.getId()).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long ruleSetId, final JsonCommand command) {
        final RuleSetRequest request = this.validator.validateRuleSet(command.json(), false);
        final TreasuryRuleSet set = find(ruleSetId);
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (set.getStatus() != ConfigStatus.DRAFT) {
            if (request.parametersPassed().equals(Set.of(IS_DEFAULT_PARAM)) && set.getStatus() == ConfigStatus.ACTIVE
                    && Boolean.TRUE.equals(request.isDefault())) {
                makeDefault(set);
                changes.put(IS_DEFAULT_PARAM, true);
                return result(command, set, changes);
            }
            throw new TreasuryWorkflowException(ENTITY, "not.draft", "Rule set " + set.getCode() + " version " + set.getVersion() + " is "
                    + set.getStatus() + " and cannot change; create a new version (only isDefault: true may be set on an active one)",
                    ruleSetId);
        }
        if (request.has(IS_DEFAULT_PARAM)) {
            throw new TreasuryWorkflowException(ENTITY, "default.not.draft", "Only an active rule set can be the default", ruleSetId);
        }
        if (request.has(NAME_PARAM)) {
            set.setName(request.name());
            changes.put(NAME_PARAM, request.name());
        }
        if (request.has(EFFECTIVE_DATE_PARAM) && request.effectiveDate() != null) {
            set.setEffectiveDate(request.effectiveDate());
            changes.put(EFFECTIVE_DATE_PARAM, request.effectiveDate());
        }
        if (request.has(DESCRIPTION_PARAM)) {
            set.setDescription(request.description());
            changes.put(DESCRIPTION_PARAM, request.description());
        }
        if (request.buckets() != null) {
            this.bucketRepository.deleteByRuleSetId(ruleSetId);
            this.bucketRepository.flush();
            saveBuckets(set, request.buckets());
            changes.put(BUCKETS_PARAM, request.buckets().size());
        }
        if (request.ratios() != null) {
            deleteRatios(ruleSetId);
            saveRatios(set, request.ratios());
            changes.put(RATIOS_PARAM, request.ratios().size());
        }
        if (request.assetClasses() != null) {
            this.assetClassRepository.deleteByRuleSetId(ruleSetId);
            this.assetClassRepository.flush();
            saveAssetClasses(set, request.assetClasses());
            changes.put(ASSET_CLASSES_PARAM, request.assetClasses().size());
        }
        checkReferences(ruleSetId);
        set.setLastEditedBy(this.actor.userId(command));
        this.ruleSetRepository.saveAndFlush(set);
        return result(command, set, changes);
    }

    @Transactional
    public CommandProcessingResult delete(final Long ruleSetId, final JsonCommand command) {
        final TreasuryRuleSet set = find(ruleSetId);
        if (set.getStatus() != ConfigStatus.DRAFT) {
            throw new TreasuryWorkflowException(ENTITY, "not.draft",
                    "Only a draft rule set can be deleted; version " + set.getVersion() + " of " + set.getCode() + " is " + set.getStatus(),
                    ruleSetId);
        }
        // children first: their foreign keys hold the rule set
        deleteRatios(ruleSetId);
        this.assetClassRepository.deleteByRuleSetId(ruleSetId);
        this.assetClassRepository.flush();
        this.bucketRepository.deleteByRuleSetId(ruleSetId);
        this.bucketRepository.flush();
        this.ruleSetRepository.delete(set);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(ruleSetId).build();
    }

    @Transactional
    public CommandProcessingResult activate(final Long ruleSetId, final JsonCommand command) {
        this.validator.validateActivate(command.json());
        final TreasuryRuleSet set = find(ruleSetId);
        if (set.getStatus() != ConfigStatus.DRAFT) {
            throw new TreasuryWorkflowException(ENTITY, "not.draft",
                    "Rule set " + set.getCode() + " version " + set.getVersion() + " is " + set.getStatus(), ruleSetId);
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, ruleSetId, "created", set.getCreatedBy(), "activate", userId);
        TreasurySegregation.requireDifferentUser(ENTITY, ruleSetId, "last edited", set.getLastEditedBy(), "activate", userId);
        if (DateUtils.isAfterBusinessDate(set.getEffectiveDate())) {
            throw new TreasuryWorkflowException(ENTITY, "effective.date.in.future",
                    "Rule set " + set.getCode() + " is effective from " + set.getEffectiveDate() + ", after the business date", ruleSetId);
        }
        if (this.bucketRepository.findByRuleSetIdOrderBySequenceNumberAsc(ruleSetId).isEmpty()) {
            throw new TreasuryWorkflowException(ENTITY, "no.buckets", "Rule set " + set.getCode() + " has no buckets", ruleSetId);
        }
        checkReferences(ruleSetId);
        boolean wasDefault = false;
        for (final TreasuryRuleSet active : this.ruleSetRepository.findByCodeAndStatus(set.getCode(), ConfigStatus.ACTIVE)) {
            wasDefault |= active.isDefaultSet();
            active.setStatus(ConfigStatus.SUPERSEDED);
            active.setDefaultSet(false);
            this.ruleSetRepository.saveAndFlush(active);
        }
        set.setStatus(ConfigStatus.ACTIVE);
        set.setDefaultSet(wasDefault);
        set.setActivatedBy(userId);
        set.setActivatedOnDate(DateUtils.getBusinessLocalDate());
        this.ruleSetRepository.saveAndFlush(set);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", set.getStatus().name());
        changes.put(IS_DEFAULT_PARAM, set.isDefaultSet());
        return result(command, set, changes);
    }

    @Transactional(readOnly = true)
    public List<TreasuryRuleSetData> retrieveAll() {
        return this.ruleSetRepository.findAllByOrderByCodeAscVersionAsc().stream().map(this::data).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryRuleSetData retrieveOne(final Long ruleSetId) {
        return data(find(ruleSetId));
    }

    /** The default rule set, which must be active. */
    public TreasuryRuleSet defaultRuleSet() {
        return this.ruleSetRepository.findFirstByDefaultSetTrueAndStatus(ConfigStatus.ACTIVE).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "no.default", "No active rule set is the default; set isDefault on one"));
    }

    public List<Bucket> buckets(final Long ruleSetId) {
        return this.bucketRepository.findByRuleSetIdOrderBySequenceNumberAsc(ruleSetId).stream()
                .map(b -> new Bucket(b.getSequenceNumber(), b.getCode(), b.getLabel(), b.getFromDay(), b.getToDay())).toList();
    }

    public List<TreasuryRatioDefinition> ratios(final Long ruleSetId) {
        return this.ratioRepository.findByRuleSetIdOrderBySequenceNumberAsc(ruleSetId);
    }

    public List<TreasuryRatioComponent> components(final Long ratioId) {
        return this.componentRepository.findByRatioIdOrderByPartAscSequenceNumberAsc(ratioId);
    }

    public List<TreasuryAssetClassRule> assetClasses(final Long ruleSetId) {
        return this.assetClassRepository.findByRuleSetIdOrderByIdAsc(ruleSetId);
    }

    TreasuryRuleSet find(final Long ruleSetId) {
        return this.ruleSetRepository.findById(ruleSetId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury rule set " + ruleSetId + " does not exist", ruleSetId));
    }

    /** Every asset class a ratio component names must be one of the rule set's. */
    private void checkReferences(final Long ruleSetId) {
        final Set<String> classes = assetClasses(ruleSetId).stream().map(TreasuryAssetClassRule::getCode).collect(Collectors.toSet());
        for (final TreasuryRatioDefinition ratio : ratios(ruleSetId)) {
            final List<TreasuryRatioComponent> components = components(ratio.getId());
            for (final TreasuryRatioComponent component : components) {
                if (component.getComponentType() == ComponentType.ASSET_CLASS && !classes.contains(component.getAssetClass())) {
                    throw new TreasuryWorkflowException(ENTITY, "asset.class.unknown", "Ratio " + ratio.getCode() + " uses asset class "
                            + component.getAssetClass() + ", which the rule set does not define", component.getAssetClass());
                }
            }
            if (components.stream().noneMatch(c -> c.getPart() == RatioPart.NUMERATOR)
                    || components.stream().noneMatch(c -> c.getPart() == RatioPart.DENOMINATOR)) {
                throw new TreasuryWorkflowException(ENTITY, "ratio.incomplete",
                        "Ratio " + ratio.getCode() + " needs at least one numerator and one denominator component", ratio.getCode());
            }
        }
    }

    private TreasuryRuleSetData data(final TreasuryRuleSet set) {
        final List<TreasuryRuleSetData.RatioData> ratios = ratios(set.getId()).stream().map(r -> new TreasuryRuleSetData.RatioData(
                r.getSequenceNumber(), r.getCode(), r.getName(), r.getDirection().name(), r.getLimitValue(), r.getWarningValue(),
                r.isInReturn(), r.getNumeratorLabel(), r.getDenominatorLabel(), r.getDescription(),
                components(r.getId()).stream()
                        .map(c -> new TreasuryRuleSetData.ComponentData(c.getPart().name(), c.getSequenceNumber(),
                                c.getComponentType().name(), c.getLabel(), c.getGlAccountId(), c.getGlCodePrefix(), c.getGlCodeFrom(),
                                c.getGlCodeTo(), c.getAssetClass(), c.getLadderMetric() == null ? null : c.getLadderMetric().name(),
                                c.getMetricDays(), c.getSign(), c.getWeight()))
                        .toList()))
                .toList();
        final List<TreasuryRuleSetData.AssetClassData> classes = assetClasses(set.getId()).stream()
                .map(a -> new TreasuryRuleSetData.AssetClassData(a.getCode(), a.getLabel(), a.isLiquid(), split(a.getAccountTypes()),
                        split(a.getInstrumentKinds()), split(a.getDealTypeIds()).stream().map(Long::valueOf).toList(),
                        a.getMaxMaturityDays(), a.getHaircut()))
                .toList();
        return new TreasuryRuleSetData(set.getId(), set.getCode(), set.getName(), set.getVersion(), set.getEffectiveDate(),
                set.getStatus().name(), set.isDefaultSet(), set.getDescription(), set.getCreatedBy(), set.getLastEditedBy(),
                set.getActivatedBy(), set.getActivatedOnDate(),
                buckets(set.getId()).stream()
                        .map(b -> new TreasuryRuleSetData.BucketData(b.seq(), b.code(), b.label(), b.fromDay(), b.toDay())).toList(),
                ratios, classes);
    }

    static List<String> split(final String list) {
        return StringUtils.isBlank(list) ? List.of() : Arrays.stream(list.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void makeDefault(final TreasuryRuleSet set) {
        for (final TreasuryRuleSet other : this.ruleSetRepository.findByDefaultSetTrue()) {
            other.setDefaultSet(false);
            this.ruleSetRepository.saveAndFlush(other);
        }
        set.setDefaultSet(true);
        this.ruleSetRepository.saveAndFlush(set);
    }

    private void saveBuckets(final TreasuryRuleSet set, final List<Bucket> buckets) {
        for (final Bucket bucket : buckets) {
            final TreasuryBucket row = new TreasuryBucket();
            row.setRuleSetId(set.getId());
            row.setSequenceNumber(bucket.seq());
            row.setCode(bucket.code());
            row.setLabel(bucket.label());
            row.setFromDay(bucket.fromDay());
            row.setToDay(bucket.toDay());
            this.bucketRepository.save(row);
        }
        this.bucketRepository.flush();
    }

    private void deleteRatios(final Long ruleSetId) {
        for (final TreasuryRatioDefinition ratio : ratios(ruleSetId)) {
            this.componentRepository.deleteByRatioId(ratio.getId());
        }
        this.componentRepository.flush();
        this.ratioRepository.deleteByRuleSetId(ruleSetId);
        this.ratioRepository.flush();
    }

    private void saveRatios(final TreasuryRuleSet set, final List<RatioConfigParser.RatioSpec> specs) {
        int seq = 0;
        for (final var spec : specs) {
            final TreasuryRatioDefinition ratio = new TreasuryRatioDefinition();
            ratio.setRuleSetId(set.getId());
            ratio.setSequenceNumber(++seq);
            ratio.setCode(spec.code());
            ratio.setName(spec.name());
            ratio.setDirection(spec.direction());
            ratio.setLimitValue(spec.limit());
            ratio.setWarningValue(spec.warning());
            ratio.setInReturn(spec.inReturn());
            ratio.setNumeratorLabel(spec.numeratorLabel());
            ratio.setDenominatorLabel(spec.denominatorLabel());
            ratio.setDescription(spec.description());
            this.ratioRepository.saveAndFlush(ratio);
            int n = 0;
            for (final var c : spec.components()) {
                final TreasuryRatioComponent component = new TreasuryRatioComponent();
                component.setRatioId(ratio.getId());
                component.setPart(c.part());
                component.setSequenceNumber(++n);
                component.setComponentType(c.type());
                component.setLabel(c.label());
                component.setGlAccountId(c.glAccountId());
                component.setGlCodePrefix(c.glCodePrefix());
                component.setGlCodeFrom(c.glCodeFrom());
                component.setGlCodeTo(c.glCodeTo());
                component.setAssetClass(c.assetClass());
                component.setLadderMetric(c.ladderMetric());
                component.setMetricDays(c.metricDays());
                component.setSign(c.sign());
                component.setWeight(c.weight());
                this.componentRepository.save(component);
            }
        }
        this.componentRepository.flush();
    }

    private void copyRatios(final TreasuryRuleSet source, final TreasuryRuleSet target) {
        for (final TreasuryRatioDefinition from : ratios(source.getId())) {
            final TreasuryRatioDefinition ratio = new TreasuryRatioDefinition();
            ratio.setRuleSetId(target.getId());
            ratio.setSequenceNumber(from.getSequenceNumber());
            ratio.setCode(from.getCode());
            ratio.setName(from.getName());
            ratio.setDirection(from.getDirection());
            ratio.setLimitValue(from.getLimitValue());
            ratio.setWarningValue(from.getWarningValue());
            ratio.setInReturn(from.isInReturn());
            ratio.setNumeratorLabel(from.getNumeratorLabel());
            ratio.setDenominatorLabel(from.getDenominatorLabel());
            ratio.setDescription(from.getDescription());
            this.ratioRepository.saveAndFlush(ratio);
            for (final TreasuryRatioComponent c : components(from.getId())) {
                final TreasuryRatioComponent component = new TreasuryRatioComponent();
                component.setRatioId(ratio.getId());
                component.setPart(c.getPart());
                component.setSequenceNumber(c.getSequenceNumber());
                component.setComponentType(c.getComponentType());
                component.setLabel(c.getLabel());
                component.setGlAccountId(c.getGlAccountId());
                component.setGlCodePrefix(c.getGlCodePrefix());
                component.setGlCodeFrom(c.getGlCodeFrom());
                component.setGlCodeTo(c.getGlCodeTo());
                component.setAssetClass(c.getAssetClass());
                component.setLadderMetric(c.getLadderMetric());
                component.setMetricDays(c.getMetricDays());
                component.setSign(c.getSign());
                component.setWeight(c.getWeight());
                this.componentRepository.save(component);
            }
        }
        this.componentRepository.flush();
    }

    private void saveAssetClasses(final TreasuryRuleSet set, final List<RatioConfigParser.AssetClassSpec> specs) {
        for (final var spec : specs) {
            final TreasuryAssetClassRule rule = new TreasuryAssetClassRule();
            rule.setRuleSetId(set.getId());
            rule.setCode(spec.code());
            rule.setLabel(spec.label());
            rule.setLiquid(spec.liquid());
            rule.setAccountTypes(spec.accountTypes().isEmpty() ? null : String.join(",", spec.accountTypes()));
            rule.setInstrumentKinds(spec.instrumentKinds().isEmpty() ? null : String.join(",", spec.instrumentKinds()));
            rule.setDealTypeIds(spec.dealTypeIds().isEmpty() ? null
                    : spec.dealTypeIds().stream().map(String::valueOf).collect(Collectors.joining(",")));
            rule.setMaxMaturityDays(spec.maxMaturityDays());
            rule.setHaircut(spec.haircut());
            this.assetClassRepository.save(rule);
        }
        this.assetClassRepository.flush();
    }

    private void copyAssetClasses(final TreasuryRuleSet source, final TreasuryRuleSet target) {
        for (final TreasuryAssetClassRule from : assetClasses(source.getId())) {
            final TreasuryAssetClassRule rule = new TreasuryAssetClassRule();
            rule.setRuleSetId(target.getId());
            rule.setCode(from.getCode());
            rule.setLabel(from.getLabel());
            rule.setLiquid(from.isLiquid());
            rule.setAccountTypes(from.getAccountTypes());
            rule.setInstrumentKinds(from.getInstrumentKinds());
            rule.setDealTypeIds(from.getDealTypeIds());
            rule.setMaxMaturityDays(from.getMaxMaturityDays());
            rule.setHaircut(from.getHaircut());
            this.assetClassRepository.save(rule);
        }
        this.assetClassRepository.flush();
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryRuleSet set, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(set.getId()).with(changes).build();
    }
}
