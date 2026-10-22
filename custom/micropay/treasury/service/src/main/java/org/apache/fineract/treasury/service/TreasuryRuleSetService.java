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
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.BUCKETS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.IS_DEFAULT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.NAME_PARAM;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryRuleSetData;
import org.apache.fineract.treasury.domain.TreasuryBucket;
import org.apache.fineract.treasury.domain.TreasuryBucketRepository;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRuleSetRepository;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RuleSetRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rule sets and their ladder buckets, versioned by code. A new version is a DRAFT, edited and deleted freely;
 * activating it (by someone other than who created or last edited it) supersedes the code's active version, which takes
 * the default flag with it. Active and superseded versions cannot change, except that an active one can be made the
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

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final RuleSetRequest request = this.validator.validateRuleSet(command.json(), true);
        final List<TreasuryRuleSet> versions = this.ruleSetRepository.findByCodeOrderByVersionAsc(request.code());
        if (versions.stream().anyMatch(v -> v.getStatus() == ConfigStatus.DRAFT)) {
            throw new TreasuryWorkflowException(ENTITY, "draft.exists", "Rule set " + request.code() + " already has a draft version",
                    request.code());
        }
        final Long userId = this.actor.userId(command);
        final TreasuryRuleSet set = new TreasuryRuleSet();
        set.setCode(request.code());
        set.setName(request.name());
        set.setVersion(versions.isEmpty() ? 1 : versions.getLast().getVersion() + 1);
        set.setEffectiveDate(request.effectiveDate());
        set.setDescription(request.description());
        set.setStatus(ConfigStatus.DRAFT);
        set.setCreatedBy(userId);
        set.setLastEditedBy(userId);
        set.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.ruleSetRepository.saveAndFlush(set);
        saveBuckets(set, request.buckets());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("version", set.getVersion());
        changes.put("status", set.getStatus().name());
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
        this.bucketRepository.deleteByRuleSetId(ruleSetId);
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
        return this.ruleSetRepository.findAllByOrderByCodeAscVersionAsc().stream()
                .map(s -> TreasuryRuleSetData.from(s, this.bucketRepository.findByRuleSetIdOrderBySequenceNumberAsc(s.getId()))).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryRuleSetData retrieveOne(final Long ruleSetId) {
        return TreasuryRuleSetData.from(find(ruleSetId), this.bucketRepository.findByRuleSetIdOrderBySequenceNumberAsc(ruleSetId));
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

    TreasuryRuleSet find(final Long ruleSetId) {
        return this.ruleSetRepository.findById(ruleSetId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury rule set " + ruleSetId + " does not exist", ruleSetId));
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

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryRuleSet set, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(set.getId()).with(changes).build();
    }
}
