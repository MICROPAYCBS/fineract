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

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryFundingLineData;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryFundingLine;
import org.apache.fineract.treasury.domain.TreasuryFundingLineRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.FundingLineRequest;
import org.apache.fineract.treasury.stress.FundingLineStatus;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The contingency funding plan's register of funding lines: lines received (committed or not), the BoU repo window,
 * asset sales, shareholder support and deposit drives, with what is available, how fast and until when, and who owns
 * them. This is also the register of undrawn credit lines received. They are not ladder flows: committed lines received
 * (and others marked {@code includeInCapacity}) count as counterbalancing capacity in stress runs from the day they can
 * be drawn. Create and update can be put under maker-checker.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryFundingLineService {

    static final String ENTITY = "funding.line";

    private final TreasuryStressDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryFundingLineRepository lineRepository;
    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final AppUserRepository appUserRepository;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final FundingLineRequest request = this.validator.validateFundingLine(command.json(), null);
        if (this.lineRepository.findByCode(request.code()).isPresent()) {
            throw new TreasuryWorkflowException(ENTITY, "code.exists", "A funding line with code " + request.code() + " exists",
                    request.code());
        }
        checkReferences(request);
        final TreasuryFundingLine line = new TreasuryFundingLine();
        apply(line, request);
        line.setCreatedBy(this.actor.userId(command));
        line.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.lineRepository.saveAndFlush(line);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("code", line.getCode());
        changes.put("availableAmount", available(line));
        changes.put("includeInCapacity", line.isIncludeInCapacity());
        return result(command, line, changes);
    }

    @Transactional
    public CommandProcessingResult update(final Long lineId, final JsonCommand command) {
        final TreasuryFundingLine line = find(lineId);
        final FundingLineRequest request = this.validator.validateFundingLine(command.json(), json(line));
        checkReferences(request);
        apply(line, request);
        line.setLastModifiedBy(this.actor.userId(command));
        line.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.lineRepository.saveAndFlush(line);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("availableAmount", available(line));
        changes.put("status", line.getStatus().name());
        return result(command, line, changes);
    }

    @Transactional(readOnly = true)
    public List<TreasuryFundingLineData> retrieveAll(final String currency, final String status) {
        return this.lineRepository.findAllByOrderByCodeAsc().stream()
                .filter(l -> currency == null || l.getCurrencyCode().equalsIgnoreCase(currency.trim()))
                .filter(l -> status == null || l.getStatus().name().equalsIgnoreCase(status.trim())).map(TreasuryFundingLineService::data)
                .toList();
    }

    @Transactional(readOnly = true)
    public TreasuryFundingLineData retrieveOne(final Long lineId) {
        return data(find(lineId));
    }

    /** What can be drawn: the amount entered, or the committed amount less what is drawn. */
    public static BigDecimal available(final TreasuryFundingLine line) {
        return line.getAvailableAmount() != null ? line.getAvailableAmount()
                : line.getCommittedAmount().subtract(line.getDrawnAmount()).max(BigDecimal.ZERO);
    }

    /** ACTIVE, in the currency and not expired before the date. */
    public static boolean availableOn(final TreasuryFundingLine line, final String currency, final LocalDate asOf) {
        return line.getStatus() == FundingLineStatus.ACTIVE && line.getCurrencyCode().equals(currency)
                && (line.getExpiryDate() == null || !line.getExpiryDate().isBefore(asOf));
    }

    private void checkReferences(final FundingLineRequest request) {
        if (!this.referenceDataLookup.isOrganisationCurrency(request.currencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.invalid", request.currencyCode() + " is not an organisation currency",
                    request.currencyCode());
        }
        if (request.counterpartyId() != null && !this.counterpartyRepository.existsById(request.counterpartyId())) {
            throw new TreasuryWorkflowException(ENTITY, "counterparty.not.found",
                    "Treasury counterparty " + request.counterpartyId() + " does not exist", request.counterpartyId());
        }
        if (request.ownerUserId() != null && !this.appUserRepository.existsById(request.ownerUserId())) {
            throw new TreasuryWorkflowException(ENTITY, "owner.not.found", "User " + request.ownerUserId() + " does not exist",
                    request.ownerUserId());
        }
    }

    private static void apply(final TreasuryFundingLine line, final FundingLineRequest r) {
        line.setCode(r.code());
        line.setName(r.name());
        line.setSourceType(r.sourceType());
        line.setCounterpartyId(r.counterpartyId());
        line.setCurrencyCode(r.currencyCode());
        line.setCommittedAmount(r.committedAmount());
        line.setDrawnAmount(r.drawnAmount());
        line.setAvailableAmount(r.availableAmount());
        line.setDaysToAccess(r.daysToAccess());
        line.setExpiryDate(r.expiryDate());
        line.setOwnerUserId(r.ownerUserId());
        line.setOwnerName(r.ownerName());
        line.setStatus(r.status());
        line.setIncludeInCapacity(r.includeInCapacity());
        line.setDescription(r.description());
    }

    static JsonObject json(final TreasuryFundingLine line) {
        final JsonObject json = new JsonObject();
        json.addProperty("code", line.getCode());
        json.addProperty("name", line.getName());
        json.addProperty("sourceType", line.getSourceType().name());
        if (line.getCounterpartyId() != null) {
            json.addProperty("counterpartyId", line.getCounterpartyId());
        }
        json.addProperty("currencyCode", line.getCurrencyCode());
        json.addProperty("committedAmount", line.getCommittedAmount().toPlainString());
        json.addProperty("drawnAmount", line.getDrawnAmount().toPlainString());
        if (line.getAvailableAmount() != null) {
            json.addProperty("availableAmount", line.getAvailableAmount().toPlainString());
        }
        json.addProperty("daysToAccess", line.getDaysToAccess());
        if (line.getExpiryDate() != null) {
            json.addProperty("expiryDate", line.getExpiryDate().toString());
        }
        if (line.getOwnerUserId() != null) {
            json.addProperty("ownerUserId", line.getOwnerUserId());
        }
        if (line.getOwnerName() != null) {
            json.addProperty("ownerName", line.getOwnerName());
        }
        json.addProperty("status", line.getStatus().name());
        json.addProperty("includeInCapacity", line.isIncludeInCapacity());
        if (line.getDescription() != null) {
            json.addProperty("description", line.getDescription());
        }
        return json;
    }

    static TreasuryFundingLineData data(final TreasuryFundingLine l) {
        return new TreasuryFundingLineData(l.getId(), l.getCode(), l.getName(), l.getSourceType().name(), l.getCounterpartyId(),
                l.getCurrencyCode(), l.getCommittedAmount(), l.getDrawnAmount(), available(l), l.getAvailableAmount(), l.getDaysToAccess(),
                l.getExpiryDate(), l.getOwnerUserId(), l.getOwnerName(), l.getStatus().name(), l.isIncludeInCapacity(), l.getDescription(),
                l.getCreatedBy(), l.getCreatedOnUtc(), l.getLastModifiedBy(), l.getLastModifiedOnUtc());
    }

    TreasuryFundingLine find(final Long lineId) {
        return this.lineRepository.findById(lineId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Funding line " + lineId + " does not exist", lineId));
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryFundingLine line,
            final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(line.getId()).with(changes).build();
    }
}
