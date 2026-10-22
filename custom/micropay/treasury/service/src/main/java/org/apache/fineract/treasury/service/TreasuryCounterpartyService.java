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
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COUNTERPARTY_RATING_CODE;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.COUNTERPARTY_TYPE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.EXPOSURE_LIMIT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.LIMIT_CURRENCY_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.RATING_CODE_VALUE_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.RATING_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.SHORT_CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.WARNING_PERCENT_PARAM;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.treasury.data.TreasuryCounterpartyData;
import org.apache.fineract.treasury.data.TreasuryExposureData;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.LimitCheck;
import org.apache.fineract.treasury.domain.TreasuryCounterparty;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyRepository;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyStatus;
import org.apache.fineract.treasury.domain.TreasuryCounterpartyType;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryDealRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.Request;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Counterparties and their exposure. Exposure is the sum, over the counterparty's asset deals that are approved,
 * confirmed or settled, of what each adds: principal, or face value for bills and bonds. Liability deals (borrowing
 * from the counterparty) and balances in treasury accounts held with it are not counted.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryCounterpartyService {

    static final String ENTITY = "counterparty";
    static final BigDecimal DEFAULT_WARNING_PERCENT = BigDecimal.valueOf(80);
    private static final List<DealStatus> OPEN = Arrays.stream(DealStatus.values())
            .filter(status -> status.canCancel() || status == DealStatus.SETTLED).toList();
    private static final List<DealStatus> EXPOSED = Arrays.stream(DealStatus.values()).filter(DealStatus::countsTowardsExposure).toList();

    private final TreasuryDealDataValidator validator;
    private final TreasuryCounterpartyRepository counterpartyRepository;
    private final TreasuryDealRepository dealRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final Request request = this.validator.validateCounterparty(command.json(), true);
        final String shortCode = request.get(SHORT_CODE_PARAM);
        if (this.counterpartyRepository.existsByShortCode(shortCode)) {
            throw new TreasuryWorkflowException(ENTITY, "duplicate.short.code",
                    "A counterparty with short code " + shortCode + " already exists", shortCode);
        }
        final TreasuryCounterparty counterparty = new TreasuryCounterparty();
        counterparty.setShortCode(shortCode);
        counterparty.setStatus(TreasuryCounterpartyStatus.ACTIVE);
        counterparty.setWarningPercent(DEFAULT_WARNING_PERCENT);
        apply(counterparty, request);
        this.counterpartyRepository.saveAndFlush(counterparty);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(counterparty.getId()).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long counterpartyId, final JsonCommand command) {
        final TreasuryCounterparty counterparty = active(counterpartyId);
        final Map<String, Object> changes = apply(counterparty, this.validator.validateCounterparty(command.json(), false));
        if (!changes.isEmpty()) {
            this.counterpartyRepository.saveAndFlush(counterparty);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(counterpartyId).with(changes).build();
    }

    /** Only a counterparty without open deals can be closed. */
    @Transactional
    public CommandProcessingResult close(final Long counterpartyId, final JsonCommand command) {
        final TreasuryCounterparty counterparty = active(counterpartyId);
        final long open = this.dealRepository.countByCounterpartyIdAndStatusIn(counterpartyId, OPEN);
        if (open > 0) {
            throw new TreasuryWorkflowException(ENTITY, "open.deals",
                    "Counterparty " + counterparty.getShortCode() + " has " + open + " open deals and cannot be closed", counterpartyId,
                    open);
        }
        counterparty.setStatus(TreasuryCounterpartyStatus.CLOSED);
        this.counterpartyRepository.saveAndFlush(counterparty);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(counterpartyId)
                .with(Map.of("status", TreasuryCounterpartyStatus.CLOSED.name())).build();
    }

    @Transactional(readOnly = true)
    public List<TreasuryCounterpartyData> retrieveAll() {
        return this.counterpartyRepository.findAllByOrderByNameAsc().stream().map(TreasuryCounterpartyData::from).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryCounterpartyData retrieveOne(final Long counterpartyId) {
        return TreasuryCounterpartyData.from(find(counterpartyId));
    }

    @Transactional(readOnly = true)
    public TreasuryExposureData exposure(final Long counterpartyId) {
        final TreasuryCounterparty counterparty = find(counterpartyId);
        final List<TreasuryDeal> deals = exposedDeals(counterpartyId, null);
        final BigDecimal exposure = sum(deals);
        final LimitCheck.Result result = LimitCheck.evaluate(counterparty.getExposureLimit(), counterparty.getWarningPercent(), exposure,
                BigDecimal.ZERO);
        return new TreasuryExposureData(counterpartyId, counterparty.getShortCode(), counterparty.getName(),
                counterparty.getLimitCurrencyCode(), counterparty.getExposureLimit(), counterparty.getWarningPercent(), exposure,
                result.utilisation(), result.available(), result.outcome().name(),
                deals.stream().map(deal -> new TreasuryExposureData.DealExposure(deal.getId(), deal.getStatus().name(),
                        deal.getCurrencyCode(), deal.getExposureAmount(), deal.getMaturityDate())).toList());
    }

    /**
     * Exposure of the counterparty without the given deal, in the deal's currency. A limit set in another currency
     * cannot be compared until FX rates exist, so such a deal is refused.
     */
    LimitCheck.Result check(final TreasuryCounterparty counterparty, final TreasuryDeal deal) {
        if (counterparty.getExposureLimit() != null && !Objects.equals(counterparty.getLimitCurrencyCode(), deal.getCurrencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "limit.currency.mismatch",
                    "The limit of counterparty " + counterparty.getShortCode() + " is in " + counterparty.getLimitCurrencyCode()
                            + " and deals in " + deal.getCurrencyCode() + " cannot be checked against it",
                    counterparty.getId(), counterparty.getLimitCurrencyCode(), deal.getCurrencyCode());
        }
        return LimitCheck.evaluate(counterparty.getExposureLimit(), counterparty.getWarningPercent(),
                sum(exposedDeals(counterparty.getId(), deal.getId())), deal.getExposureAmount());
    }

    private List<TreasuryDeal> exposedDeals(final Long counterpartyId, final Long excludedDealId) {
        return this.dealRepository.findByCounterpartyIdAndStatusIn(counterpartyId, EXPOSED).stream()
                .filter(deal -> !Objects.equals(deal.getId(), excludedDealId))
                .filter(deal -> deal.getExposureAmount() != null && deal.getExposureAmount().signum() > 0).toList();
    }

    private static BigDecimal sum(final List<TreasuryDeal> deals) {
        return deals.stream().map(TreasuryDeal::getExposureAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    TreasuryCounterparty find(final Long counterpartyId) {
        return this.counterpartyRepository.findById(counterpartyId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "not.found",
                "Treasury counterparty " + counterpartyId + " does not exist", counterpartyId));
    }

    TreasuryCounterparty active(final Long counterpartyId) {
        final TreasuryCounterparty counterparty = find(counterpartyId);
        if (!counterparty.isActive()) {
            throw new TreasuryWorkflowException(ENTITY, "closed", "Counterparty " + counterparty.getShortCode() + " is closed",
                    counterpartyId);
        }
        return counterparty;
    }

    private Map<String, Object> apply(final TreasuryCounterparty counterparty, final Request request) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        set(request, NAME_PARAM, counterparty.getName(), counterparty::setName, changes);
        if (request.has(COUNTERPARTY_TYPE_PARAM) && counterparty.getCounterpartyType() != request.get(COUNTERPARTY_TYPE_PARAM)) {
            counterparty.setCounterpartyType(request.<TreasuryCounterpartyType>get(COUNTERPARTY_TYPE_PARAM));
            changes.put(COUNTERPARTY_TYPE_PARAM, counterparty.getCounterpartyType().name());
        }
        set(request, RATING_PARAM, counterparty.getRating(), counterparty::setRating, changes);
        if (request.has(RATING_CODE_VALUE_ID_PARAM)
                && !Objects.equals(counterparty.getRatingCodeValueId(), request.get(RATING_CODE_VALUE_ID_PARAM))) {
            final Long ratingId = request.get(RATING_CODE_VALUE_ID_PARAM);
            if (ratingId != null && !isRatingValue(ratingId)) {
                throw new TreasuryWorkflowException(ENTITY, "rating.unknown",
                        "Code value " + ratingId + " is not an active value of code " + COUNTERPARTY_RATING_CODE, ratingId);
            }
            counterparty.setRatingCodeValueId(ratingId);
            changes.put(RATING_CODE_VALUE_ID_PARAM, ratingId);
        }
        if (request.has(EXPOSURE_LIMIT_PARAM) && amountChanged(counterparty.getExposureLimit(), request.get(EXPOSURE_LIMIT_PARAM))) {
            counterparty.setExposureLimit(request.get(EXPOSURE_LIMIT_PARAM));
            changes.put(EXPOSURE_LIMIT_PARAM, counterparty.getExposureLimit());
        }
        set(request, LIMIT_CURRENCY_CODE_PARAM, counterparty.getLimitCurrencyCode(), counterparty::setLimitCurrencyCode, changes);
        if (request.has(WARNING_PERCENT_PARAM) && request.get(WARNING_PERCENT_PARAM) != null
                && amountChanged(counterparty.getWarningPercent(), request.get(WARNING_PERCENT_PARAM))) {
            counterparty.setWarningPercent(request.get(WARNING_PERCENT_PARAM));
            changes.put(WARNING_PERCENT_PARAM, counterparty.getWarningPercent());
        }
        if (counterparty.getExposureLimit() != null && counterparty.getLimitCurrencyCode() == null) {
            throw new TreasuryWorkflowException(ENTITY, "limit.currency.required", "An exposure limit needs a limit currency");
        }
        if (counterparty.getLimitCurrencyCode() != null
                && !this.referenceDataLookup.isOrganisationCurrency(counterparty.getLimitCurrencyCode())) {
            throw new TreasuryWorkflowException(ENTITY, "currency.not.organisation.currency",
                    "Currency " + counterparty.getLimitCurrencyCode() + " is not an organisation currency",
                    counterparty.getLimitCurrencyCode());
        }
        return changes;
    }

    private boolean isRatingValue(final Long codeValueId) {
        final Long count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_code_value cv JOIN m_code c ON c.id = cv.code_id"
                        + " WHERE c.code_name = ? AND cv.id = ? AND cv.is_active = ?",
                Long.class, COUNTERPARTY_RATING_CODE, codeValueId, Boolean.TRUE);
        return count != null && count > 0;
    }

    private static void set(final Request request, final String parameter, final String current,
            final java.util.function.Consumer<String> setter, final Map<String, Object> changes) {
        if (request.has(parameter) && !Objects.equals(current, request.get(parameter))) {
            setter.accept(request.get(parameter));
            changes.put(parameter, request.get(parameter));
        }
    }

    private static boolean amountChanged(final BigDecimal current, final BigDecimal requested) {
        return current == null ? requested != null : requested == null || current.compareTo(requested) != 0;
    }
}
