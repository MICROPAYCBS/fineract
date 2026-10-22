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
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.deal.CashflowStatus;
import org.apache.fineract.treasury.deal.DealAccrualCalculator;
import org.apache.fineract.treasury.deal.DealAccrualCalculator.Targets;
import org.apache.fineract.treasury.deal.DealCalculator.Flow;
import org.apache.fineract.treasury.deal.DealCalculator.FlowType;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealAccrual;
import org.apache.fineract.treasury.domain.TreasuryDealAccrualRepository;
import org.apache.fineract.treasury.domain.TreasuryDealCashflow;
import org.apache.fineract.treasury.domain.TreasuryDealCashflowRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEvent;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryEventType;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.domain.TreasurySetting;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.RolloverRequest;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.SettleCashflowRequest;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.TerminateRequest;
import org.apache.fineract.treasury.service.DealLifecyclePlanner.Carrying;
import org.apache.fineract.treasury.service.DealLifecyclePlanner.Gl;
import org.apache.fineract.treasury.service.TreasuryPostingPlanner.Leg;
import org.apache.fineract.useradministration.domain.AppUserRepositoryWrapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A settled deal's life: daily accrual, cash flows on their due dates, maturity, early termination and rollover.
 *
 * <p>
 * The accrual job takes each settled deal up to the closed business day (the business date less one): it posts the
 * interest, amortisation and fee amortisation earned since the last run as one journal entry
 * ({@code TR-DA-<deal>-<n>}), then, unless the currency's settings say otherwise, settles the cash flows due by then
 * ({@code TR-DC-<flow>-<n>}). Each deal runs in its own transaction, so a closed GL period or a missing account skips
 * that deal (logged) without stopping the others. When every cash flow is settled the deal is MATURED (ROLLED_OVER if
 * it was rolled), and anything left of a premium, discount or deferred fee is released.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryDealLifecycleService {

    static final String ENTITY = "deal";

    private final TreasuryActor actor;
    private final TreasuryDealDataValidator validator;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealCashflowRepository cashflowRepository;
    private final TreasuryDealEventRepository eventRepository;
    private final TreasuryDealAccrualRepository accrualRepository;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryDealWriteService dealWriteService;
    private final TreasurySettingService settingService;
    private final TreasuryBalanceReader balanceReader;
    private final TreasuryJournalPoster journalPoster;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final AppUserRepositoryWrapper appUserRepository;
    private final PlatformTransactionManager transactionManager;

    public record RunSummary(LocalDate closedDay, int deals, int accrued, int cashflowsSettled, int matured, int skipped) {
    }

    // ---------- the job ----------

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
        int accrued = 0;
        int settled = 0;
        int matured = 0;
        int skipped = 0;
        for (final Long dealId : ids) {
            try {
                final int[] outcome = perDeal.execute(status -> process(dealId, closedDay, systemUserId));
                accrued += outcome[0];
                settled += outcome[1];
                matured += outcome[2];
            } catch (RuntimeException e) {
                skipped++;
                log.error("Treasury accrual skipped deal {} for {}: {}", dealId, closedDay, e.getMessage());
            }
        }
        log.info("Treasury accrual for {}: {} settled deals, {} accrued, {} cash flows settled, {} matured, {} skipped", closedDay,
                ids.size(), accrued, settled, matured, skipped);
        return new RunSummary(closedDay, ids.size(), accrued, settled, matured, skipped);
    }

    /** @return accrual posted (0/1), cash flows settled, matured (0/1) */
    private int[] process(final Long dealId, final LocalDate closedDay, final Long userId) {
        final TreasuryDeal deal = find(dealId);
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        // through the closed day: interest for the days before the day after it
        final int accrued = accrue(deal, type, closedDay.plusDays(1)) ? 1 : 0;
        int settled = 0;
        if (this.settingService.settingsFor(deal.getCurrencyCode()).isAutoSettleCashflows()) {
            settled = settleDue(deal, type, closedDay, userId);
        }
        final int matured = finishIfComplete(deal, type, userId) ? 1 : 0;
        return new int[] { accrued, settled, matured };
    }

    // ---------- accrual ----------

    /**
     * Posts what the deal earned since its last accrual for the days before {@code toDate} (capped at maturity), dated
     * the last of those days.
     *
     * @return whether anything was posted
     */
    boolean accrue(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate toDate) {
        final LocalDate from = deal.getAccruedToDate() == null ? deal.getValueDate() : deal.getAccruedToDate();
        final LocalDate to = toDate.isAfter(deal.getMaturityDate()) ? deal.getMaturityDate() : toDate;
        if (!to.isAfter(from)) {
            return false;
        }
        final Targets targets = DealAccrualCalculator.targets(calculatorDeal(deal, type), to);
        final BigDecimal interest = targets.interest().subtract(deal.getInterestAccrued());
        final BigDecimal amortisation = targets.amortisation().subtract(deal.getAmortisationAccrued());
        final BigDecimal fee = targets.feeAmortised().subtract(deal.getFeeAmortised());
        String transactionId = null;
        final int sequence = (int) this.accrualRepository.countByDealId(deal.getId()) + 1;
        if (interest.signum() != 0 || amortisation.signum() != 0 || fee.signum() != 0) {
            final List<Leg> legs = DealLifecyclePlanner.accrual(type.getInstrumentKind(), interest, amortisation, fee,
                    deal.getPremium().signum() > 0, deal.getOfficeId(), gl(deal, type));
            transactionId = this.journalPoster.post(TreasuryEventType.DEAL_ACCRUAL, deal.getId(), "TR-DA-" + deal.getId() + "-" + sequence,
                    deal.getCurrencyCode(), to.minusDays(1), "Treasury deal " + deal.getId() + " accrual " + from + " to " + to,
                    deal.getExternalReference(), legs);
        }
        final TreasuryDealAccrual accrual = new TreasuryDealAccrual();
        accrual.setDealId(deal.getId());
        accrual.setSequenceNumber(sequence);
        accrual.setFromDate(from);
        accrual.setToDate(to);
        accrual.setInterest(interest);
        accrual.setAmortisation(amortisation);
        accrual.setFeeAmortisation(fee);
        accrual.setTransactionId(transactionId);
        accrual.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.accrualRepository.save(accrual);
        deal.setInterestAccrued(targets.interest());
        deal.setAmortisationAccrued(targets.amortisation());
        deal.setFeeAmortised(targets.feeAmortised());
        deal.setAccruedToDate(to);
        this.dealRepository.saveAndFlush(deal);
        return transactionId != null;
    }

    DealAccrualCalculator.Deal calculatorDeal(final TreasuryDeal deal, final TreasuryDealType type) {
        final List<Flow> flows = this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(deal.getId()).stream()
                .map(f -> new Flow(f.getSequenceNumber(), f.getFlowType(), f.getPeriodStart(), f.getPeriodEnd(), f.getDueDate(),
                        f.getAmount(), f.getPrincipalOutstanding()))
                .toList();
        return new DealAccrualCalculator.Deal(type.getInstrumentKind(), deal.getAmount(), deal.getRate(), deal.getDayCount(),
                deal.getFrequency(), deal.getValueDate(), deal.getMaturityDate(), deal.getIssueDate(), deal.getConsideration(),
                deal.getAccruedInterestPurchased(), deal.getPremium(), deal.getDiscount(), deal.getFeeDeferred(),
                type.getAmortisationMethod(), deal.getClassification() != DealClassification.FVTPL, scale(deal), flows);
    }

    // ---------- cash flows ----------

    /**
     * Settles the open cash flows due by {@code upTo}; a payment the settlement account cannot cover is skipped and
     * recorded.
     */
    int settleDue(final TreasuryDeal deal, final TreasuryDealType type, final LocalDate upTo, final Long userId) {
        int settled = 0;
        for (final TreasuryDealCashflow flow : this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(deal.getId())) {
            if (!flow.getStatus().isOpen() || flow.getDueDate().isAfter(upTo) || flow.getDueDate().isAfter(accruedTo(deal))) {
                continue;
            }
            if (!type.getInstrumentKind().isAsset() && !covered(deal, flow.remaining())) {
                event(deal, TreasuryDealEventType.CASHFLOW_BREACH, userId,
                        "Cash flow " + flow.getId() + " of " + flow.remaining().toPlainString() + " due " + flow.getDueDate()
                                + " not paid: the settlement account cannot cover it",
                        null);
                continue;
            }
            settleFlow(deal, type, flow, flow.remaining(), flow.getDueDate(), userId);
            settled++;
        }
        return settled;
    }

    private void settleFlow(final TreasuryDeal deal, final TreasuryDealType type, final TreasuryDealCashflow flow, final BigDecimal amount,
            final LocalDate date, final Long userId) {
        final int count = flow.getSettlementCount() + 1;
        final String transactionId = this.journalPoster.post(TreasuryEventType.DEAL_CASHFLOW, deal.getId(),
                "TR-DC-" + flow.getId() + "-" + count, deal.getCurrencyCode(), date,
                "Treasury deal " + deal.getId() + " " + flow.getFlowType().name().toLowerCase() + " due " + flow.getDueDate(),
                deal.getExternalReference(),
                DealLifecyclePlanner.cashflow(type.getInstrumentKind(), flow.getFlowType(), amount, deal.getOfficeId(), gl(deal, type)));
        flow.setSettledAmount(flow.getSettledAmount().add(amount));
        flow.setSettlementCount(count);
        flow.setSettledOnDate(date);
        flow.setTransactionId(transactionId);
        flow.setStatus(flow.remaining().signum() == 0 ? CashflowStatus.SETTLED : CashflowStatus.PARTIAL);
        this.cashflowRepository.save(flow);
        if (flow.getFlowType() == FlowType.PRINCIPAL) {
            deal.setPrincipalSettled(deal.getPrincipalSettled().add(amount));
            deal.setExposureAmount(deal.getExposureAmount().subtract(amount).max(BigDecimal.ZERO));
        } else {
            deal.setInterestSettled(deal.getInterestSettled().add(amount));
        }
        this.dealRepository.saveAndFlush(deal);
        event(deal,
                flow.getStatus() == CashflowStatus.SETTLED ? TreasuryDealEventType.CASHFLOW_SETTLED
                        : TreasuryDealEventType.CASHFLOW_PARTIAL,
                userId, flow.getFlowType() + " " + amount.toPlainString() + " of cash flow " + flow.getId(), transactionId);
    }

    /**
     * When no cash flow is open the deal is MATURED (or ROLLED_OVER), and what is left of its premium, discount or
     * deferred fee is released on the maturity date.
     */
    boolean finishIfComplete(final TreasuryDeal deal, final TreasuryDealType type, final Long userId) {
        final boolean open = this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(deal.getId()).stream()
                .anyMatch(f -> f.getStatus().isOpen());
        if (open || deal.getAccruedToDate() == null || deal.getAccruedToDate().isBefore(deal.getMaturityDate())) {
            return false;
        }
        final Carrying carrying = carrying(deal, type);
        String transactionId = null;
        final List<Leg> legs = DealLifecyclePlanner.residual(carrying.unamortisedPremium(), carrying.unamortisedDiscount(),
                carrying.deferredFee(), deal.getOfficeId(), gl(deal, type));
        if (!legs.isEmpty()) {
            transactionId = this.journalPoster.post(TreasuryEventType.DEAL_ACCRUAL, deal.getId(), "TR-DA-" + deal.getId() + "-M",
                    deal.getCurrencyCode(), deal.getMaturityDate(), "Treasury deal " + deal.getId() + " residual released at maturity",
                    deal.getExternalReference(), legs);
            deal.setAmortisationAccrued(deal.getDiscount().subtract(deal.getPremium()));
            deal.setFeeAmortised(deal.getFeeDeferred());
        }
        deal.setStatus(deal.getRolledToDealId() == null ? DealStatus.MATURED : DealStatus.ROLLED_OVER);
        deal.setMaturedOnDate(deal.getMaturityDate());
        deal.setExposureAmount(BigDecimal.ZERO);
        this.dealRepository.saveAndFlush(deal);
        event(deal, deal.getStatus() == DealStatus.MATURED ? TreasuryDealEventType.MATURED : TreasuryDealEventType.ROLLED_OVER, userId,
                deal.getRolledToDealId() == null ? null : "Rolled into deal " + deal.getRolledToDealId(), transactionId);
        return true;
    }

    // ---------- commands ----------

    /**
     * Settles a cash flow by hand, on or after its due date: when the job does not settle cash flows, or the amount
     * paid differs. A smaller amount leaves the rest open and the flow PARTIAL.
     */
    @Transactional
    public CommandProcessingResult settleCashflow(final Long dealId, final JsonCommand command) {
        final SettleCashflowRequest request = this.validator.validateSettleCashflow(command.json());
        final TreasuryDeal deal = settled(dealId, "have a cash flow settled");
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        final TreasuryDealCashflow flow = this.cashflowRepository.findById(request.cashflowId()).filter(f -> f.getDealId().equals(dealId))
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "cashflow.not.found",
                        "Cash flow " + request.cashflowId() + " is not a cash flow of deal " + dealId, request.cashflowId()));
        if (!flow.getStatus().isOpen()) {
            throw new TreasuryWorkflowException(ENTITY, "cashflow.not.open", "Cash flow " + flow.getId() + " is " + flow.getStatus(),
                    flow.getId());
        }
        final LocalDate date = request.transactionDate() == null ? DateUtils.getBusinessLocalDate() : request.transactionDate();
        notAfterBusinessDate(date);
        if (date.isBefore(flow.getDueDate())) {
            throw new TreasuryWorkflowException(ENTITY, "cashflow.not.due",
                    "Cash flow " + flow.getId() + " falls due on " + flow.getDueDate() + ", after " + date, flow.getId(),
                    flow.getDueDate());
        }
        final BigDecimal amount = request.amount() == null ? flow.remaining() : request.amount();
        if (amount.compareTo(flow.remaining()) > 0) {
            throw new TreasuryWorkflowException(ENTITY, "cashflow.amount.too.large",
                    "Only " + flow.remaining().toPlainString() + " of cash flow " + flow.getId() + " is still open", flow.getId(),
                    flow.remaining());
        }
        final Long userId = this.actor.userId(command);
        accrue(deal, type, flow.getDueDate());
        if (!type.getInstrumentKind().isAsset()) {
            requireCovered(deal, amount);
        }
        settleFlow(deal, type, flow, amount, date, userId);
        final boolean matured = finishIfComplete(deal, type, userId);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("cashflowStatus", flow.getStatus().name());
        changes.put("remaining", flow.remaining());
        changes.put("transactionId", flow.getTransactionId());
        changes.put("status", deal.getStatus().name());
        if (matured) {
            changes.put("matured", true);
        }
        return result(command, deal, changes);
    }

    /**
     * Ends a settled deal early on a date from its last accrual up to the business date and before maturity: accrues to
     * that date, then takes the whole carrying amount off the books against the proceeds, the difference (and any break
     * cost) to realised gain or loss. Asset deals receive the proceeds; liability deals pay them (early repayment). The
     * user who settled the deal may not terminate it.
     */
    @Transactional
    public CommandProcessingResult terminate(final Long dealId, final JsonCommand command) {
        final TerminateRequest request = this.validator.validateTerminate(command.json());
        final TreasuryDeal deal = settled(dealId, "be terminated");
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        final LocalDate date = request.transactionDate() == null ? DateUtils.getBusinessLocalDate() : request.transactionDate();
        notAfterBusinessDate(date);
        final LocalDate accruedTo = deal.getAccruedToDate() == null ? deal.getValueDate() : deal.getAccruedToDate();
        if (date.isBefore(accruedTo) || !date.isBefore(deal.getMaturityDate())) {
            throw new TreasuryWorkflowException(
                    ENTITY, "termination.date.invalid", "Deal " + dealId + " can be terminated from " + accruedTo
                            + " (its last accrual) and before its maturity on " + deal.getMaturityDate() + ", not on " + date,
                    dealId, date);
        }
        if (type.getRealisedGainGlAccountId() == null || type.getRealisedLossGlAccountId() == null) {
            throw new TreasuryWorkflowException(ENTITY, "deal.type.gl.account.missing",
                    "Deal type " + type.getCode()
                            + " needs realisedGainGlAccountId and realisedLossGlAccountId before its deals can be terminated",
                    type.getId());
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, dealId, "settled", deal.getSettledBy(), "terminate", userId);
        accrue(deal, type, date);
        final BigDecimal breakCost = request.breakCost() == null ? BigDecimal.ZERO : request.breakCost();
        final BigDecimal cashOut = (type.getInstrumentKind().isAsset() ? BigDecimal.ZERO : request.proceeds()).add(breakCost);
        if (cashOut.signum() > 0) {
            requireCovered(deal, cashOut);
        }
        final DealLifecyclePlanner.Termination termination = DealLifecyclePlanner.termination(type.getInstrumentKind(),
                carrying(deal, type), request.proceeds(), breakCost, deal.getOfficeId(), gl(deal, type));
        final String transactionId = this.journalPoster.post(TreasuryEventType.DEAL_TERMINATION, dealId, deal.getCurrencyCode(), date,
                "Treasury deal " + dealId + " terminated: " + request.reason(), deal.getExternalReference(), termination.legs());
        for (final TreasuryDealCashflow flow : this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(dealId)) {
            if (flow.getStatus().isOpen()) {
                flow.setStatus(CashflowStatus.CANCELLED);
                this.cashflowRepository.save(flow);
            }
        }
        deal.setStatus(DealStatus.TERMINATED);
        deal.setTerminatedBy(userId);
        deal.setTerminatedOnDate(date);
        deal.setTerminationProceeds(request.proceeds());
        deal.setBreakCost(breakCost);
        deal.setRealisedResult(termination.realisedResult());
        deal.setCloseReason(request.reason());
        deal.setExposureAmount(BigDecimal.ZERO);
        this.dealRepository.saveAndFlush(deal);
        event(deal, TreasuryDealEventType.TERMINATED, userId, request.reason(), transactionId);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", deal.getStatus().name());
        changes.put("realisedResult", termination.realisedResult());
        changes.put("transactionId", transactionId);
        return result(command, deal, changes);
    }

    /**
     * Rolls a settled placement, deposit or borrowing into a new deal from its maturity date: same type, counterparty
     * and settlement account; the principal still outstanding, plus the interest due at maturity when capitalised. The
     * old deal matures as usual (paying in what it owes) and the new one settles on the old maturity date, so only the
     * part not rolled over moves cash. The new deal needs approval, unless the old one was captured with
     * {@code rolloverPreApproved}, the tenor is no longer and the amount no larger than the maturity proceeds, and it
     * is within the limit; it then starts APPROVED with the original approval.
     */
    @Transactional
    public CommandProcessingResult rollover(final Long dealId, final JsonCommand command) {
        final RolloverRequest request = this.validator.validateRollover(command.json());
        final TreasuryDeal old = settled(dealId, "be rolled over");
        final TreasuryDealType type = this.dealTypeService.find(old.getDealTypeId());
        if (type.getInstrumentKind().interestMethod() != InterestMethod.SIMPLE) {
            throw new TreasuryWorkflowException(ENTITY, "rollover.not.supported",
                    type.getInstrumentKind() + " deals are not rolled over; buy the new bill or bond as a new deal", dealId);
        }
        if (old.getRolledToDealId() != null) {
            throw new TreasuryWorkflowException(ENTITY, "already.rolled.over",
                    "Deal " + dealId + " is already rolled into deal " + old.getRolledToDealId(), dealId, old.getRolledToDealId());
        }
        final TreasurySetting setting = this.settingService.settingsFor(old.getCurrencyCode());
        final LocalDate business = DateUtils.getBusinessLocalDate();
        if (business.isBefore(old.getMaturityDate().minusDays(setting.getRolloverWindowDays()))
                || business.isAfter(old.getMaturityDate())) {
            throw new TreasuryWorkflowException(ENTITY, "rollover.window", "Deal " + dealId + " can be rolled over from "
                    + old.getMaturityDate().minusDays(setting.getRolloverWindowDays()) + " to its maturity on " + old.getMaturityDate(),
                    dealId);
        }
        if (!request.maturityDate().isAfter(old.getMaturityDate())) {
            throw new TreasuryWorkflowException(ENTITY, "rollover.maturity.invalid",
                    "The new maturity " + request.maturityDate() + " must be after " + old.getMaturityDate(), dealId);
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(ENTITY, dealId, "settled", old.getSettledBy(), "roll over", userId);

        final BigDecimal principal = old.getAmount().subtract(old.getPrincipalSettled());
        final BigDecimal maturityInterest = this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(dealId).stream()
                .filter(f -> f.getStatus().isOpen() && f.getFlowType() == FlowType.INTEREST && f.getDueDate().equals(old.getMaturityDate()))
                .map(TreasuryDealCashflow::remaining).reduce(BigDecimal.ZERO, BigDecimal::add);
        final BigDecimal amount = request.capitaliseInterest() ? principal.add(maturityInterest) : principal;

        final TreasuryDeal draft = new TreasuryDeal();
        draft.setDealTypeId(old.getDealTypeId());
        draft.setCounterpartyId(old.getCounterpartyId());
        draft.setSettlementAccountId(old.getSettlementAccountId());
        draft.setExternalReference(request.externalReference());
        draft.setTradeDate(business);
        draft.setValueDate(old.getMaturityDate());
        draft.setMaturityDate(request.maturityDate());
        draft.setAmount(amount);
        draft.setRate(request.rate());
        draft.setDayCount(old.getDayCount());
        draft.setFrequency(old.getFrequency());
        draft.setAmortising(old.isAmortising());
        draft.setFees(BigDecimal.ZERO);
        draft.setClassification(old.getClassification());
        draft.setRolledFromDealId(dealId);
        draft.setRolloverPreApproved(request.rolloverPreApproved());
        final Map<String, Object> changes = new LinkedHashMap<>();
        final TreasuryDeal rolled = this.dealWriteService.captureRolledDeal(draft, userId, changes);

        final long oldTenor = ChronoUnit.DAYS.between(old.getValueDate(), old.getMaturityDate());
        final long newTenor = ChronoUnit.DAYS.between(rolled.getValueDate(), rolled.getMaturityDate());
        final boolean preApproved = old.isRolloverPreApproved() && newTenor <= oldTenor
                && amount.compareTo(principal.add(maturityInterest)) <= 0 && !rolled.isLimitExceeded();
        if (preApproved) {
            this.dealWriteService.approveRolledDeal(rolled, old.getApprovedBy(), "Pre-approved rollover of deal " + dealId
                    + ": same counterparty, tenor " + newTenor + " days (original " + oldTenor + "), amount within the maturity proceeds");
        }
        old.setRolledToDealId(rolled.getId());
        old.setRolledOverBy(userId);
        this.dealRepository.saveAndFlush(old);
        event(old, TreasuryDealEventType.ROLLED_OVER, userId,
                "Rolling into deal " + rolled.getId()
                        + (request.capitaliseInterest() ? ", interest of " + maturityInterest.toPlainString() + " capitalised"
                                : ", interest paid out"),
                null);
        changes.put("rolledToDealId", rolled.getId());
        changes.put("amount", amount);
        changes.put("newDealStatus", rolled.getStatus().name());
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId).withSubEntityId(rolled.getId())
                .withOfficeId(old.getOfficeId()).with(changes).build();
    }

    // ---------- helpers ----------

    /** The deal's carrying amount from what has been posted and settled. */
    Carrying carrying(final TreasuryDeal deal, final TreasuryDealType type) {
        final InstrumentKind kind = type.getInstrumentKind();
        final BigDecimal principal = kind.interestMethod() == InterestMethod.DISCOUNT_YIELD
                ? deal.getConsideration().add(deal.getInterestAccrued()).subtract(deal.getPrincipalSettled())
                : deal.getAmount().subtract(deal.getPrincipalSettled());
        final BigDecimal interest = kind.interestMethod() == InterestMethod.DISCOUNT_YIELD ? BigDecimal.ZERO
                : deal.getAccruedInterestPurchased().add(deal.getInterestAccrued()).subtract(deal.getInterestSettled());
        final BigDecimal premium = deal.getPremium().signum() > 0 ? deal.getPremium().add(deal.getAmortisationAccrued()) : BigDecimal.ZERO;
        final BigDecimal discount = deal.getDiscount().signum() > 0 ? deal.getDiscount().subtract(deal.getAmortisationAccrued())
                : BigDecimal.ZERO;
        return new Carrying(principal, premium, discount, interest, deal.getFeeDeferred().subtract(deal.getFeeAmortised()));
    }

    Gl gl(final TreasuryDeal deal, final TreasuryDealType type) {
        final TreasuryAccount account = this.accountRepository.findById(deal.getSettlementAccountId())
                .orElseThrow(() -> new TreasuryAccountNotFoundException(deal.getSettlementAccountId()));
        return new Gl(account.getGlAccountId(), type.getPrincipalGlAccountId(), type.getInterestReceivableGlAccountId(),
                type.getInterestPayableGlAccountId(), type.getInterestIncomeGlAccountId(), type.getInterestExpenseGlAccountId(),
                type.getPremiumGlAccountId(), type.getDiscountGlAccountId(), type.getDeferredFeeGlAccountId(),
                type.getFeeExpenseGlAccountId(), type.getRealisedGainGlAccountId(), type.getRealisedLossGlAccountId());
    }

    private boolean covered(final TreasuryDeal deal, final BigDecimal amount) {
        if (this.settingService.settingsFor(deal.getCurrencyCode()).isAllowInsufficientBalance()) {
            return true;
        }
        final BigDecimal balance = this.balanceReader.glBalances(List.of(deal.getSettlementAccountId()), null)
                .getOrDefault(deal.getSettlementAccountId(), BigDecimal.ZERO);
        return balance.compareTo(amount) >= 0;
    }

    private void requireCovered(final TreasuryDeal deal, final BigDecimal amount) {
        if (!covered(deal, amount)) {
            throw new TreasuryWorkflowException(ENTITY, "insufficient.balance",
                    "The settlement account of deal " + deal.getId() + " cannot cover " + amount.toPlainString(), deal.getId(), amount);
        }
    }

    private static LocalDate accruedTo(final TreasuryDeal deal) {
        return deal.getAccruedToDate() == null ? deal.getValueDate() : deal.getAccruedToDate();
    }

    private int scale(final TreasuryDeal deal) {
        return this.referenceDataLookup.decimalPlaces(deal.getCurrencyCode());
    }

    private TreasuryDeal settled(final Long dealId, final String step) {
        final TreasuryDeal deal = find(dealId);
        if (deal.getStatus() != DealStatus.SETTLED) {
            throw new TreasuryWorkflowException(ENTITY, "invalid.status",
                    "Deal " + dealId + " is " + deal.getStatus() + " and cannot " + step, dealId, deal.getStatus().name());
        }
        return deal;
    }

    private TreasuryDeal find(final Long dealId) {
        return this.dealRepository.findById(dealId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury deal " + dealId + " does not exist", dealId));
    }

    private static void notAfterBusinessDate(final LocalDate date) {
        if (DateUtils.isAfterBusinessDate(date)) {
            throw new TreasuryWorkflowException(ENTITY, "future.date",
                    "The date " + date + " is after the business date " + DateUtils.getBusinessLocalDate(), date);
        }
    }

    private void event(final TreasuryDeal deal, final TreasuryDealEventType type, final Long userId, final String note,
            final String transactionId) {
        final TreasuryDealEvent event = new TreasuryDealEvent();
        event.setDealId(deal.getId());
        event.setEventType(type);
        event.setBusinessDate(DateUtils.getBusinessLocalDate());
        event.setUserId(userId);
        event.setNote(note == null ? null : note.length() > 1000 ? note.substring(0, 1000) : note);
        event.setTransactionId(transactionId);
        event.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.eventRepository.save(event);
    }

    private static CommandProcessingResult result(final JsonCommand command, final TreasuryDeal deal, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(deal.getId())
                .withOfficeId(deal.getOfficeId()).with(changes).build();
    }
}
