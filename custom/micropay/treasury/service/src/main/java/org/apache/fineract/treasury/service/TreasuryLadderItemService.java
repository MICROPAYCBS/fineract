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
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ACTIVE_FROM_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ACTIVE_TO_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DAY_OF_MONTH_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.DIRECTION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.OFFICE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryLiquidityApiConstants.ONE_OFF_DATE_PARAM;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryLadderItemData;
import org.apache.fineract.treasury.domain.TreasuryLadderItem;
import org.apache.fineract.treasury.domain.TreasuryLadderItemRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.LadderItemRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Ladder items: configured fixed flows, one-off or monthly, institution-wide or for one office. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryLadderItemService {

    static final String ENTITY = "ladder.item";

    private final TreasuryLiquidityDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryLadderItemRepository itemRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final LadderItemRequest request = this.validator.validateLadderItem(command.json(), true);
        final TreasuryLadderItem item = new TreasuryLadderItem();
        apply(item, request);
        final Long userId = this.actor.userId(command);
        item.setCreatedBy(userId);
        item.setLastEditedBy(userId);
        item.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        check(item);
        this.itemRepository.saveAndFlush(item);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(item.getId()).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long itemId, final JsonCommand command) {
        final LadderItemRequest request = this.validator.validateLadderItem(command.json(), false);
        final TreasuryLadderItem item = find(itemId);
        final Map<String, Object> changes = apply(item, request);
        item.setLastEditedBy(this.actor.userId(command));
        check(item);
        this.itemRepository.saveAndFlush(item);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(itemId).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult delete(final Long itemId, final JsonCommand command) {
        this.itemRepository.delete(find(itemId));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(itemId).build();
    }

    @Transactional(readOnly = true)
    public List<TreasuryLadderItemData> retrieveAll() {
        return this.itemRepository.findAllByOrderByIdAsc().stream().map(TreasuryLadderItemData::from).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryLadderItemData retrieveOne(final Long itemId) {
        return TreasuryLadderItemData.from(find(itemId));
    }

    private Map<String, Object> apply(final TreasuryLadderItem item, final LadderItemRequest request) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.has(NAME_PARAM)) {
            item.setName(request.name());
            changes.put(NAME_PARAM, request.name());
        }
        if (request.has(DIRECTION_PARAM)) {
            item.setDirection(request.direction());
            changes.put(DIRECTION_PARAM, request.direction().name());
        }
        if (request.has(AMOUNT_PARAM)) {
            item.setAmount(request.amount());
            changes.put(AMOUNT_PARAM, request.amount());
        }
        if (request.has(CURRENCY_CODE_PARAM)) {
            item.setCurrencyCode(request.currencyCode());
            changes.put(CURRENCY_CODE_PARAM, request.currencyCode());
        }
        if (request.has(OFFICE_ID_PARAM)) {
            item.setOfficeId(request.officeId());
            changes.put(OFFICE_ID_PARAM, request.officeId());
        }
        if (request.has(ONE_OFF_DATE_PARAM)) {
            item.setOneOffDate(request.oneOffDate());
            changes.put(ONE_OFF_DATE_PARAM, request.oneOffDate());
        }
        if (request.has(DAY_OF_MONTH_PARAM)) {
            item.setDayOfMonth(request.dayOfMonth());
            changes.put(DAY_OF_MONTH_PARAM, request.dayOfMonth());
        }
        if (request.has(ACTIVE_FROM_PARAM)) {
            item.setActiveFrom(request.activeFrom());
            changes.put(ACTIVE_FROM_PARAM, request.activeFrom());
        }
        if (request.has(ACTIVE_TO_PARAM)) {
            item.setActiveTo(request.activeTo());
            changes.put(ACTIVE_TO_PARAM, request.activeTo());
        }
        return changes;
    }

    private void check(final TreasuryLadderItem item) {
        if ((item.getOneOffDate() == null) == (item.getDayOfMonth() == null)) {
            throw new TreasuryWorkflowException(ENTITY, "schedule.invalid",
                    "A ladder item has either a oneOffDate or a dayOfMonth, not both or neither", item.getId());
        }
        if (item.getActiveTo() != null && item.getActiveTo().isBefore(item.getActiveFrom())) {
            throw new TreasuryWorkflowException(ENTITY, "window.invalid", "activeTo is before activeFrom", item.getId());
        }
        if (!this.referenceDataLookup.isOrganisationCurrency(item.getCurrencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.invalid", item.getCurrencyCode() + " is not an organisation currency",
                    item.getCurrencyCode());
        }
        if (item.getOfficeId() != null && !this.referenceDataLookup.officeExists(item.getOfficeId())) {
            throw new TreasuryWorkflowException(ENTITY, "office.not.found", "Office " + item.getOfficeId() + " does not exist",
                    item.getOfficeId());
        }
    }

    private TreasuryLadderItem find(final Long itemId) {
        return this.itemRepository.findById(itemId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury ladder item " + itemId + " does not exist", itemId));
    }
}
