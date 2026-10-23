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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryFxRateData;
import org.apache.fineract.treasury.domain.TreasuryFxRate;
import org.apache.fineract.treasury.domain.TreasuryFxRateRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.fx.FxRateStatus;
import org.apache.fineract.treasury.fx.FxRateType;
import org.apache.fineract.treasury.fx.FxRates;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.RateRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange rates against the base currency: entered one by one or in a batch, both through
 * {@code CREATE_TREASURYFXRATE} (maker-checker capable). A rate of the same pair, date, type and source replaces the
 * earlier one, which is kept as SUPERSEDED. A lookup takes the latest active rate on or before the date; for
 * revaluation the closing MID rate may be at most the configured number of days old.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryFxRateService {

    static final String ENTITY = "fx.rate";

    private final TreasuryFxDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryFxRateRepository rateRepository;
    private final TreasuryFxConfig fxConfig;
    private final TreasuryReferenceDataLookup referenceDataLookup;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final List<RateRequest> requests = this.validator.validateRates(command.json());
        final Long userId = this.actor.userId(command);
        final String base = this.fxConfig.baseCurrency();
        final List<Long> ids = new ArrayList<>();
        int superseded = 0;
        for (final RateRequest r : requests) {
            if (r.baseCurrency() != null && !r.baseCurrency().equals(base)) {
                throw new TreasuryWorkflowException(ENTITY, "base.currency.differs",
                        "Rates are against the base currency " + base + ", not " + r.baseCurrency(), r.baseCurrency());
            }
            if (r.quoteCurrency().equals(base)) {
                throw new TreasuryWorkflowException(ENTITY, "quote.is.base", "The quote currency is the base currency " + base, base);
            }
            if (!this.referenceDataLookup.isOrganisationCurrency(r.quoteCurrency())) {
                throw new TreasuryWorkflowException(ENTITY, "currency.invalid", r.quoteCurrency() + " is not an organisation currency",
                        r.quoteCurrency());
            }
            if (DateUtils.isAfterBusinessDate(r.rateDate())) {
                throw new TreasuryWorkflowException(ENTITY, "future.date", "The rate date " + r.rateDate() + " is after the business date",
                        r.rateDate());
            }
            final TreasuryFxRate rate = new TreasuryFxRate();
            rate.setBaseCurrency(base);
            rate.setQuoteCurrency(r.quoteCurrency());
            rate.setRateDate(r.rateDate());
            rate.setRateType(r.rateType());
            rate.setRate(r.rate());
            rate.setSource(r.source());
            rate.setStatus(FxRateStatus.ACTIVE);
            rate.setNote(r.note());
            rate.setCreatedBy(userId);
            rate.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
            final List<TreasuryFxRate> earlier = this.rateRepository
                    .findByBaseCurrencyAndQuoteCurrencyAndRateDateAndRateTypeAndSourceAndStatus(base, r.quoteCurrency(), r.rateDate(),
                            r.rateType(), r.source(), FxRateStatus.ACTIVE);
            this.rateRepository.saveAndFlush(rate);
            for (final TreasuryFxRate old : earlier) {
                old.setStatus(FxRateStatus.SUPERSEDED);
                old.setSupersededBy(rate.getId());
                this.rateRepository.save(old);
                superseded++;
            }
            ids.add(rate.getId());
        }
        this.rateRepository.flush();
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("rates", ids.size());
        changes.put("superseded", superseded);
        changes.put("rateIds", ids);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(ids.getFirst()).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult cancel(final Long rateId, final JsonCommand command) {
        this.validator.validateNote(command.json());
        final TreasuryFxRate rate = this.rateRepository.findById(rateId)
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "not.found", "FX rate " + rateId + " does not exist", rateId));
        if (rate.getStatus() != FxRateStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "not.active", "FX rate " + rateId + " is " + rate.getStatus(), rateId);
        }
        rate.setStatus(FxRateStatus.CANCELLED);
        this.rateRepository.saveAndFlush(rate);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", FxRateStatus.CANCELLED.name());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(rateId).with(changes).build();
    }

    /** The active rates of a currency against the base currency. */
    public List<FxRates.Rate> rates(final String quoteCurrency) {
        return this.rateRepository
                .findByBaseCurrencyAndQuoteCurrencyAndStatusOrderByRateDateAscIdAsc(this.fxConfig.baseCurrency(), quoteCurrency,
                        FxRateStatus.ACTIVE)
                .stream().map(r -> new FxRates.Rate(r.getId(), r.getRateDate(), r.getRateType(), r.getSource(), r.getRate())).toList();
    }

    /** The MID rate in effect on the date, however old. */
    public Optional<FxRates.Rate> mid(final String quoteCurrency, final LocalDate date) {
        return FxRates.latest(rates(quoteCurrency), FxRateType.MID, date);
    }

    /** The closing MID rate for revaluation: refused when there is none or it is older than the maximum age. */
    public FxRates.Rate closingMid(final String quoteCurrency, final LocalDate date) {
        final FxRates.Rate rate = mid(quoteCurrency, date).orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "missing",
                "No MID rate of " + quoteCurrency + " on or before " + date, quoteCurrency, date));
        if (FxRates.isStale(rate, date, this.fxConfig.maxRateAgeDays())) {
            throw new TreasuryWorkflowException(ENTITY, "stale", "The latest MID rate of " + quoteCurrency + " is of " + rate.rateDate()
                    + ", more than " + this.fxConfig.maxRateAgeDays() + " days before " + date, quoteCurrency, rate.rateDate(), date);
        }
        return rate;
    }

    /**
     * @param pair
     *            the quote currency, or the pair as {@code UGX/USD} or {@code USD/UGX}
     * @param date
     *            with a date, the rate of each type in effect on it; without, every rate in the period
     */
    @Transactional(readOnly = true)
    public List<TreasuryFxRateData> retrieve(final String pair, final LocalDate date, final LocalDate from, final LocalDate to) {
        final String base = this.fxConfig.baseCurrency();
        final List<String> quotes = pair == null
                ? this.referenceDataLookup.organisationCurrencies().stream().filter(c -> !c.equals(base)).toList()
                : List.of(Arrays.stream(pair.trim().toUpperCase().split("/")).filter(c -> !c.equals(base)).findFirst().orElse(base));
        final List<TreasuryFxRateData> result = new ArrayList<>();
        for (final String quote : quotes) {
            final List<TreasuryFxRate> rows = this.rateRepository.findAll().stream()
                    .filter(r -> r.getBaseCurrency().equals(base) && r.getQuoteCurrency().equals(quote)).toList();
            if (date != null) {
                for (final FxRateType type : FxRateType.values()) {
                    FxRates.latest(rates(quote), type, date)
                            .flatMap(rate -> rows.stream().filter(r -> r.getId().equals(rate.id())).findFirst())
                            .ifPresent(r -> result.add(data(r)));
                }
            } else {
                rows.stream().filter(r -> (from == null || !r.getRateDate().isBefore(from)) && (to == null || !r.getRateDate().isAfter(to)))
                        .sorted((a, b) -> !a.getRateDate().equals(b.getRateDate()) ? b.getRateDate().compareTo(a.getRateDate())
                                : Long.compare(b.getId(), a.getId()))
                        .forEach(r -> result.add(data(r)));
            }
        }
        return result;
    }

    private static TreasuryFxRateData data(final TreasuryFxRate r) {
        return new TreasuryFxRateData(r.getId(), r.getBaseCurrency(), r.getQuoteCurrency(), r.getRateDate(), r.getRateType().name(),
                r.getRate(), r.getSource().name(), r.getStatus().name(), r.getNote(), r.getCreatedBy(), r.getCreatedOnUtc(),
                r.getSupersededBy());
    }
}
