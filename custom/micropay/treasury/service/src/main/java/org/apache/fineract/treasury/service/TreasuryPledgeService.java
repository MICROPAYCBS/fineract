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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventType;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryPledge;
import org.apache.fineract.treasury.domain.TreasuryPledgeRepository;
import org.apache.fineract.treasury.domain.TreasuryPledgeStatus;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.PledgeRequest;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator.ReleasePledgeRequest;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pledges of settled asset deals (placements, deposits, bills, bonds) to counterparties: for a repo, as collateral for
 * a borrowing, for a central bank facility. A pledge is a memo by nominal (principal, or face value) and posts nothing;
 * the pledges active on a date may not exceed the nominal outstanding, and what is left is the holding's unencumbered
 * amount (for the liquid asset rules of P3). A pledged deal cannot be terminated until released; at maturity its
 * pledges are released.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryPledgeService {

    static final String ENTITY = "deal";
    private static final List<DealStatus> LINKABLE = List.of(DealStatus.CAPTURED, DealStatus.APPROVED, DealStatus.CONFIRMED,
            DealStatus.SETTLED);

    private final TreasuryHoldingDataValidator validator;
    private final TreasuryActor actor;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryDealTypeService dealTypeService;
    private final TreasuryCounterpartyService counterpartyService;
    private final TreasuryPledgeRepository pledgeRepository;
    private final TreasuryDealEventRepository eventRepository;

    @Transactional
    public CommandProcessingResult pledge(final Long dealId, final JsonCommand command) {
        final PledgeRequest request = this.validator.validatePledge(command.json());
        final TreasuryDeal deal = find(dealId);
        final TreasuryDealType type = this.dealTypeService.find(deal.getDealTypeId());
        if (deal.getStatus() != DealStatus.SETTLED || !type.getInstrumentKind().isAsset()) {
            throw new TreasuryWorkflowException(ENTITY, "pledge.not.allowed", "Only settled asset deals can be pledged; deal " + dealId
                    + " is a " + deal.getStatus() + " " + type.getInstrumentKind(), dealId);
        }
        this.counterpartyService.active(request.pledgeeCounterpartyId());
        if (request.linkedDealId() != null) {
            final TreasuryDeal linked = find(request.linkedDealId());
            if (this.dealTypeService.find(linked.getDealTypeId()).getInstrumentKind().isAsset() || !LINKABLE.contains(linked.getStatus())) {
                throw new TreasuryWorkflowException(ENTITY, "pledge.linked.deal.invalid",
                        "A pledge may secure an open borrowing; deal " + linked.getId() + " is not one", linked.getId());
            }
        }
        final LocalDate business = DateUtils.getBusinessLocalDate();
        final LocalDate start = request.startDate() == null ? business : request.startDate();
        if (start.isAfter(business) || start.isBefore(deal.getValueDate())) {
            throw new TreasuryWorkflowException(ENTITY, "pledge.start.date.invalid", "A pledge of deal " + dealId
                    + " starts from its value date " + deal.getValueDate() + " up to the business date " + business + ", not " + start,
                    dealId, start);
        }
        if (request.expectedEndDate() != null && request.expectedEndDate().isAfter(deal.getMaturityDate())) {
            throw new TreasuryWorkflowException(ENTITY, "pledge.end.date.invalid",
                    "The pledge cannot be expected to end after the deal matures on " + deal.getMaturityDate(), dealId,
                    request.expectedEndDate());
        }
        final BigDecimal available = outstanding(deal).subtract(activeTotal(dealId));
        if (request.amount().compareTo(available) > 0) {
            throw new TreasuryWorkflowException(ENTITY, "pledge.exceeds.unencumbered", "Only " + available.toPlainString() + " of deal "
                    + dealId + " is unpledged; " + request.amount().toPlainString() + " cannot be pledged", dealId, available);
        }
        final Long userId = this.actor.userId(command);
        final TreasuryPledge pledge = new TreasuryPledge();
        pledge.setDealId(dealId);
        pledge.setPledgeeCounterpartyId(request.pledgeeCounterpartyId());
        pledge.setPurpose(request.purpose());
        pledge.setLinkedDealId(request.linkedDealId());
        pledge.setAmount(request.amount());
        pledge.setStartDate(start);
        pledge.setExpectedEndDate(request.expectedEndDate());
        pledge.setStatus(TreasuryPledgeStatus.ACTIVE);
        pledge.setReason(request.reason());
        pledge.setPledgedBy(userId);
        pledge.setCreatedOnUtc(DateUtils.getAuditOffsetDateTime());
        this.pledgeRepository.saveAndFlush(pledge);
        DealEvents.record(this.eventRepository, dealId, TreasuryDealEventType.PLEDGED, userId, "Pledge " + pledge.getId() + ": "
                + request.amount().toPlainString() + " to counterparty " + request.pledgeeCounterpartyId() + " (" + request.purpose() + ")",
                null);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("pledgeId", pledge.getId());
        changes.put("encumbered", activeTotal(dealId));
        changes.put("unencumbered", outstanding(deal).subtract(activeTotal(dealId)));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId).withSubEntityId(pledge.getId())
                .withOfficeId(deal.getOfficeId()).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult releasePledge(final Long dealId, final JsonCommand command) {
        final ReleasePledgeRequest request = this.validator.validateReleasePledge(command.json());
        final TreasuryDeal deal = find(dealId);
        final TreasuryPledge pledge = this.pledgeRepository.findById(request.pledgeId()).filter(p -> p.getDealId().equals(dealId))
                .orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "pledge.not.found",
                        "Pledge " + request.pledgeId() + " is not a pledge of deal " + dealId, request.pledgeId()));
        if (pledge.getStatus() != TreasuryPledgeStatus.ACTIVE) {
            throw new TreasuryWorkflowException(ENTITY, "pledge.not.active", "Pledge " + pledge.getId() + " is " + pledge.getStatus(),
                    pledge.getId());
        }
        final LocalDate business = DateUtils.getBusinessLocalDate();
        final LocalDate date = request.releaseDate() == null ? business : request.releaseDate();
        if (date.isAfter(business) || date.isBefore(pledge.getStartDate())) {
            throw new TreasuryWorkflowException(ENTITY, "pledge.release.date.invalid", "Pledge " + pledge.getId()
                    + " can be released from its start " + pledge.getStartDate() + " up to the business date " + business, pledge.getId(),
                    date);
        }
        final Long userId = this.actor.userId(command);
        release(pledge, date, userId, request.reason());
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("pledgeId", pledge.getId());
        changes.put("status", pledge.getStatus().name());
        changes.put("unencumbered", outstanding(deal).subtract(activeTotal(dealId)));
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealId).withSubEntityId(pledge.getId())
                .withOfficeId(deal.getOfficeId()).with(changes).build();
    }

    /** Pledged nominal of the deal on the date. */
    @Transactional(readOnly = true)
    public BigDecimal encumberedAmount(final Long dealId, final LocalDate date) {
        return this.pledgeRepository.findByDealIdOrderByIdAsc(dealId).stream().filter(p -> p.activeOn(date)).map(TreasuryPledge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** The amount pledged on the date of each of the deals, in one query; deals without pledges are left out. */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> encumberedAmounts(final Collection<Long> dealIds, final LocalDate date) {
        if (dealIds.isEmpty()) {
            return Map.of();
        }
        return this.pledgeRepository.findByDealIdIn(dealIds).stream().filter(p -> p.activeOn(date))
                .collect(Collectors.toMap(TreasuryPledge::getDealId, TreasuryPledge::getAmount, BigDecimal::add));
    }

    /**
     * Nominal of a settled holding not pledged on the date; zero for a deal that is not a settled asset deal, or not
     * yet held on the date.
     */
    @Transactional(readOnly = true)
    public BigDecimal unencumberedAmount(final TreasuryDeal deal, final LocalDate date) {
        if (deal.getStatus() != DealStatus.SETTLED || date.isBefore(deal.getValueDate()) || !date.isBefore(deal.getMaturityDate())
                || !this.dealTypeService.find(deal.getDealTypeId()).getInstrumentKind().isAsset()) {
            return BigDecimal.ZERO;
        }
        return outstanding(deal).subtract(encumberedAmount(deal.getId(), date)).max(BigDecimal.ZERO);
    }

    void requireUnpledged(final TreasuryDeal deal, final String step) {
        final BigDecimal pledged = activeTotal(deal.getId());
        if (pledged.signum() > 0) {
            throw new TreasuryWorkflowException(ENTITY, "pledged", "Deal " + deal.getId() + " has " + pledged.toPlainString()
                    + " pledged and cannot " + step + " until its pledges are released", deal.getId(), pledged);
        }
    }

    /** A principal repayment may not take the outstanding below what is pledged, short of repaying it all. */
    void requirePledgesCovered(final TreasuryDeal deal, final BigDecimal outstandingAfter) {
        final BigDecimal pledged = activeTotal(deal.getId());
        if (outstandingAfter.signum() > 0 && pledged.compareTo(outstandingAfter) > 0) {
            throw new TreasuryWorkflowException(ENTITY, "pledged",
                    "Deal " + deal.getId() + " has " + pledged.toPlainString()
                            + " pledged; release part of it before its outstanding falls to " + outstandingAfter.toPlainString(),
                    deal.getId(), pledged);
        }
    }

    /** At maturity: every active pledge is released on the maturity date. */
    int releaseAll(final TreasuryDeal deal, final LocalDate date, final Long userId, final String reason) {
        final List<TreasuryPledge> active = this.pledgeRepository.findByDealIdAndStatus(deal.getId(), TreasuryPledgeStatus.ACTIVE);
        for (final TreasuryPledge pledge : active) {
            release(pledge, date, userId, reason);
        }
        return active.size();
    }

    private void release(final TreasuryPledge pledge, final LocalDate date, final Long userId, final String reason) {
        pledge.setStatus(TreasuryPledgeStatus.RELEASED);
        pledge.setReleasedBy(userId);
        pledge.setReleasedOnDate(date);
        pledge.setReleaseReason(reason);
        this.pledgeRepository.saveAndFlush(pledge);
        DealEvents.record(this.eventRepository, pledge.getDealId(), TreasuryDealEventType.PLEDGE_RELEASED, userId,
                "Pledge " + pledge.getId() + " of " + pledge.getAmount().toPlainString() + " released on " + date + ": " + reason, null);
    }

    private BigDecimal activeTotal(final Long dealId) {
        return this.pledgeRepository.findByDealIdAndStatus(dealId, TreasuryPledgeStatus.ACTIVE).stream().map(TreasuryPledge::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Principal, or face, not yet repaid. */
    static BigDecimal outstanding(final TreasuryDeal deal) {
        return deal.getAmount().subtract(deal.getPrincipalSettled());
    }

    private TreasuryDeal find(final Long dealId) {
        return this.dealRepository.findById(dealId).orElseThrow(
                () -> new TreasuryWorkflowException(ENTITY, "not.found", "Treasury deal " + dealId + " does not exist", dealId));
    }
}
