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

import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryPriceData;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryPrice;
import org.apache.fineract.treasury.domain.TreasuryPriceRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.PriceRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Market prices per instrument and date: a clean price per 100 (bonds) or a yield (bills), entered by hand or from Bank
 * of Uganda auction and secondary market results. An instrument is named by its ISIN, or by the deal's external
 * reference when it has none. A price entered again for the same instrument and date replaces the earlier one.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryPriceService {

    static final String ENTITY = "price";

    private final TreasuryHoldingDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryPriceRepository priceRepository;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final List<PriceRequest> requests = this.validator.validatePrices(command.json());
        final Set<String> seen = new HashSet<>();
        for (final PriceRequest request : requests) {
            if (DateUtils.isAfterBusinessDate(request.priceDate())) {
                throw new TreasuryWorkflowException(
                        ENTITY, "future.date", "The price date " + request.priceDate() + " of " + request.instrumentId()
                                + " is after the business date " + DateUtils.getBusinessLocalDate(),
                        request.instrumentId(), request.priceDate());
            }
            if (!seen.add(request.instrumentId() + "|" + request.priceDate())) {
                throw new TreasuryWorkflowException(ENTITY, "duplicate.in.batch",
                        "The batch prices " + request.instrumentId() + " on " + request.priceDate() + " twice", request.instrumentId(),
                        request.priceDate());
            }
        }
        final Long userId = this.actor.userId(command);
        int created = 0;
        int replaced = 0;
        Long lastId = null;
        for (final PriceRequest request : requests) {
            final Optional<TreasuryPrice> existing = this.priceRepository.findByInstrumentIdAndPriceDate(request.instrumentId(),
                    request.priceDate());
            final TreasuryPrice price = existing.orElseGet(TreasuryPrice::new);
            price.setInstrumentId(request.instrumentId());
            price.setPriceDate(request.priceDate());
            price.setCleanPrice(request.cleanPrice());
            price.setYieldRate(request.yield());
            price.setSource(request.source());
            price.setEnteredBy(userId);
            price.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
            this.priceRepository.saveAndFlush(price);
            lastId = price.getId();
            if (existing.isPresent()) {
                replaced++;
            } else {
                created++;
            }
        }
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("created", created);
        changes.put("replaced", replaced);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(requests.size() == 1 ? lastId : null)
                .with(changes).build();
    }

    /** The latest price of the instrument on or before the date. */
    @Transactional(readOnly = true)
    public Optional<TreasuryPrice> latest(final String instrumentId, final LocalDate date) {
        return instrumentId == null ? Optional.empty()
                : this.priceRepository.findFirstByInstrumentIdAndPriceDateLessThanEqualOrderByPriceDateDesc(instrumentId, date);
    }

    @Transactional(readOnly = true)
    public TreasuryPriceData retrieveLatest(final String instrumentId, final LocalDate date) {
        return latest(instrumentId, date == null ? DateUtils.getBusinessLocalDate() : date).map(TreasuryPriceData::from)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "not.found",
                        "No price of " + instrumentId + " on or before " + (date == null ? DateUtils.getBusinessLocalDate() : date),
                        instrumentId));
    }

    @Transactional(readOnly = true)
    public List<TreasuryPriceData> retrieveHistory(final String instrumentId, final LocalDate from, final LocalDate to) {
        return this.priceRepository.findByInstrumentIdAndPriceDateBetweenOrderByPriceDateAsc(instrumentId, from, to).stream()
                .map(TreasuryPriceData::from).toList();
    }

    /** How a deal's instrument is named in the price table: its ISIN, or else its external reference. */
    public static String instrumentId(final TreasuryDeal deal) {
        return deal.getIsin() != null ? deal.getIsin() : deal.getExternalReference();
    }
}
