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
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.DESCRIPTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.EFFECTIVE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SCENARIO_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryStressApiConstants.SEVERITY_PARAM;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryScenarioData;
import org.apache.fineract.treasury.domain.TreasuryScenario;
import org.apache.fineract.treasury.domain.TreasuryScenarioRepository;
import org.apache.fineract.treasury.domain.TreasuryScenarioShock;
import org.apache.fineract.treasury.domain.TreasuryScenarioShockRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.ScenarioRequest;
import org.apache.fineract.treasury.stress.Shock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stress scenarios, versioned like assumption sets: a code has at most one DRAFT; activating a draft supersedes the
 * code's ACTIVE version, and deactivating an active version retires it; only a draft is changed or deleted.
 * {@code copyFromId} starts a draft from another version (its code's next version, or a new code), the fields sent
 * replacing those copied. Create, update and activate can be put under maker-checker.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryScenarioService {

    static final String ENTITY = "scenario";

    private final TreasuryStressDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryScenarioRepository scenarioRepository;
    private final TreasuryScenarioShockRepository shockRepository;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final ScenarioRequest request = this.validator.validateScenario(command.json(), true);
        final TreasuryScenario source = request.copyFromId() == null ? null : find(request.copyFromId());
        final String code = request.code() != null ? request.code() : source.getCode();
        final List<TreasuryScenario> versions = this.scenarioRepository.findByCodeOrderByVersionAsc(code);
        if (versions.stream().anyMatch(v -> v.getStatus() == ConfigStatus.DRAFT)) {
            throw new TreasuryWorkflowException(ENTITY, "draft.exists", "Scenario " + code + " already has a draft version", code);
        }
        final Long userId = this.actor.userId(command);
        final TreasuryScenario scenario = new TreasuryScenario();
        scenario.setCode(code);
        scenario.setVersion(versions.isEmpty() ? 1 : versions.getLast().getVersion() + 1);
        scenario.setName(request.has(NAME_PARAM) || source == null ? request.name() : source.getName());
        scenario.setScenarioType(request.scenarioType() != null || source == null ? request.scenarioType() : source.getScenarioType());
        scenario.setSeverity(request.has(SEVERITY_PARAM) || source == null ? request.severity() : source.getSeverity());
        scenario.setDescription(request.has(DESCRIPTION_PARAM) || source == null ? request.description() : source.getDescription());
        final LocalDate effective = request.effectiveDate() != null ? request.effectiveDate()
                : source != null ? source.getEffectiveDate() : DateUtils.getBusinessLocalDate();
        scenario.setEffectiveDate(effective);
        scenario.setStatus(ConfigStatus.DRAFT);
        scenario.setCreatedBy(userId);
        scenario.setLastModifiedBy(userId);
        scenario.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.scenarioRepository.saveAndFlush(scenario);
        final List<Shock> shocks = request.shocks() != null ? request.shocks() : shocks(source.getId());
        saveShocks(scenario, shocks);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("code", code);
        changes.put("version", scenario.getVersion());
        changes.put("status", scenario.getStatus().name());
        changes.put("shocks", shocks.size());
        if (source != null) {
            changes.put("copiedFrom", source.getCode() + " v" + source.getVersion());
        }
        return result(command, scenario.getId(), changes);
    }

    @Transactional
    public CommandProcessingResult update(final Long scenarioId, final JsonCommand command) {
        final ScenarioRequest request = this.validator.validateScenario(command.json(), false);
        final TreasuryScenario scenario = draft(scenarioId, "changed");
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.has(NAME_PARAM)) {
            scenario.setName(request.name());
            changes.put(NAME_PARAM, request.name());
        }
        if (request.has(SCENARIO_TYPE_PARAM) && request.scenarioType() != null) {
            scenario.setScenarioType(request.scenarioType());
            changes.put(SCENARIO_TYPE_PARAM, request.scenarioType().name());
        }
        if (request.has(SEVERITY_PARAM)) {
            scenario.setSeverity(request.severity());
            changes.put(SEVERITY_PARAM, request.severity());
        }
        if (request.has(EFFECTIVE_DATE_PARAM) && request.effectiveDate() != null) {
            scenario.setEffectiveDate(request.effectiveDate());
            changes.put(EFFECTIVE_DATE_PARAM, request.effectiveDate());
        }
        if (request.has(DESCRIPTION_PARAM)) {
            scenario.setDescription(request.description());
            changes.put(DESCRIPTION_PARAM, request.description());
        }
        if (request.shocks() != null) {
            this.shockRepository.deleteByScenarioId(scenarioId);
            this.shockRepository.flush();
            saveShocks(scenario, request.shocks());
            changes.put("shocks", request.shocks().size());
        }
        scenario.setLastModifiedBy(this.actor.userId(command));
        scenario.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.scenarioRepository.saveAndFlush(scenario);
        return result(command, scenarioId, changes);
    }

    @Transactional
    public CommandProcessingResult delete(final Long scenarioId, final JsonCommand command) {
        final TreasuryScenario scenario = draft(scenarioId, "deleted");
        // the shocks go first: their foreign key holds the scenario
        this.shockRepository.deleteByScenarioId(scenarioId);
        this.shockRepository.flush();
        this.scenarioRepository.delete(scenario);
        return result(command, scenarioId, Map.of());
    }

    @Transactional
    public CommandProcessingResult activate(final Long scenarioId, final JsonCommand command) {
        this.validator.validateNote(command.json());
        final TreasuryScenario scenario = draft(scenarioId, "activated");
        if (DateUtils.isAfterBusinessDate(scenario.getEffectiveDate())) {
            throw new TreasuryWorkflowException(ENTITY, "effective.date.in.future",
                    "Scenario " + scenario.getCode() + " is effective from " + scenario.getEffectiveDate() + ", after the business date",
                    scenarioId);
        }
        final Map<String, Object> changes = new LinkedHashMap<>();
        for (final TreasuryScenario active : this.scenarioRepository.findByCodeAndStatus(scenario.getCode(), ConfigStatus.ACTIVE)) {
            active.setStatus(ConfigStatus.SUPERSEDED);
            this.scenarioRepository.saveAndFlush(active);
            changes.put("supersededVersion", active.getVersion());
        }
        scenario.setStatus(ConfigStatus.ACTIVE);
        scenario.setActivatedBy(this.actor.userId(command));
        scenario.setActivatedOnDate(DateUtils.getBusinessLocalDate());
        this.scenarioRepository.saveAndFlush(scenario);
        changes.put("status", ConfigStatus.ACTIVE.name());
        return result(command, scenarioId, changes);
    }

    /** Retires the active version: SUPERSEDED with no active version of the code, so no stress run uses it. */
    @Transactional
    public CommandProcessingResult deactivate(final Long scenarioId, final JsonCommand command) {
        this.validator.validateNote(command.json());
        final TreasuryScenario scenario = find(scenarioId);
        if (scenario.getStatus() != ConfigStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "not.active",
                    "Scenario " + scenario.getCode() + " version " + scenario.getVersion() + " is " + scenario.getStatus(), scenarioId);
        }
        scenario.setStatus(ConfigStatus.SUPERSEDED);
        scenario.setLastModifiedBy(this.actor.userId(command));
        scenario.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.scenarioRepository.saveAndFlush(scenario);
        return result(command, scenarioId, Map.of("status", ConfigStatus.SUPERSEDED.name()));
    }

    // ---------- reads ----------

    @Transactional(readOnly = true)
    public List<TreasuryScenarioData> retrieveAll() {
        return this.scenarioRepository.findAllByOrderByCodeAscVersionAsc().stream().map(this::data).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryScenarioData retrieveOne(final Long scenarioId) {
        return data(find(scenarioId));
    }

    /** The ACTIVE version of every code. */
    public List<TreasuryScenario> active() {
        return this.scenarioRepository.findByStatusOrderByCodeAsc(ConfigStatus.ACTIVE);
    }

    public List<Shock> shocks(final Long scenarioId) {
        return this.shockRepository.findByScenarioIdOrderBySequenceNumberAsc(scenarioId).stream()
                .map(s -> new Shock(s.getShockType(), s.getLabel(), s.getSource(), s.getLineCode(), s.getProductId(), s.getBucketCode(),
                        s.getSide(), s.getFactor(), s.getAssetClass(), s.getFundingSourceType(), s.getFundingLineId()))
                .toList();
    }

    public TreasuryScenario find(final Long scenarioId) {
        return this.scenarioRepository.findById(scenarioId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Stress scenario " + scenarioId + " does not exist", scenarioId));
    }

    private TreasuryScenarioData data(final TreasuryScenario s) {
        final List<TreasuryScenarioData.Shock> shocks = this.shockRepository.findByScenarioIdOrderBySequenceNumberAsc(s.getId()).stream()
                .map(x -> new TreasuryScenarioData.Shock(x.getSequenceNumber(), x.getShockType().name(), x.getLabel(),
                        x.getSource() == null ? null : x.getSource().name(), x.getLineCode(), x.getProductId(), x.getBucketCode(),
                        x.getSide() == null ? null : x.getSide().name(), x.getFactor(), x.getAssetClass(),
                        x.getFundingSourceType() == null ? null : x.getFundingSourceType().name(), x.getFundingLineId()))
                .toList();
        return new TreasuryScenarioData(s.getId(), s.getCode(), s.getName(), s.getVersion(), s.getStatus().name(),
                s.getScenarioType().name(), s.getSeverity(), s.getEffectiveDate(), s.getDescription(), s.getCreatedBy(),
                s.getLastModifiedBy(), s.getActivatedBy(), s.getActivatedOnDate(), shocks);
    }

    private void saveShocks(final TreasuryScenario scenario, final List<Shock> shocks) {
        int seq = 0;
        for (final Shock shock : shocks) {
            final TreasuryScenarioShock row = new TreasuryScenarioShock();
            row.setScenarioId(scenario.getId());
            row.setSequenceNumber(++seq);
            row.setShockType(shock.type());
            row.setLabel(shock.label());
            row.setSource(shock.source());
            row.setLineCode(shock.lineCode());
            row.setProductId(shock.productId());
            row.setBucketCode(shock.bucketCode());
            row.setSide(shock.side());
            row.setFactor(shock.factor());
            row.setAssetClass(shock.assetClass());
            row.setFundingSourceType(shock.fundingSourceType());
            row.setFundingLineId(shock.fundingLineId());
            this.shockRepository.save(row);
        }
        this.shockRepository.flush();
    }

    private TreasuryScenario draft(final Long scenarioId, final String step) {
        final TreasuryScenario scenario = find(scenarioId);
        if (scenario.getStatus() != ConfigStatus.DRAFT) {
            throw new TreasuryWorkflowException(ENTITY, "not.draft", "Scenario " + scenario.getCode() + " version " + scenario.getVersion()
                    + " is " + scenario.getStatus() + "; only a draft can be " + step, scenarioId);
        }
        return scenario;
    }

    private static CommandProcessingResult result(final JsonCommand command, final Long id, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(id).with(new LinkedHashMap<>(changes))
                .build();
    }
}
