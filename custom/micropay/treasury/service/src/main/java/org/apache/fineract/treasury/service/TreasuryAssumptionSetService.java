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
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ASSUMPTIONS_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.NAME_PARAM;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryAssumptionSetData;
import org.apache.fineract.treasury.domain.TreasuryAssumption;
import org.apache.fineract.treasury.domain.TreasuryAssumptionRepository;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSet;
import org.apache.fineract.treasury.domain.TreasuryAssumptionSetRepository;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.AssumptionSetRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Behavioural assumption sets, versioned by code like rule sets: a DRAFT is edited or deleted; activating it (not by
 * who created or last edited it) supersedes the code's active version. Active and superseded versions are immutable. A
 * run without an assumption set given takes the most recently activated active set.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryAssumptionSetService {

    static final String ENTITY = "assumption.set";

    private final TreasuryLiquidityDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryAssumptionSetRepository setRepository;
    private final TreasuryAssumptionRepository assumptionRepository;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final AssumptionSetRequest request = this.validator.validateAssumptionSet(command.json(), true);
        final List<TreasuryAssumptionSet> versions = this.setRepository.findByCodeOrderByVersionAsc(request.code());
        if (versions.stream().anyMatch(v -> v.getStatus() == ConfigStatus.DRAFT)) {
            throw new TreasuryWorkflowException(ENTITY, "draft.exists", "Assumption set " + request.code() + " already has a draft version",
                    request.code());
        }
        final Long userId = this.actor.userId(command);
        final TreasuryAssumptionSet set = new TreasuryAssumptionSet();
        set.setCode(request.code());
        set.setName(request.name());
        set.setVersion(versions.isEmpty() ? 1 : versions.getLast().getVersion() + 1);
        set.setEffectiveDate(request.effectiveDate());
        set.setDescription(request.description());
        set.setStatus(ConfigStatus.DRAFT);
        set.setCreatedBy(userId);
        set.setLastEditedBy(userId);
        set.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.setRepository.saveAndFlush(set);
        saveRows(set, request.assumptions());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("version", set.getVersion());
        changes.put("status", set.getStatus().name());
        changes.put(ASSUMPTIONS_PARAM, request.assumptions().size());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(set.getId()).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long setId, final JsonCommand command) {
        final AssumptionSetRequest request = this.validator.validateAssumptionSet(command.json(), false);
        final TreasuryAssumptionSet set = draft(setId, "change");
        final Map<String, Object> changes = new LinkedHashMap<>();
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
        if (request.assumptions() != null) {
            this.assumptionRepository.deleteByAssumptionSetId(setId);
            this.assumptionRepository.flush();
            saveRows(set, request.assumptions());
            changes.put(ASSUMPTIONS_PARAM, request.assumptions().size());
        }
        set.setLastEditedBy(this.actor.userId(command));
        this.setRepository.saveAndFlush(set);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(setId).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult delete(final Long setId, final JsonCommand command) {
        final TreasuryAssumptionSet set = draft(setId, "be deleted");
        // the rows go first: their foreign key holds the set
        this.assumptionRepository.deleteByAssumptionSetId(setId);
        this.assumptionRepository.flush();
        this.setRepository.delete(set);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(setId).build();
    }

    @Transactional
    public CommandProcessingResult activate(final Long setId, final JsonCommand command) {
        this.validator.validateActivate(command.json());
        final TreasuryAssumptionSet set = draft(setId, "be activated");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, setId, "created", set.getCreatedBy(), "activate", userId);
        TreasurySegregation.requireDifferentUser(ENTITY, setId, "last edited", set.getLastEditedBy(), "activate", userId);
        if (DateUtils.isAfterBusinessDate(set.getEffectiveDate())) {
            throw new TreasuryWorkflowException(ENTITY, "effective.date.in.future",
                    "Assumption set " + set.getCode() + " is effective from " + set.getEffectiveDate() + ", after the business date",
                    setId);
        }
        Integer superseded = null;
        for (final TreasuryAssumptionSet active : this.setRepository.findByCodeAndStatus(set.getCode(), ConfigStatus.ACTIVE)) {
            active.setStatus(ConfigStatus.SUPERSEDED);
            superseded = active.getVersion();
            this.setRepository.saveAndFlush(active);
        }
        set.setStatus(ConfigStatus.ACTIVE);
        set.setActivatedBy(userId);
        set.setActivatedOnDate(DateUtils.getBusinessLocalDate());
        this.setRepository.saveAndFlush(set);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", set.getStatus().name());
        if (superseded != null) {
            changes.put("supersededVersion", superseded);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(setId).with(changes).build();
    }

    @Transactional(readOnly = true)
    public List<TreasuryAssumptionSetData> retrieveAll() {
        return this.setRepository.findAllByOrderByCodeAscVersionAsc().stream()
                .map(s -> TreasuryAssumptionSetData.from(s, this.assumptionRepository.findByAssumptionSetIdOrderByIdAsc(s.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public TreasuryAssumptionSetData retrieveOne(final Long setId) {
        return TreasuryAssumptionSetData.from(find(setId), this.assumptionRepository.findByAssumptionSetIdOrderByIdAsc(setId));
    }

    /** The most recently activated active set, if any. */
    public Optional<TreasuryAssumptionSet> activeSet() {
        return this.setRepository.findByStatusOrderByActivatedOnDateDescIdDesc(ConfigStatus.ACTIVE).stream().findFirst();
    }

    public Assumptions assumptions(final Long setId) {
        return new Assumptions(this.assumptionRepository.findByAssumptionSetIdOrderByIdAsc(setId).stream()
                .map(r -> new Assumptions.Row(r.getAssumptionType(), r.getSource(), r.getProductId(), r.getBucketCode(), r.getMinDays(),
                        r.getMaxDays(), r.getRate()))
                .toList());
    }

    TreasuryAssumptionSet find(final Long setId) {
        return this.setRepository.findById(setId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury assumption set " + setId + " does not exist", setId));
    }

    private TreasuryAssumptionSet draft(final Long setId, final String step) {
        final TreasuryAssumptionSet set = find(setId);
        if (set.getStatus() != ConfigStatus.DRAFT) {
            throw new TreasuryWorkflowException(ENTITY, "not.draft", "Assumption set " + set.getCode() + " version " + set.getVersion()
                    + " is " + set.getStatus() + " and cannot " + step + "; create a new version", setId);
        }
        return set;
    }

    private void saveRows(final TreasuryAssumptionSet set, final List<Assumptions.Row> rows) {
        for (final Assumptions.Row row : rows) {
            final TreasuryAssumption assumption = new TreasuryAssumption();
            assumption.setAssumptionSetId(set.getId());
            assumption.setAssumptionType(row.type());
            assumption.setSource(row.source());
            assumption.setProductId(row.productId());
            assumption.setBucketCode(row.bucketCode());
            assumption.setMinDays(row.minDays());
            assumption.setMaxDays(row.maxDays());
            assumption.setRate(row.rate());
            this.assumptionRepository.save(assumption);
        }
        this.assumptionRepository.flush();
    }
}
