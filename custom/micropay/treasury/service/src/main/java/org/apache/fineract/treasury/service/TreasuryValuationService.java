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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.FairValueCalculator;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryDealValuation;
import org.apache.fineract.treasury.domain.TreasuryDealValuationRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasuryPrice;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.service.DealValuationPlanner.Release;
import org.apache.fineract.treasury.service.DealValuationPlanner.ValuationGl;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Fair value revaluation of bills and bonds classified FVOCI or FVTPL, run by the "Treasury Valuation" job after the
 * accrual. Each holding is valued as at the day after the closed business day, the date its amortised cost is accrued
 * to: the clean fair value from the latest price on or before the closed day against the clean amortised cost. The
 * adjustment (fair value less amortised cost) is posted cumulatively: only its change since the last valuation
 * ({@code TR-DV-<deal>-<n>}), to profit or loss (FVTPL) or the OCI reserve (FVOCI). A holding without a price, or whose
 * latest price is older than the currency's {@code stalePriceDays}, is not revalued and gets a STALE_PRICE event.
 * Holdings at amortised cost are never revalued.
 *
 * <p>
 * When a holding leaves the books, by termination or maturity, its adjustment is released: see
 * {@link DealValuationPlanner#release}.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryValuationService {

    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryDealValuationRepository valuationRepository;
    private final TreasuryPriceService priceService;
    private final TreasurySettingService settingService;
    private final TreasuryJournalPoster journalPoster;
    private final TreasuryDealEventRepository eventRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final AppUserRepositoryWrapper appUserRepository;
    private final PlatformTransactionManager transactionManager;

    public enum Outcome {
        NOT_REVALUED, REVALUED, UNCHANGED, STALE
    }

    public record RunSummary(LocalDate closedDay, int deals, int revalued, int stale, int skipped) {
    }

    public RunSummary runForClosedDay() {
        return run(DateUtils.getBusinessLocalDate().minusDays(1));
    }

    public RunSummary run(final LocalDate closedDay) {
        final TransactionTemplate perDeal = new TransactionTemplate(this.transactionManager);
        perDeal.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final List<Long> ids = new TransactionTemplate(this.transactionManager).execute(
                status -> this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED).stream().map(TreasuryDeal::getId).toList());
        final Long systemUserId = new TransactionTemplate(this.transactionManager)
                .execute(status -> this.appUserRepository.fetchSystemUser().getId());
        int revalued = 0;
        int stale = 0;
        int skipped = 0;
        for (final Long dealId : ids) {
            try {
                final Outcome outcome = perDeal.execute(status -> {
                    final TreasuryDeal deal = find(dealId);
                    return revalue(deal, this.dealTypeService.find(deal.getDealTypeId()), closedDay, systemUserId);
                });
                if (outcome == Outcome.REVALUED) {
                    revalued++;
                } else if (outcome == Outcome.STALE) {
                    stale++;
                }
            } catch (RuntimeException e) {
                skipped++;
                log.error("Treasury valuation skipped deal {} for {}: {}", dealId, closedDay, e.getMessage());
            }
        }
        log.info("Treasury valuation for {}: {} settled deals, {} revalued, {} without a current price, {} skipped", closedDay, ids.size(),
                revalued, stale, skipped);
        return new RunSummary(closedDay, ids.size(), revalued, stale, skipped);
    }

    Outcome revalue(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate closedDay, final Long userId) {
        final InstrumentKind kind = type.getInstrumentKind();
        if (!TreasuryDealTypeService.revalued(kind, deal.getClassification())) {
            return Outcome.NOT_REVALUED;
        }
        final List<String> missing = TreasuryDealTypeService.missingValuationAccounts(type, deal.getClassification());
        if (!missing.isEmpty()) {
            // a deal captured at fair value before its type had valuation accounts
            log.warn("Treasury valuation skipped deal {}: deal type {} lacks {}", deal.getId(), type.getCode(), missing);
            return Outcome.NOT_REVALUED;
        }
        final LocalDate accruedTo = deal.getAccruedToDate() == null ? deal.getValueDate() : deal.getAccruedToDate();
        final LocalDate date = closedDay.plusDays(1).isBefore(accruedTo) ? closedDay.plusDays(1) : accruedTo;
        if (!date.isAfter(deal.getValueDate()) || !date.isBefore(deal.getMaturityDate())) {
            return Outcome.NOT_REVALUED;
        }
        if (deal.getLastValuationDate() != null && !date.isAfter(deal.getLastValuationDate())) {
            return Outcome.UNCHANGED;
        }
        final LocalDate priceOn = date.minusDays(1);
        final String instrument = TreasuryPriceService.instrumentId(deal);
        final Optional<TreasuryPrice> price = this.priceService.latest(instrument, priceOn);
        final int scale = this.referenceDataLookup.decimalPlaces(deal.getCurrencyCode());
        final BigDecimal fairValue = price.map(p -> FairValueCalculator.fairValue(kind, TreasuryPledgeService.outstanding(deal),
                p.getCleanPrice(), p.getYieldRate(), deal.getDayCount(), deal.getValueDate(), deal.getMaturityDate(), date, scale))
                .orElse(null);
        final int staleDays = this.settingService.settingsFor(deal.getCurrencyCode()).getStalePriceDays();
        if (fairValue == null || ChronoUnit.DAYS.between(price.get().getPriceDate(), priceOn) > staleDays) {
            DealEvents.record(this.eventRepository, deal.getId(), TreasuryDealEventType.STALE_PRICE, userId,
                    price.isEmpty() || fairValue == null
                            ? "No " + (kind == InstrumentKind.GOVERNMENT_BOND ? "clean price" : "yield or price") + " of " + instrument
                                    + " on or before " + priceOn + "; not revalued"
                            : "The latest price of " + instrument + " is of " + price.get().getPriceDate() + ", more than " + staleDays
                                    + " days before " + priceOn + "; not revalued",
                    null);
            return Outcome.STALE;
        }
        final BigDecimal amortisedCost = DealLifecyclePlanner.carrying(deal, kind).cleanAmortisedCost().setScale(scale,
                RoundingMode.HALF_UP);
        final BigDecimal target = fairValue.subtract(amortisedCost);
        final BigDecimal change = target.subtract(deal.getFairValueAdjustment());
        final int sequence = (int) this.valuationRepository.countByDealId(deal.getId()) + 1;
        String transactionId = null;
        if (change.signum() != 0) {
            transactionId = this.journalPoster.post(TreasuryEventType.DEAL_VALUATION, deal.getId(),
                    "TR-DV-" + deal.getId() + "-" + sequence, deal.getCurrencyCode(), priceOn,
                    "Treasury deal " + deal.getId() + " revalued as at " + priceOn, deal.getExternalReference(),
                    DealValuationPlanner.revaluation(deal.getClassification(), change, deal.getOfficeId(), gl(type)));
        }
        final TreasuryDealValuation valuation = new TreasuryDealValuation();
        valuation.setDealId(deal.getId());
        valuation.setSequenceNumber(sequence);
        valuation.setValuationDate(date);
        valuation.setPriceDate(price.get().getPriceDate());
        valuation.setCleanPrice(price.get().getCleanPrice());
        valuation.setYieldRate(price.get().getYieldRate());
        valuation.setFairValue(fairValue);
        valuation.setAmortisedCost(amortisedCost);
        valuation.setAdjustment(target);
        valuation.setAdjustmentChange(change);
        valuation.setTransactionId(transactionId);
        valuation.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.valuationRepository.save(valuation);
        deal.setFairValueAdjustment(target);
        deal.setLastFairValue(fairValue);
        deal.setLastValuationDate(date);
        deal.setLastPriceDate(price.get().getPriceDate());
        this.dealRepository.saveAndFlush(deal);
        DealEvents.record(this.eventRepository, deal.getId(), TreasuryDealEventType.REVALUED, userId,
                "Fair value " + fairValue.toPlainString() + " against amortised cost " + amortisedCost.toPlainString() + ": adjustment "
                        + target.toPlainString() + " (change " + change.toPlainString() + "), price of " + price.get().getPriceDate(),
                transactionId);
        return Outcome.REVALUED;
    }

    /** The legs taking the holding's fair value adjustment off the books with it (none when there is none). */
    Release release(final TreasuryDeal deal, final TreasuryDealType type) {
        if (!TreasuryDealTypeService.revalued(type.getInstrumentKind(), deal.getClassification())) {
            return new Release(List.of(), BigDecimal.ZERO, BigDecimal.ZERO);
        }
        return DealValuationPlanner.release(deal.getClassification(), deal.getFairValueAdjustment(), deal.getOfficeId(), gl(type));
    }

    /** After the release legs are posted (with a termination, or at maturity). */
    void released(final TreasuryDeal deal, final Release release, final Long userId, final String transactionId) {
        if (release.legs().isEmpty()) {
            return;
        }
        final BigDecimal adjustment = deal.getFairValueAdjustment();
        deal.setFairValueAdjustment(BigDecimal.ZERO);
        this.dealRepository.saveAndFlush(deal);
        DealEvents.record(this.eventRepository, deal.getId(), TreasuryDealEventType.FAIR_VALUE_RELEASED, userId,
                "Fair value adjustment of " + adjustment.toPlainString() + " released"
                        + (release.ociRecycled().signum() != 0 ? "; " + release.ociRecycled().toPlainString() + " recycled from OCI" : ""),
                transactionId);
    }

    /** At maturity: the adjustment left is released on the maturity date ({@code TR-DV-<deal>-M}). */
    void releaseAtMaturity(final TreasuryDeal deal, final TreasuryDealType type, final Long userId) {
        final Release release = release(deal, type);
        if (release.legs().isEmpty()) {
            return;
        }
        final String transactionId = this.journalPoster.post(TreasuryEventType.DEAL_VALUATION, deal.getId(), "TR-DV-" + deal.getId() + "-M",
                deal.getCurrencyCode(), deal.getMaturityDate(),
                "Treasury deal " + deal.getId() + " fair value adjustment released at maturity", deal.getExternalReference(),
                release.legs());
        if (release.realised().signum() != 0) {
            deal.setRealisedResult((deal.getRealisedResult() == null ? BigDecimal.ZERO : deal.getRealisedResult()).add(release.realised()));
        }
        released(deal, release, userId, transactionId);
    }

    static ValuationGl gl(final TreasuryDealType type) {
        return new ValuationGl(type.getFairValueAdjustmentGlAccountId(), type.getFairValueGainGlAccountId(),
                type.getFairValueLossGlAccountId(), type.getFairValueReserveGlAccountId(), type.getImpairmentExpenseGlAccountId(),
                type.getEclAllowanceGlAccountId(), type.getRealisedGainGlAccountId(), type.getRealisedLossGlAccountId());
    }

    private TreasuryDeal find(final Long dealId) {
        return this.dealRepository.findById(dealId).orElseThrow(
                () -> new TreasuryWorkflowException("deal", "not.found", "Treasury deal " + dealId + " does not exist", dealId));
    }
}
