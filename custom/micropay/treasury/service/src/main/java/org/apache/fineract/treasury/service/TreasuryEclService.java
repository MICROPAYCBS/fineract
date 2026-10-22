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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryEclParameterData;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.EclCalculator;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealEcl;
import org.apache.fineract.treasury.domain.TreasuryDealEclRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryEclParameter;
import org.apache.fineract.treasury.domain.TreasuryEclParameterRepository;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.EclParameterRequest;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.EclStageRequest;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * IFRS 9 expected credit losses of settled asset deals at amortised cost or FVOCI (FVTPL holdings carry none).
 *
 * <p>
 * ECL = EAD x PD x LGD. EAD is the gross carrying amount at amortised cost, accrued interest included. PD and LGD come
 * from {@code m_treasury_ecl_parameter} by the counterparty's rating (its rating code value, else its rating text) and
 * the deal's stage, falling back to the {@code DEFAULT} rating; stage 1 takes the 12-month PD, stages 2 and 3 the
 * lifetime PD. A deal may carry a manual PD, LGD, or ECL amount with a reason instead.
 * </p>
 *
 * <p>
 * The allowance is posted cumulatively ({@code TR-DE-<deal>-<n>}): Dr impairment expense / Cr loss allowance, or Cr OCI
 * reserve for FVOCI. It is recalculated monthly by the "Treasury ECL" job, on a stage change, and on demand, and
 * released (reversed through impairment) when the deal matures or is terminated. Interest on stage 3 deals still
 * accrues on the gross carrying amount (see the deal's {@code holding.stage3InterestOnGross}).
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryEclService {

    static final String ENTITY = "deal";
    static final String TRIGGER_JOB = "JOB";
    static final String TRIGGER_ON_DEMAND = "ON_DEMAND";
    static final String TRIGGER_STAGE_CHANGE = "STAGE_CHANGE";
    static final String TRIGGER_RELEASE = "RELEASE";

    private final TreasuryHoldingDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryDealEclRepository eclRepository;
    private final TreasuryEclParameterRepository parameterRepository;
    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryJournalPoster journalPoster;
    private final TreasuryDealEventRepository eventRepository;
    private final AppUserRepositoryWrapper appUserRepository;
    private final PlatformTransactionManager transactionManager;

    public record RunSummary(LocalDate date, int deals, int calculated, int skipped) {
    }

    /** What an ECL is based on: PD and LGD (percent), or a manual amount. */
    record Basis(BigDecimal pd, BigDecimal lgd, BigDecimal manualEcl, String source) {
    }

    record Calculation(BigDecimal exposure, BigDecimal ecl, BigDecimal change, String transactionId) {
    }

    private enum JobOutcome {
        CALCULATED, NOT_DUE, SKIPPED
    }

    public static boolean eligible(final InstrumentKind kind, final DealClassification classification) {
        return kind.isAsset() && classification != DealClassification.FVTPL;
    }

    // ---------- parameters ----------

    @Transactional
    public CommandProcessingResult updateParameter(final JsonCommand command) {
        final EclParameterRequest request = this.validator.validateEclParameter(command.json());
        final TreasuryEclParameter parameter = this.parameterRepository.findByRatingCodeAndStage(request.ratingCode(), request.stage())
                .orElseGet(TreasuryEclParameter::new);
        final boolean created = parameter.getId() == null;
        parameter.setRatingCode(request.ratingCode());
        parameter.setStage(request.stage());
        parameter.setPd12Month(request.pd12Month());
        parameter.setPdLifetime(request.pdLifetime());
        parameter.setLgd(request.lgd());
        parameter.setUpdatedBy(this.actor.userId(command));
        parameter.setUpdatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.parameterRepository.saveAndFlush(parameter);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("created", created);
        changes.put("ratingCode", parameter.getRatingCode());
        changes.put("stage", parameter.getStage());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(parameter.getId()).with(changes)
                .build();
    }

    @Transactional(readOnly = true)
    public List<TreasuryEclParameterData> retrieveParameters() {
        return this.parameterRepository.findAllByOrderByRatingCodeAscStageAsc().stream().map(TreasuryEclParameterData::from).toList();
    }

    // ---------- commands ----------

    /**
     * Moves the deal to a stage (with any manual PD, LGD or ECL) and recalculates its allowance on the business date.
     */
    @Transactional
    public CommandProcessingResult setStage(final Long dealId, final JsonCommand command) {
        final EclStageRequest request = this.validator.validateEclStage(command.json());
        final TreasuryDeal deal = eligibleDeal(dealId);
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        requireAccounts(type, deal.getClassification());
        final Long userId = this.actor.userId(command);
        final int previous = deal.getEclStage();
        if (request.clearOverrides()) {
            deal.setEclManualPd(null);
            deal.setEclManualLgd(null);
            deal.setEclManualAmount(null);
            deal.setEclManualReason(null);
        }
        if (request.manualPd() != null) {
            deal.setEclManualPd(request.manualPd());
        }
        if (request.manualLgd() != null) {
            deal.setEclManualLgd(request.manualLgd());
        }
        if (request.manualEcl() != null) {
            deal.setEclManualAmount(request.manualEcl());
            deal.setEclManualReason(request.manualEclReason());
        }
        deal.setEclStage(request.stage());
        deal.setEclStageReason(request.reason());
        deal.setEclStageDate(DateUtils.getBusinessLocalDate());
        this.dealRepository.saveAndFlush(deal);
        DealEvents.record(this.eventRepository, dealId, TreasuryDealEventType.ECL_STAGE_CHANGED, userId,
                "Stage " + previous + " to " + request.stage() + ": " + request.reason() + overrides(deal), null);
        final Calculation calculation = calculate(deal, type, DateUtils.getBusinessLocalDate(), userId, TRIGGER_STAGE_CHANGE);
        final Map<String, Object> changes = changes(deal, calculation);
        changes.put("previousStage", previous);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId).withOfficeId(deal.getOfficeId())
                .with(changes).build();
    }

    @Transactional
    public CommandProcessingResult recalculate(final Long dealId, final JsonCommand command) {
        this.validator.validateRecalculateEcl(command.json());
        final TreasuryDeal deal = eligibleDeal(dealId);
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        requireAccounts(type, deal.getClassification());
        final Calculation calculation = calculate(deal, type, DateUtils.getBusinessLocalDate(), this.actor.userId(command),
                TRIGGER_ON_DEMAND);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId).withOfficeId(deal.getOfficeId())
                .with(changes(deal, calculation)).build();
    }

    // ---------- the job ----------

    public RunSummary runForClosedDay() {
        return run(DateUtils.getBusinessLocalDate().minusDays(1));
    }

    /**
     * Recalculates every eligible settled deal as at the date; a deal already calculated on that date is left alone.
     */
    public RunSummary run(final LocalDate date) {
        final TransactionTemplate perDeal = new TransactionTemplate(this.transactionManager);
        perDeal.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        final List<Long> ids = new TransactionTemplate(this.transactionManager).execute(
                status -> this.dealRepository.findByStatusOrderByIdAsc(DealStatus.SETTLED).stream().map(TreasuryDeal::getId).toList());
        final Long systemUserId = new TransactionTemplate(this.transactionManager)
                .execute(status -> this.appUserRepository.fetchSystemUser().getId());
        int calculated = 0;
        int skipped = 0;
        for (final Long dealId : ids) {
            try {
                final JobOutcome outcome = perDeal.execute(status -> calculateForJob(dealId, date, systemUserId));
                if (outcome == JobOutcome.CALCULATED) {
                    calculated++;
                } else if (outcome == JobOutcome.SKIPPED) {
                    skipped++;
                }
            } catch (RuntimeException e) {
                skipped++;
                log.error("Treasury ECL skipped deal {} for {}: {}", dealId, date, e.getMessage());
            }
        }
        log.info("Treasury ECL for {}: {} settled deals, {} calculated, {} skipped", date, ids.size(), calculated, skipped);
        return new RunSummary(date, ids.size(), calculated, skipped);
    }

    private JobOutcome calculateForJob(final Long dealId, final LocalDate date, final Long userId) {
        final TreasuryDeal deal = find(dealId);
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        if (!eligible(type.getInstrumentKind(), deal.getClassification()) || date.equals(deal.getEclCalculatedOnDate())) {
            return JobOutcome.NOT_DUE;
        }
        if (!hasAccounts(type, deal.getClassification())) {
            log.warn("Treasury ECL skipped deal {}: deal type {} has no impairment accounts", dealId, type.getCode());
            return JobOutcome.SKIPPED;
        }
        if (resolve(deal) == null) {
            DealEvents.record(this.eventRepository, dealId, TreasuryDealEventType.ECL_SKIPPED, userId,
                    "No ECL parameters for rating " + ratingKey(deal) + " (nor DEFAULT) at stage " + deal.getEclStage(), null);
            return JobOutcome.SKIPPED;
        }
        calculate(deal, type, date, userId, TRIGGER_JOB);
        return JobOutcome.CALCULATED;
    }

    // ---------- calculation ----------

    Calculation calculate(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate date, final Long userId,
            final String trigger) {
        final int scale = this.referenceDataLookup.decimalPlaces(deal.getCurrencyCode());
        final BigDecimal exposure = DealLifecyclePlanner.carrying(deal, type.getInstrumentKind()).assetTotal().max(BigDecimal.ZERO)
                .setScale(scale, RoundingMode.HALF_UP);
        final Basis basis = resolve(deal);
        if (basis == null) {
            throw new TreasuryWorkflowException(
                    ENTITY, "ecl.parameters.missing", "No ECL parameters for rating " + ratingKey(deal) + " (nor DEFAULT) at stage "
                            + deal.getEclStage() + ", and no manual PD and LGD or ECL on deal " + deal.getId(),
                    deal.getId(), deal.getEclStage());
        }
        final BigDecimal ecl = basis.manualEcl() != null ? basis.manualEcl().setScale(scale, RoundingMode.HALF_UP)
                : EclCalculator.ecl(exposure, basis.pd(), basis.lgd(), scale);
        return post(deal, type, date, userId, trigger, exposure, basis, ecl);
    }

    /** When the deal matures or is terminated: the allowance is reversed through impairment. */
    void release(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate date, final Long userId) {
        if (deal.getEclAllowance().signum() == 0) {
            return;
        }
        post(deal, type, date, userId, TRIGGER_RELEASE, BigDecimal.ZERO, new Basis(null, null, null, TRIGGER_RELEASE), BigDecimal.ZERO);
    }

    private Calculation post(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate date, final Long userId,
            final String trigger, final BigDecimal exposure, final Basis basis, final BigDecimal ecl) {
        final BigDecimal change = ecl.subtract(deal.getEclAllowance());
        final int sequence = (int) this.eclRepository.countByDealId(deal.getId()) + 1;
        String transactionId = null;
        if (change.signum() != 0) {
            transactionId = this.journalPoster.post(TreasuryEventType.DEAL_IMPAIRMENT, deal.getId(),
                    "TR-DE-" + deal.getId() + "-" + sequence, deal.getCurrencyCode(), date,
                    "Treasury deal " + deal.getId()
                            + (TRIGGER_RELEASE.equals(trigger) ? " loss allowance released"
                                    : " expected credit loss, stage " + deal.getEclStage()),
                    deal.getExternalReference(), DealValuationPlanner.impairment(deal.getClassification(), change, deal.getOfficeId(),
                            TreasuryValuationService.gl(type)));
        }
        final TreasuryDealEcl row = new TreasuryDealEcl();
        row.setDealId(deal.getId());
        row.setSequenceNumber(sequence);
        row.setCalculationDate(date);
        row.setStage(deal.getEclStage());
        row.setTriggerType(trigger);
        row.setExposure(exposure);
        row.setPd(basis.pd());
        row.setLgd(basis.lgd());
        row.setBasis(basis.source());
        row.setEcl(ecl);
        row.setEclChange(change);
        row.setTransactionId(transactionId);
        row.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.eclRepository.save(row);
        deal.setEclAllowance(ecl);
        deal.setEclCalculatedOnDate(date);
        this.dealRepository.saveAndFlush(deal);
        if (change.signum() != 0 || !TRIGGER_JOB.equals(trigger)) {
            DealEvents.record(this.eventRepository, deal.getId(),
                    TRIGGER_RELEASE.equals(trigger) ? TreasuryDealEventType.ECL_RELEASED : TreasuryDealEventType.ECL_CALCULATED, userId,
                    TRIGGER_RELEASE.equals(trigger) ? "Loss allowance of " + change.negate().toPlainString() + " released"
                            : "Stage " + deal.getEclStage() + ", exposure " + exposure.toPlainString() + ", " + describe(basis) + ": ECL "
                                    + ecl.toPlainString() + " (change " + change.toPlainString() + ")",
                    transactionId);
        }
        return new Calculation(exposure, ecl, change, transactionId);
    }

    /** PD and LGD of the deal: its manual ones, else the parameter row of its rating and stage, else of DEFAULT. */
    Basis resolve(final TreasuryDeal deal) {
        if (deal.getEclManualAmount() != null) {
            return new Basis(null, null, deal.getEclManualAmount(), "MANUAL_ECL");
        }
        final Optional<TreasuryEclParameter> parameter = this.parameterRepository
                .findByRatingCodeAndStage(ratingKey(deal), deal.getEclStage())
                .or(() -> this.parameterRepository.findByRatingCodeAndStage(TreasuryEclParameter.DEFAULT_RATING, deal.getEclStage()));
        final BigDecimal pd = deal.getEclManualPd() != null ? deal.getEclManualPd()
                : parameter.map(p -> EclCalculator.pd(deal.getEclStage(), p.getPd12Month(), p.getPdLifetime())).orElse(null);
        final BigDecimal lgd = deal.getEclManualLgd() != null ? deal.getEclManualLgd()
                : parameter.map(TreasuryEclParameter::getLgd).orElse(null);
        if (pd == null || lgd == null) {
            return null;
        }
        final String source = (deal.getEclManualPd() != null || deal.getEclManualLgd() != null ? "MANUAL" : "")
                + (parameter.isPresent() && (deal.getEclManualPd() == null || deal.getEclManualLgd() == null)
                        ? (deal.getEclManualPd() != null || deal.getEclManualLgd() != null ? "+" : "") + parameter.get().getRatingCode()
                        : "");
        return new Basis(pd, lgd, null, source);
    }

    /**
     * The counterparty's rating as the parameter table names it: its rating code value, else its rating text, upper
     * case.
     */
    String ratingKey(final TreasuryDeal deal) {
        final TreasuryCounterparty counterparty = this.counterpartyRepository.findById(deal.getCounterpartyId()).orElse(null);
        if (counterparty == null) {
            return TreasuryEclParameter.DEFAULT_RATING;
        }
        final String rating = counterparty.getRatingCodeValueId() != null
                ? this.referenceDataLookup.codeValueName(counterparty.getRatingCodeValueId()).orElse(counterparty.getRating())
                : counterparty.getRating();
        return StringUtils.isBlank(rating) ? TreasuryEclParameter.DEFAULT_RATING : rating.trim().toUpperCase(Locale.ROOT);
    }

    static boolean hasAccounts(final TreasuryDealType type, final DealClassification classification) {
        return type.getImpairmentExpenseGlAccountId() != null
                && (classification == DealClassification.FVOCI ? type.getFairValueReserveGlAccountId() != null
                        : type.getEclAllowanceGlAccountId() != null);
    }

    private static void requireAccounts(final TreasuryDealType type, final DealClassification classification) {
        if (!hasAccounts(type, classification)) {
            throw new TreasuryWorkflowException(ENTITY, "ecl.gl.account.missing",
                    "Deal type " + type.getCode() + " needs impairmentExpenseGlAccountId and "
                            + (classification == DealClassification.FVOCI ? "fairValueReserveGlAccountId" : "eclAllowanceGlAccountId")
                            + " for expected credit losses",
                    type.getId());
        }
    }

    private TreasuryDeal eligibleDeal(final Long dealId) {
        final TreasuryDeal deal = find(dealId);
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        if (deal.getStatus() != DealStatus.SETTLED || !eligible(type.getInstrumentKind(), deal.getClassification())) {
            throw new TreasuryWorkflowException(ENTITY, "ecl.not.applicable",
                    "Expected credit losses apply to settled asset deals at " + "amortised cost or FVOCI; deal " + dealId + " is a "
                            + deal.getStatus() + " " + deal.getClassification() + " " + type.getInstrumentKind(),
                    dealId);
        }
        return deal;
    }

    private static Map<String, Object> changes(final TreasuryDeal deal, final Calculation calculation) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("stage", deal.getEclStage());
        changes.put("exposure", calculation.exposure());
        changes.put("ecl", calculation.ecl());
        changes.put("eclChange", calculation.change());
        changes.put("transactionId", calculation.transactionId());
        if (deal.getEclStage() == 3) {
            changes.put("stage3InterestOnGross", true);
        }
        return changes;
    }

    private static String overrides(final TreasuryDeal deal) {
        final StringBuilder text = new StringBuilder();
        if (deal.getEclManualPd() != null) {
            text.append("; manual PD ").append(deal.getEclManualPd().toPlainString()).append('%');
        }
        if (deal.getEclManualLgd() != null) {
            text.append("; manual LGD ").append(deal.getEclManualLgd().toPlainString()).append('%');
        }
        if (deal.getEclManualAmount() != null) {
            text.append("; manual ECL ").append(deal.getEclManualAmount().toPlainString()).append(" (").append(deal.getEclManualReason())
                    .append(')');
        }
        return text.toString();
    }

    private static String describe(final Basis basis) {
        return basis.manualEcl() != null ? "manual ECL"
                : "PD " + basis.pd().toPlainString() + "%, LGD " + basis.lgd().toPlainString() + "% (" + basis.source() + ")";
    }

    private TreasuryDeal find(final Long dealId) {
        return this.dealRepository.findById(dealId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury deal " + dealId + " does not exist", dealId));
    }
}
