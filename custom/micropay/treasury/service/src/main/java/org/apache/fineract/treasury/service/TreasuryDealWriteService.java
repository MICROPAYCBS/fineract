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
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.AMORTISING_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.AMOUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.APPROVE_OVERLIMIT_PERMISSION;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CLASSIFICATION_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CLEAN_PRICE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COUNTERPARTY_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DAY_COUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEAL_TYPE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.EXTERNAL_REFERENCE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FREQUENCY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ISIN_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ISSUE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.MATURITY_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.NOTES_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.RATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ROLLOVER_PRE_APPROVED_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.SETTLEMENT_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.TRADE_DATE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.VALUE_DATE_PARAM;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryDealPreviewData;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealPricer;
import org.apache.fineract.treasury.deal.DealPricer.Priced;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.FeeMethod;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealCashflow;
import org.apache.fineract.treasury.domain.TreasuryDealCashflowRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEvent;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.ActionRequest;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.Request;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.Terms;
import org.apache.fineract.treasury.service.DealSettlementPlanner.DealGlAccounts;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal capture and lifecycle: CAPTURED, APPROVED, CONFIRMED, SETTLED; or REJECTED, or CANCELLED before settlement. No
 * user takes two steps in a row (capturer or last editor, approver, confirmer, settler). Settlement is the only step
 * that posts; accrual, maturity, early termination and rollover come later.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryDealWriteService {

    static final String ENTITY = "deal";

    private final TreasuryActor actor;
    private final TreasuryDealDataValidator validator;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealCashflowRepository cashflowRepository;
    private final TreasuryDealEventRepository eventRepository;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryCounterpartyService counterpartyService;
    private final TreasurySettingService settingService;
    private final TreasuryBalanceReader balanceReader;
    private final TreasuryJournalPoster journalPoster;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final AppUserRepository appUserRepository;
    private final TreasuryLimitEngine limitEngine;

    // ---------- capture and edit ----------

    @Transactional
    public CommandProcessingResult capture(final JsonCommand command) {
        final Request request = this.validator.validateDeal(command.json(), true);
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setStatus(DealStatus.CAPTURED);
        deal.setFees(BigDecimal.ZERO);
        deal.setClassification(DealClassification.AMORTISED_COST);
        deal.setCapturedBy(this.actor.userId(command));
        apply(deal, request);
        final Map<String, Object> changes = new LinkedHashMap<>();
        final List<Flow> flows = price(deal);
        final LimitCheck.Result limit = checkLimit(deal, changes, deal.getCapturedBy());
        this.dealRepository.saveAndFlush(deal);
        saveFlows(deal, flows);
        event(deal, TreasuryDealEventType.CAPTURED, deal.getCapturedBy(), null, null);
        limitEvent(deal, limit, deal.getCapturedBy());
        changes.put("status", deal.getStatus().name());
        changes.put("consideration", deal.getConsideration());
        return result(command, deal, changes);
    }

    /**
     * The deal as it would be captured, without saving it or recording anything: validated, priced and scheduled the
     * way {@link #capture} does, with the counterparty limit check. A deal above the limit is shown, not refused.
     */
    @Transactional(readOnly = true)
    public TreasuryDealPreviewData preview(final String json) {
        final Request request = this.validator.validateDeal(json, true);
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setStatus(DealStatus.CAPTURED);
        deal.setFees(BigDecimal.ZERO);
        deal.setClassification(DealClassification.AMORTISED_COST);
        apply(deal, request);
        final List<Flow> flows = price(deal);
        TreasuryDealPreviewData.Limit limit = null;
        if (deal.getExposureAmount() != null && deal.getExposureAmount().signum() != 0) {
            final TreasuryCounterparty counterparty = this.counterpartyService.find(deal.getCounterpartyId());
            final LimitCheck.Result result = this.counterpartyService.check(counterparty, deal);
            limit = new TreasuryDealPreviewData.Limit(result.outcome().name(), result.exposureAfter(), result.utilisation(),
                    result.available(), counterparty.getExposureLimit(), counterparty.getWarningPercent(),
                    this.settingService.settingsFor(deal.getCurrencyCode()).isAllowLimitReferral());
        }
        return new TreasuryDealPreviewData(deal.getCurrencyCode(), deal.getOfficeId(),
                deal.getDayCount() == null ? null : deal.getDayCount().name(), deal.getFrequency(), deal.getConsideration(),
                deal.getAccruedInterestPurchased(), deal.getPremium(), deal.getDiscount(), deal.getExposureAmount(),
                flows.stream().map(f -> new TreasuryDealPreviewData.Cashflow(f.sequence(), f.type().name(), f.periodStart(), f.periodEnd(),
                        f.dueDate(), f.amount(), f.outstanding())).toList(),
                limit);
    }

    /** Only a CAPTURED deal can be edited; the cash flows are generated again and the limit checked again. */
    @Transactional
    public CommandProcessingResult update(final Long dealId, final JsonCommand command) {
        final TreasuryDeal deal = find(dealId);
        requireStatus(deal, deal.getStatus().canUpdate(), "edited");
        final Request request = this.validator.validateDeal(command.json(), false);
        apply(deal, request);
        final Long userId = this.actor.userId(command);
        deal.setLastEditedBy(userId);
        final Map<String, Object> changes = new LinkedHashMap<>();
        final List<Flow> flows = price(deal);
        final LimitCheck.Result limit = checkLimit(deal, changes, userId);
        this.dealRepository.saveAndFlush(deal);
        this.cashflowRepository.deleteByDealId(dealId);
        saveFlows(deal, flows);
        event(deal, TreasuryDealEventType.UPDATED, userId, null, null);
        limitEvent(deal, limit, userId);
        changes.put("consideration", deal.getConsideration());
        return result(command, deal, changes);
    }

    /**
     * Captures a deal built by a rollover: priced, limit-checked, scheduled and recorded like any capture. A deal over
     * the limit is refused or flagged as at capture.
     */
    TreasuryDeal captureRolledDeal(final TreasuryDeal draft, final Long userId, final Map<String, Object> changes) {
        draft.setStatus(DealStatus.CAPTURED);
        draft.setCapturedBy(userId);
        final List<Flow> flows = price(draft);
        final LimitCheck.Result limit = checkLimit(draft, changes, userId);
        this.dealRepository.saveAndFlush(draft);
        saveFlows(draft, flows);
        event(draft, TreasuryDealEventType.CAPTURED, userId, "Rolled over from deal " + draft.getRolledFromDealId(), null);
        limitEvent(draft, limit, userId);
        return draft;
    }

    /** Records a pre-approved rollover: the new deal carries the original approval. */
    void approveRolledDeal(final TreasuryDeal deal, final Long approvedBy, final String reason) {
        deal.setStatus(DealStatus.APPROVED);
        deal.setApprovedBy(approvedBy);
        deal.setApprovedOnDate(DateUtils.getBusinessLocalDate());
        this.dealRepository.saveAndFlush(deal);
        event(deal, TreasuryDealEventType.APPROVED, approvedBy, reason, null);
    }

    // ---------- lifecycle ----------

    /**
     * The approver may not be the capturer or the last editor. The limit is checked again; a deal above it needs
     * {@code APPROVE_TREASURYDEAL_OVERLIMIT} as well.
     */
    @Transactional
    public CommandProcessingResult approve(final Long dealId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(command.json());
        final TreasuryDeal deal = find(dealId);
        requireStatus(deal, deal.getStatus().canApprove(), "approved");
        final Long userId = this.actor.userId(command);
        notCapturer(deal, userId, "approve");
        final Map<String, Object> changes = new LinkedHashMap<>();
        final LimitCheck.Result limit = checkLimit(deal, changes, userId);
        if (deal.isLimitExceeded()) {
            final AppUser approver = this.appUserRepository.findById(userId).orElseThrow();
            if (!approver.hasAnyPermission("ALL_FUNCTIONS", APPROVE_OVERLIMIT_PERMISSION)) {
                throw new TreasuryWorkflowException(ENTITY, "overlimit.approval.not.permitted", "Deal " + dealId
                        + " is above its counterparty's limit and needs " + APPROVE_OVERLIMIT_PERMISSION + " to be approved", dealId);
            }
        }
        deal.setStatus(DealStatus.APPROVED);
        deal.setApprovedBy(userId);
        deal.setApprovedOnDate(DateUtils.getBusinessLocalDate());
        this.dealRepository.saveAndFlush(deal);
        limitEvent(deal, limit, userId);
        event(deal, TreasuryDealEventType.APPROVED, userId, action.note(), null);
        changes.put("status", deal.getStatus().name());
        return result(command, deal, changes);
    }

    @Transactional
    public CommandProcessingResult reject(final Long dealId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(command.json());
        final TreasuryDeal deal = find(dealId);
        requireStatus(deal, deal.getStatus().canReject(), "rejected");
        final Long userId = this.actor.userId(command);
        notCapturer(deal, userId, "reject");
        close(deal, DealStatus.REJECTED, userId, requireReason(action, dealId, "reject"));
        event(deal, TreasuryDealEventType.REJECTED, userId, action.reason(), null);
        return result(command, deal, Map.of("status", deal.getStatus().name()));
    }

    /** The counterparty's confirmation reference is recorded; the confirmer may not be the approver. */
    @Transactional
    public CommandProcessingResult confirm(final Long dealId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(command.json());
        final TreasuryDeal deal = find(dealId);
        requireStatus(deal, deal.getStatus().canConfirm(), "confirmed");
        if (action.confirmationReference() == null) {
            throw new TreasuryWorkflowException(ENTITY, "confirmation.reference.required",
                    "The counterparty's confirmation reference is required to confirm deal " + dealId, dealId);
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, dealId, "approved", deal.getApprovedBy(), "confirm", userId);
        deal.setStatus(DealStatus.CONFIRMED);
        deal.setConfirmedBy(userId);
        deal.setConfirmedOnDate(DateUtils.getBusinessLocalDate());
        deal.setConfirmationReference(action.confirmationReference());
        this.dealRepository.saveAndFlush(deal);
        event(deal, TreasuryDealEventType.CONFIRMED, userId, action.note() == null ? action.confirmationReference() : action.note(), null);
        return result(command, deal, Map.of("status", deal.getStatus().name()));
    }

    @Transactional
    public CommandProcessingResult cancel(final Long dealId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(command.json());
        final TreasuryDeal deal = find(dealId);
        if (!deal.getStatus().canCancel()) {
            throw new TreasuryWorkflowException(ENTITY, "cannot.cancel",
                    "Deal " + dealId + " is " + deal.getStatus() + "; only a deal not yet settled can be cancelled", dealId);
        }
        final Long userId = this.actor.userId(command);
        close(deal, DealStatus.CANCELLED, userId, requireReason(action, dealId, "cancel"));
        event(deal, TreasuryDealEventType.CANCELLED, userId, action.reason(), null);
        return result(command, deal, Map.of("status", deal.getStatus().name()));
    }

    /**
     * Moves the cash on the value date, which may not be after the business date. Asset deals need the settlement
     * account's GL balance to cover the consideration and fees unless the currency's settings allow otherwise.
     */
    @Transactional
    public CommandProcessingResult settle(final Long dealId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(command.json());
        final TreasuryDeal deal = find(dealId);
        requireStatus(deal, deal.getStatus().canSettle(), "settled");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, dealId, "confirmed", deal.getConfirmedBy(), "settle", userId);
        if (DateUtils.isAfterBusinessDate(deal.getValueDate())) {
            throw new TreasuryWorkflowException(ENTITY, "value.date.in.future", "Deal " + dealId + " has value date " + deal.getValueDate()
                    + ", after the business date " + DateUtils.getBusinessLocalDate(), dealId, deal.getValueDate());
        }
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        final TreasuryAccount account = activeAccount(deal.getSettlementAccountId());
        final Priced priced = priced(deal, type.getInstrumentKind());
        requireAccounts(type, priced, deal.getFees());
        final BigDecimal cashOut = DealSettlementPlanner.cashOut(type.getInstrumentKind(), priced, deal.getFees());
        if (cashOut.signum() > 0 && !this.settingService.settingsFor(deal.getCurrencyCode()).isAllowInsufficientBalance()) {
            final BigDecimal balance = this.balanceReader.glBalances(List.of(account.getId()), null).getOrDefault(account.getId(),
                    BigDecimal.ZERO);
            if (balance.compareTo(cashOut) < 0) {
                throw new TreasuryWorkflowException(ENTITY, "insufficient.balance",
                        "Treasury account " + account.getCode() + " has " + deal.getCurrencyCode() + " " + balance.toPlainString()
                                + ", less than the " + cashOut.toPlainString() + " the settlement needs",
                        account.getId(), balance, cashOut);
            }
        }
        final String transactionId = this.journalPoster.post(TreasuryEventType.DEAL_SETTLEMENT, dealId, deal.getCurrencyCode(),
                deal.getValueDate(), description(deal, type), deal.getExternalReference(),
                DealSettlementPlanner.legs(type.getInstrumentKind(), priced, deal.getFees(), account.getOfficeId(),
                        account.getGlAccountId(),
                        new DealGlAccounts(type.getPrincipalGlAccountId(), type.getInterestReceivableGlAccountId(),
                                type.getPremiumGlAccountId(), type.getDiscountGlAccountId(), feeAccount(type))));
        deal.setStatus(DealStatus.SETTLED);
        deal.setSettledBy(userId);
        deal.setSettledOnDate(DateUtils.getBusinessLocalDate());
        deal.setSettlementTransactionId(transactionId);
        deal.setAccruedToDate(deal.getValueDate());
        deal.setFeeDeferred(type.getFeeMethod() == FeeMethod.AMORTISE ? deal.getFees() : BigDecimal.ZERO);
        this.dealRepository.saveAndFlush(deal);
        event(deal, TreasuryDealEventType.SETTLED, userId, action.note(), transactionId);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", deal.getStatus().name());
        changes.put("transactionId", transactionId);
        return result(command, deal, changes);
    }

    // ---------- helpers ----------

    private void apply(final TreasuryDeal deal, final Request request) {
        if (request.has(DEAL_TYPE_ID_PARAM)) {
            deal.setDealTypeId(request.get(DEAL_TYPE_ID_PARAM));
        }
        if (request.has(COUNTERPARTY_ID_PARAM)) {
            deal.setCounterpartyId(request.get(COUNTERPARTY_ID_PARAM));
        }
        if (request.has(SETTLEMENT_ACCOUNT_ID_PARAM)) {
            deal.setSettlementAccountId(request.get(SETTLEMENT_ACCOUNT_ID_PARAM));
        }
        if (request.has(EXTERNAL_REFERENCE_PARAM)) {
            deal.setExternalReference(request.get(EXTERNAL_REFERENCE_PARAM));
        }
        if (request.has(ISIN_PARAM)) {
            deal.setIsin(request.get(ISIN_PARAM));
        }
        if (request.has(TRADE_DATE_PARAM)) {
            deal.setTradeDate(request.get(TRADE_DATE_PARAM));
        }
        if (request.has(VALUE_DATE_PARAM)) {
            deal.setValueDate(request.get(VALUE_DATE_PARAM));
        }
        if (request.has(MATURITY_DATE_PARAM)) {
            deal.setMaturityDate(request.get(MATURITY_DATE_PARAM));
        }
        if (request.has(ISSUE_DATE_PARAM)) {
            deal.setIssueDate(request.get(ISSUE_DATE_PARAM));
        }
        if (request.has(AMOUNT_PARAM)) {
            deal.setAmount(request.get(AMOUNT_PARAM));
        }
        if (request.has(RATE_PARAM)) {
            deal.setRate(request.get(RATE_PARAM));
        }
        if (request.has(CLEAN_PRICE_PARAM)) {
            deal.setCleanPrice(request.get(CLEAN_PRICE_PARAM));
        }
        if (request.has(DAY_COUNT_PARAM)) {
            deal.setDayCount(request.get(DAY_COUNT_PARAM));
        }
        if (request.has(FREQUENCY_PARAM) && request.get(FREQUENCY_PARAM) != null) {
            deal.setFrequency(request.<Integer>get(FREQUENCY_PARAM));
        }
        if (request.has(AMORTISING_PARAM) && request.get(AMORTISING_PARAM) != null) {
            deal.setAmortising(request.<Boolean>get(AMORTISING_PARAM));
        }
        if (request.has(FEES_PARAM)) {
            deal.setFees(request.get(FEES_PARAM) == null ? BigDecimal.ZERO : request.get(FEES_PARAM));
        }
        if (request.has(CLASSIFICATION_PARAM) && request.get(CLASSIFICATION_PARAM) != null) {
            deal.setClassification(request.get(CLASSIFICATION_PARAM));
        }
        if (request.has(ROLLOVER_PRE_APPROVED_PARAM) && request.get(ROLLOVER_PRE_APPROVED_PARAM) != null) {
            deal.setRolloverPreApproved(request.<Boolean>get(ROLLOVER_PRE_APPROVED_PARAM));
        }
        if (request.has(NOTES_PARAM)) {
            deal.setNotes(request.get(NOTES_PARAM));
        }
        // a new deal, or one moved to another type, takes the type's frequency unless it gives one (-1: not given)
        if (!request.has(FREQUENCY_PARAM) && (deal.getId() == null || request.has(DEAL_TYPE_ID_PARAM))) {
            deal.setFrequency(-1);
        }
    }

    /**
     * Checks the references and terms, then prices the deal and returns its schedule. A missing day count, and a
     * frequency left at -1 by {@link #apply}, come from the deal type.
     */
    private List<Flow> price(final TreasuryDeal deal) {
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        if (!type.isActive()) {
            throw new TreasuryWorkflowException(ENTITY, "deal.type.inactive", "Deal type " + type.getCode() + " is inactive", type.getId());
        }
        this.counterpartyService.active(deal.getCounterpartyId());
        final TreasuryAccount account = activeAccount(deal.getSettlementAccountId());
        deal.setCurrencyCode(account.getCurrencyCode());
        deal.setOfficeId(account.getOfficeId());
        final InstrumentKind kind = type.getInstrumentKind();
        if (deal.getDayCount() == null) {
            deal.setDayCount(type.getDefaultDayCount());
        }
        if (deal.getFrequency() < 0) {
            deal.setFrequency(kind == InstrumentKind.TREASURY_BILL ? 0 : type.getDefaultFrequency());
        }
        if (DateUtils.isAfterBusinessDate(deal.getTradeDate())) {
            throw new TreasuryWorkflowException(ENTITY, "trade.date.in.future",
                    "The trade date " + deal.getTradeDate() + " is after the business date " + DateUtils.getBusinessLocalDate(),
                    deal.getTradeDate());
        }
        TreasuryDealDataValidator.validateTerms(new Terms(kind, deal.getTradeDate(), deal.getValueDate(), deal.getMaturityDate(),
                deal.getIssueDate(), deal.getAmount(), deal.getRate(), deal.getCleanPrice(), deal.getFrequency(), deal.isAmortising()));
        TreasuryDealTypeService.requireValuationAccounts(type, deal.getClassification());
        final Priced priced = priced(deal, kind);
        deal.setConsideration(priced.consideration());
        deal.setAccruedInterestPurchased(priced.accruedInterest());
        deal.setPremium(priced.premium());
        deal.setDiscount(priced.discount());
        deal.setExposureAmount(kind.isAsset() ? priced.exposureAmount() : BigDecimal.ZERO);
        return priced.flows();
    }

    private Priced priced(final TreasuryDeal deal, final InstrumentKind kind) {
        return DealPricer.price(new DealPricer.Terms(kind, deal.getAmount(), deal.getRate(), deal.getCleanPrice(), deal.getDayCount(),
                deal.getFrequency(), deal.isAmortising(), deal.getValueDate(), deal.getMaturityDate(), deal.getIssueDate(),
                this.referenceDataLookup.decimalPlaces(deal.getCurrencyCode())));
    }

    /**
     * Asset deals against the counterparty's limit: a warning from the warning percentage; above the limit refused, or
     * flagged for an over-limit approval when the currency's settings allow referral. A refusal is kept in the breach
     * log by the counterparty's active COUNTERPARTY_EXPOSURE limits, in a transaction of its own.
     */
    private LimitCheck.Result checkLimit(final TreasuryDeal deal, final Map<String, Object> changes, final Long userId) {
        if (deal.getExposureAmount() == null || deal.getExposureAmount().signum() == 0) {
            deal.setLimitExceeded(false);
            return null;
        }
        final TreasuryCounterparty counterparty = this.counterpartyService.find(deal.getCounterpartyId());
        final LimitCheck.Result result = this.counterpartyService.check(counterparty, deal);
        deal.setLimitExceeded(false);
        switch (result.outcome()) {
            case WARNING -> changes.put("warning", "Counterparty " + counterparty.getShortCode() + " would be at " + result.utilisation()
                    + "% of its limit, at or above the warning level of " + counterparty.getWarningPercent() + "%");
            case EXCEEDED -> {
                if (!this.settingService.settingsFor(deal.getCurrencyCode()).isAllowLimitReferral()) {
                    recordRefusal(counterparty, deal, result, userId);
                    throw new TreasuryWorkflowException(ENTITY, "limit.exceeded",
                            "The deal would take counterparty " + counterparty.getShortCode() + " to "
                                    + result.exposureAfter().toPlainString() + " " + deal.getCurrencyCode() + ", above its limit of "
                                    + counterparty.getExposureLimit().toPlainString(),
                            counterparty.getId(), result.exposureAfter(), counterparty.getExposureLimit());
                }
                deal.setLimitExceeded(true);
                changes.put("warning", "Counterparty " + counterparty.getShortCode() + " would be at " + result.utilisation()
                        + "% of its limit; the deal needs an over-limit approval");
            }
            default -> {
            }
        }
        changes.put("limitUtilisation", result.utilisation());
        changes.put("limitExceeded", deal.isLimitExceeded());
        return result;
    }

    /**
     * The deal's event at the warning level or above the limit, and the check through the counterparty's active
     * COUNTERPARTY_EXPOSURE limits into the breach log.
     */
    private void limitEvent(final TreasuryDeal deal, final LimitCheck.Result result, final Long userId) {
        if (result == null) {
            return;
        }
        this.limitEngine.dealChecked(this.counterpartyService.find(deal.getCounterpartyId()), deal, result, userId);
        if (result.outcome() == LimitCheck.Outcome.OK) {
            return;
        }
        event(deal,
                result.outcome() == LimitCheck.Outcome.EXCEEDED ? TreasuryDealEventType.LIMIT_EXCEEDED
                        : TreasuryDealEventType.LIMIT_WARNING,
                userId, "Limit utilisation " + result.utilisation() + "%, exposure " + result.exposureAfter().toPlainString(), null);
    }

    private void recordRefusal(final TreasuryCounterparty counterparty, final TreasuryDeal deal, final LimitCheck.Result result,
            final Long userId) {
        try {
            this.limitEngine.dealRefused(counterparty, deal, result, userId);
        } catch (RuntimeException e) {
            log.error("The refusal of a deal with counterparty {} could not be logged as a breach: {}", counterparty.getShortCode(),
                    e.getMessage());
        }
    }

    /** Fees are expensed, or deferred to be amortised by the accrual job. */
    private static Long feeAccount(final TreasuryDealType type) {
        return type.getFeeMethod() == FeeMethod.AMORTISE ? type.getDeferredFeeGlAccountId() : type.getFeeExpenseGlAccountId();
    }

    private static void requireAccounts(final TreasuryDealType type, final Priced priced, final BigDecimal fees) {
        if (priced.premium().signum() > 0 && type.getPremiumGlAccountId() == null
                || priced.discount().signum() > 0 && type.getDiscountGlAccountId() == null
                || priced.accruedInterest().signum() > 0 && type.getInterestReceivableGlAccountId() == null
                || fees != null && fees.signum() > 0 && feeAccount(type) == null) {
            throw new TreasuryWorkflowException(ENTITY, "deal.type.gl.account.missing",
                    "Deal type " + type.getCode()
                            + " lacks a GL account this settlement needs (premium, discount, interest receivable or fee expense)",
                    type.getId());
        }
    }

    private void notCapturer(final TreasuryDeal deal, final Long userId, final String step) {
        TreasurySegregation.requireDifferentUser(ENTITY, deal.getId(), "captured", deal.getCapturedBy(), step, userId);
        TreasurySegregation.requireDifferentUser(ENTITY, deal.getId(), "last edited", deal.getLastEditedBy(), step, userId);
    }

    private static String requireReason(final ActionRequest action, final Long dealId, final String step) {
        if (action.reason() == null) {
            throw new TreasuryWorkflowException(ENTITY, "reason.required", "A reason is required to " + step + " deal " + dealId, dealId);
        }
        return action.reason();
    }

    private void close(final TreasuryDeal deal, final DealStatus status, final Long userId, final String reason) {
        deal.setStatus(status);
        deal.setClosedBy(userId);
        deal.setClosedOnDate(DateUtils.getBusinessLocalDate());
        deal.setCloseReason(reason);
        this.dealRepository.saveAndFlush(deal);
    }

    private void saveFlows(final TreasuryDeal deal, final List<Flow> flows) {
        for (final Flow flow : flows) {
            final TreasuryDealCashflow cashflow = new TreasuryDealCashflow();
            cashflow.setDealId(deal.getId());
            cashflow.setSequenceNumber(flow.sequence());
            cashflow.setFlowType(flow.type());
            cashflow.setPeriodStart(flow.periodStart());
            cashflow.setPeriodEnd(flow.periodEnd());
            cashflow.setDueDate(flow.dueDate());
            cashflow.setAmount(flow.amount());
            cashflow.setPrincipalOutstanding(flow.outstanding());
            this.cashflowRepository.save(cashflow);
        }
    }

    private void event(final TreasuryDeal deal, final TreasuryDealEventType type, final Long userId, final String note,
            final String transactionId) {
        final TreasuryDealEvent event = new TreasuryDealEvent();
        event.setDealId(deal.getId());
        event.setEventType(type);
        event.setBusinessDate(DateUtils.getBusinessLocalDate());
        event.setUserId(userId);
        event.setNote(note);
        event.setTransactionId(transactionId);
        event.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.eventRepository.save(event);
    }

    private static void requireStatus(final TreasuryDeal deal, final boolean allowed, final String step) {
        if (!allowed) {
            throw new TreasuryWorkflowException(ENTITY, "invalid.status",
                    "Deal " + deal.getId() + " is " + deal.getStatus() + " and cannot be " + step, deal.getId(), deal.getStatus().name());
        }
    }

    private static String description(final TreasuryDeal deal, final TreasuryDealType type) {
        return "Treasury deal " + deal.getId() + " " + type.getCode() + " settled"
                + (deal.getExternalReference() == null ? "" : " (" + deal.getExternalReference() + ")");
    }

    TreasuryDeal find(final Long dealId) {
        return this.dealRepository.findById(dealId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury deal " + dealId + " does not exist", dealId));
    }

    private TreasuryAccount activeAccount(final Long accountId) {
        final TreasuryAccount account = this.accountRepository.findById(accountId)
                .orElseThrow(() -> new TreasuryAccountNotFoundException(accountId));
        if (!account.isActive()) {
            throw new TreasuryWorkflowException(ENTITY, "settlement.account.closed", "Treasury account " + account.getCode() + " is closed",
                    accountId);
        }
        return account;
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryDeal deal, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(deal.getId())
                .withOfficeId(deal.getOfficeId()).with(new LinkedHashMap<>(changes)).build();
    }
}
