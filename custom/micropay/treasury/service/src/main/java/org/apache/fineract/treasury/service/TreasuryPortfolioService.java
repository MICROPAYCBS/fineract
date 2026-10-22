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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryPortfolioData;
import org.apache.fineract.treasury.data.TreasuryPortfolioData.Holding;
import org.apache.fineract.treasury.data.TreasuryPortfolioData.Total;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.service.DealLifecyclePlanner.Carrying;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The holdings portfolio: settled asset deals held on the date (value date on or before it, maturing after it).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryPortfolioService {

    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryPledgeService pledgeService;

    public TreasuryPortfolioData portfolio(final LocalDate requestedDate, final String requestedCurrency) {
        final LocalDate date = requestedDate == null ? DateUtils.getBusinessLocalDate() : requestedDate;
        final String currency = StringUtils.isBlank(requestedCurrency) ? null : requestedCurrency.trim().toUpperCase(Locale.ROOT);
        final List<Holding> holdings = new ArrayList<>();
        final Map<String, List<Holding>> byCurrency = new TreeMap<>();
        for (final TreasuryDeal deal : this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED)) {
            final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
            if (!type.getInstrumentKind().isAsset() || date.isBefore(deal.getValueDate()) || !date.isBefore(deal.getMaturityDate())
                    || currency != null && !currency.equals(deal.getCurrencyCode())) {
                continue;
            }
            final Holding holding = holding(deal, type, date);
            holdings.add(holding);
            byCurrency.computeIfAbsent(deal.getCurrencyCode(), c -> new ArrayList<>()).add(holding);
        }
        final List<Total> totals = byCurrency.entrySet().stream().map(e -> total(e.getKey(), e.getValue())).toList();
        return new TreasuryPortfolioData(date, currency, holdings, totals);
    }

    private Holding holding(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate date) {
        final Carrying carrying = DealLifecyclePlanner.carrying(deal, type.getInstrumentKind());
        final DealClassification classification = deal.getClassification();
        final boolean revalued = TreasuryDealTypeService.revalued(type.getInstrumentKind(), classification);
        final BigDecimal gross = carrying.assetTotal();
        final BigDecimal adjustment = deal.getFairValueAdjustment();
        final BigDecimal allowance = deal.getEclAllowance();
        final BigDecimal carryingAmount = gross.add(adjustment)
                .subtract(classification == DealClassification.AMORTISED_COST ? allowance : BigDecimal.ZERO);
        final BigDecimal pledged = this.pledgeService.encumberedAmount(deal.getId(), date);
        final String counterparty = this.counterpartyRepository.findById(deal.getCounterpartyId()).map(TreasuryCounterparty::getShortCode)
                .orElse(null);
        return new Holding(deal.getId(), type.getCode(), type.getInstrumentKind().name(), counterparty, deal.getCurrencyCode(),
                TreasuryPriceService.instrumentId(deal), classification.name(), deal.getMaturityDate(),
                TreasuryPledgeService.outstanding(deal), carrying.cleanAmortisedCost(), carrying.interest(), gross,
                revalued ? deal.getLastFairValue() : null, revalued ? deal.getLastValuationDate() : null, adjustment,
                classification == DealClassification.FVOCI ? adjustment.add(allowance) : BigDecimal.ZERO, deal.getEclStage(), allowance,
                carryingAmount, pledged, this.pledgeService.unencumberedAmount(deal, date));
    }

    private static Total total(final String currency, final List<Holding> holdings) {
        BigDecimal nominal = BigDecimal.ZERO;
        BigDecimal amortisedCost = BigDecimal.ZERO;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal adjustment = BigDecimal.ZERO;
        BigDecimal reserve = BigDecimal.ZERO;
        BigDecimal allowance = BigDecimal.ZERO;
        BigDecimal carrying = BigDecimal.ZERO;
        BigDecimal pledged = BigDecimal.ZERO;
        BigDecimal unencumbered = BigDecimal.ZERO;
        for (final Holding holding : holdings) {
            nominal = nominal.add(holding.nominal());
            amortisedCost = amortisedCost.add(holding.amortisedCost());
            gross = gross.add(holding.grossCarryingAmount());
            adjustment = adjustment.add(holding.fairValueAdjustment());
            reserve = reserve.add(holding.ociReserve());
            allowance = allowance.add(holding.eclAllowance());
            carrying = carrying.add(holding.carryingAmount());
            pledged = pledged.add(holding.pledged());
            unencumbered = unencumbered.add(holding.unencumbered());
        }
        return new Total(currency, holdings.size(), nominal, amortisedCost, gross, adjustment, reserve, allowance, carrying, pledged,
                unencumbered);
    }
}
