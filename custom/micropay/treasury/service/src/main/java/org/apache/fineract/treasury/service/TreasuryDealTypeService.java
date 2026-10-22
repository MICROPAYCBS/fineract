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
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ACTIVE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.AMORTISATION_METHOD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.CODE_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEFAULT_DAY_COUNT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEFAULT_FREQUENCY_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DEFERRED_FEE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.DISCOUNT_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.ECL_ALLOWANCE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FAIR_VALUE_ADJUSTMENT_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FAIR_VALUE_GAIN_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FAIR_VALUE_LOSS_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FAIR_VALUE_RESERVE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEE_EXPENSE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEE_INCOME_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.FEE_METHOD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.IMPAIRMENT_EXPENSE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INSTRUMENT_KIND_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_EXPENSE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_INCOME_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_METHOD_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_PAYABLE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.INTEREST_RECEIVABLE_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.NAME_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.PREMIUM_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.PRINCIPAL_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.REALISED_GAIN_GL_PARAM;
import static org.apache.fineract.treasury.api.TreasuryDealApiConstants.REALISED_LOSS_GL_PARAM;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.treasury.data.TreasuryDealTypeData;
import org.apache.fineract.treasury.deal.DayCount;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealDirection;
import org.apache.fineract.treasury.deal.FeeMethod;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;
import org.apache.fineract.treasury.domain.TreasuryDealType;
import org.apache.fineract.treasury.domain.TreasuryDealTypeRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryDealDataValidator.Request;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.GlAccountInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deal types and their GL accounts. Every account must be an enabled detail account of the right type:
 *
 * <ul>
 * <li>asset deal types: principal and interest receivable (asset) and interest income (income) are required; bond types
 * also need premium and discount (asset; the discount account works as a contra-asset);</li>
 * <li>liability deal types: principal and interest payable (liability) and interest expense (expense) are
 * required;</li>
 * <li>fee income (income) and fee expense (expense) are optional; fee expense is needed to settle a deal with
 * fees.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryDealTypeService {

    static final String ENTITY = "deal.type";
    static final int ASSET = 1;
    static final int LIABILITY = 2;
    static final int EQUITY = 3;
    static final int INCOME = 4;
    static final int EXPENSE = 5;

    private final TreasuryDealDataValidator validator;
    private final TreasuryDealTypeRepository dealTypeRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;

    @Transactional
    public CommandProcessingResult create(final JsonCommand command) {
        final Request request = this.validator.validateDealType(command.json(), true);
        final String code = request.get(CODE_PARAM);
        if (this.dealTypeRepository.existsByCode(code)) {
            throw new TreasuryWorkflowException(ENTITY, "duplicate.code", "A deal type with code " + code + " already exists", code);
        }
        final InstrumentKind kind = request.get(INSTRUMENT_KIND_PARAM);
        final TreasuryDealType type = new TreasuryDealType();
        type.setCode(code);
        type.setInstrumentKind(kind);
        type.setDirection(kind.direction());
        type.setInterestMethod(kind.interestMethod());
        type.setDefaultDayCount(DayCount.ACT_365);
        type.setDefaultFrequency(kind == InstrumentKind.GOVERNMENT_BOND ? 2 : 0);
        type.setActive(true);
        apply(type, request);
        validateAccounts(type);
        this.dealTypeRepository.saveAndFlush(type);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(type.getId()).build();
    }

    @Transactional
    public CommandProcessingResult update(final Long dealTypeId, final JsonCommand command) {
        final TreasuryDealType type = find(dealTypeId);
        final Map<String, Object> changes = apply(type, this.validator.validateDealType(command.json(), false));
        validateAccounts(type);
        if (!changes.isEmpty()) {
            this.dealTypeRepository.saveAndFlush(type);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(dealTypeId).with(changes).build();
    }

    @Transactional(readOnly = true)
    public List<TreasuryDealTypeData> retrieveAll() {
        return this.dealTypeRepository.findAllByOrderByCodeAsc().stream().map(TreasuryDealTypeData::from).toList();
    }

    @Transactional(readOnly = true)
    public TreasuryDealTypeData retrieveOne(final Long dealTypeId) {
        return TreasuryDealTypeData.from(find(dealTypeId));
    }

    TreasuryDealType find(final Long dealTypeId) {
        return this.dealTypeRepository.findById(dealTypeId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY, "not.found",
                "Treasury deal type " + dealTypeId + " does not exist", dealTypeId));
    }

    private static Map<String, Object> apply(final TreasuryDealType type, final Request request) {
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.has(NAME_PARAM) && !Objects.equals(type.getName(), request.get(NAME_PARAM))) {
            type.setName(request.get(NAME_PARAM));
            changes.put(NAME_PARAM, type.getName());
        }
        if (request.has(INTEREST_METHOD_PARAM) && request.get(INTEREST_METHOD_PARAM) != type.getInstrumentKind().interestMethod()) {
            final InterestMethod method = request.get(INTEREST_METHOD_PARAM);
            throw new TreasuryWorkflowException(ENTITY, "interest.method.mismatch",
                    type.getInstrumentKind() + " deals use " + type.getInstrumentKind().interestMethod() + " interest, not " + method,
                    String.valueOf(method));
        }
        if (request.has(DEFAULT_DAY_COUNT_PARAM) && request.get(DEFAULT_DAY_COUNT_PARAM) != null
                && type.getDefaultDayCount() != request.get(DEFAULT_DAY_COUNT_PARAM)) {
            type.setDefaultDayCount(request.get(DEFAULT_DAY_COUNT_PARAM));
            changes.put(DEFAULT_DAY_COUNT_PARAM, type.getDefaultDayCount().name());
        }
        if (request.has(DEFAULT_FREQUENCY_PARAM) && request.get(DEFAULT_FREQUENCY_PARAM) != null
                && type.getDefaultFrequency() != request.<Integer>get(DEFAULT_FREQUENCY_PARAM)) {
            type.setDefaultFrequency(request.<Integer>get(DEFAULT_FREQUENCY_PARAM));
            changes.put(DEFAULT_FREQUENCY_PARAM, type.getDefaultFrequency());
        }
        gl(request, PRINCIPAL_GL_PARAM, type::getPrincipalGlAccountId, type::setPrincipalGlAccountId, changes);
        gl(request, INTEREST_RECEIVABLE_GL_PARAM, type::getInterestReceivableGlAccountId, type::setInterestReceivableGlAccountId, changes);
        gl(request, INTEREST_PAYABLE_GL_PARAM, type::getInterestPayableGlAccountId, type::setInterestPayableGlAccountId, changes);
        gl(request, INTEREST_INCOME_GL_PARAM, type::getInterestIncomeGlAccountId, type::setInterestIncomeGlAccountId, changes);
        gl(request, INTEREST_EXPENSE_GL_PARAM, type::getInterestExpenseGlAccountId, type::setInterestExpenseGlAccountId, changes);
        gl(request, PREMIUM_GL_PARAM, type::getPremiumGlAccountId, type::setPremiumGlAccountId, changes);
        gl(request, DISCOUNT_GL_PARAM, type::getDiscountGlAccountId, type::setDiscountGlAccountId, changes);
        gl(request, FEE_INCOME_GL_PARAM, type::getFeeIncomeGlAccountId, type::setFeeIncomeGlAccountId, changes);
        gl(request, FEE_EXPENSE_GL_PARAM, type::getFeeExpenseGlAccountId, type::setFeeExpenseGlAccountId, changes);
        gl(request, DEFERRED_FEE_GL_PARAM, type::getDeferredFeeGlAccountId, type::setDeferredFeeGlAccountId, changes);
        gl(request, REALISED_GAIN_GL_PARAM, type::getRealisedGainGlAccountId, type::setRealisedGainGlAccountId, changes);
        gl(request, REALISED_LOSS_GL_PARAM, type::getRealisedLossGlAccountId, type::setRealisedLossGlAccountId, changes);
        gl(request, FAIR_VALUE_ADJUSTMENT_GL_PARAM, type::getFairValueAdjustmentGlAccountId, type::setFairValueAdjustmentGlAccountId,
                changes);
        gl(request, FAIR_VALUE_GAIN_GL_PARAM, type::getFairValueGainGlAccountId, type::setFairValueGainGlAccountId, changes);
        gl(request, FAIR_VALUE_LOSS_GL_PARAM, type::getFairValueLossGlAccountId, type::setFairValueLossGlAccountId, changes);
        gl(request, FAIR_VALUE_RESERVE_GL_PARAM, type::getFairValueReserveGlAccountId, type::setFairValueReserveGlAccountId, changes);
        gl(request, IMPAIRMENT_EXPENSE_GL_PARAM, type::getImpairmentExpenseGlAccountId, type::setImpairmentExpenseGlAccountId, changes);
        gl(request, ECL_ALLOWANCE_GL_PARAM, type::getEclAllowanceGlAccountId, type::setEclAllowanceGlAccountId, changes);
        if (request.has(FEE_METHOD_PARAM) && request.get(FEE_METHOD_PARAM) != null
                && type.getFeeMethod() != request.get(FEE_METHOD_PARAM)) {
            type.setFeeMethod(request.get(FEE_METHOD_PARAM));
            changes.put(FEE_METHOD_PARAM, type.getFeeMethod().name());
        }
        if (request.has(AMORTISATION_METHOD_PARAM) && request.get(AMORTISATION_METHOD_PARAM) != null
                && type.getAmortisationMethod() != request.get(AMORTISATION_METHOD_PARAM)) {
            type.setAmortisationMethod(request.get(AMORTISATION_METHOD_PARAM));
            changes.put(AMORTISATION_METHOD_PARAM, type.getAmortisationMethod().name());
        }
        if (request.has(ACTIVE_PARAM) && request.get(ACTIVE_PARAM) != null && type.isActive() != request.<Boolean>get(ACTIVE_PARAM)) {
            type.setActive(request.<Boolean>get(ACTIVE_PARAM));
            changes.put(ACTIVE_PARAM, type.isActive());
        }
        return changes;
    }

    private static void gl(final Request request, final String parameter, final Supplier<Long> current, final Consumer<Long> setter,
            final Map<String, Object> changes) {
        if (request.has(parameter) && !Objects.equals(current.get(), request.get(parameter))) {
            setter.accept(request.get(parameter));
            changes.put(parameter, request.get(parameter));
        }
    }

    void validateAccounts(final TreasuryDealType type) {
        final boolean asset = type.getDirection() == DealDirection.ASSET;
        final boolean bond = type.getInstrumentKind() == InstrumentKind.GOVERNMENT_BOND;
        account(PRINCIPAL_GL_PARAM, type.getPrincipalGlAccountId(), asset ? ASSET : LIABILITY, true);
        account(INTEREST_RECEIVABLE_GL_PARAM, type.getInterestReceivableGlAccountId(), ASSET, asset);
        account(INTEREST_INCOME_GL_PARAM, type.getInterestIncomeGlAccountId(), INCOME, asset);
        account(INTEREST_PAYABLE_GL_PARAM, type.getInterestPayableGlAccountId(), LIABILITY, !asset);
        account(INTEREST_EXPENSE_GL_PARAM, type.getInterestExpenseGlAccountId(), EXPENSE, !asset);
        account(PREMIUM_GL_PARAM, type.getPremiumGlAccountId(), ASSET, bond);
        account(DISCOUNT_GL_PARAM, type.getDiscountGlAccountId(), ASSET, bond);
        account(FEE_INCOME_GL_PARAM, type.getFeeIncomeGlAccountId(), INCOME, false);
        final boolean amortiseFees = type.getFeeMethod() == FeeMethod.AMORTISE;
        account(FEE_EXPENSE_GL_PARAM, type.getFeeExpenseGlAccountId(), EXPENSE, amortiseFees);
        account(DEFERRED_FEE_GL_PARAM, type.getDeferredFeeGlAccountId(), ASSET, amortiseFees);
        account(REALISED_GAIN_GL_PARAM, type.getRealisedGainGlAccountId(), INCOME, false);
        account(REALISED_LOSS_GL_PARAM, type.getRealisedLossGlAccountId(), EXPENSE, false);
        account(FAIR_VALUE_ADJUSTMENT_GL_PARAM, type.getFairValueAdjustmentGlAccountId(), ASSET, false);
        account(FAIR_VALUE_GAIN_GL_PARAM, type.getFairValueGainGlAccountId(), INCOME, false);
        account(FAIR_VALUE_LOSS_GL_PARAM, type.getFairValueLossGlAccountId(), EXPENSE, false);
        account(FAIR_VALUE_RESERVE_GL_PARAM, type.getFairValueReserveGlAccountId(), EQUITY, false);
        account(IMPAIRMENT_EXPENSE_GL_PARAM, type.getImpairmentExpenseGlAccountId(), EXPENSE, false);
        account(ECL_ALLOWANCE_GL_PARAM, type.getEclAllowanceGlAccountId(), ASSET, false);
    }

    /** Bills and bonds held at fair value are revalued from market prices. */
    public static boolean revalued(final InstrumentKind kind, final DealClassification classification) {
        return (kind == InstrumentKind.TREASURY_BILL || kind == InstrumentKind.GOVERNMENT_BOND)
                && (classification == DealClassification.FVOCI || classification == DealClassification.FVTPL);
    }

    /**
     * A bill or bond at fair value needs the accounts its revaluation and derecognition post to: the fair value
     * adjustment, the gain and loss accounts (FVTPL) or the OCI reserve (FVOCI), and the realised gain and loss
     * accounts.
     */
    static void requireValuationAccounts(final TreasuryDealType type, final DealClassification classification) {
        final List<String> missing = missingValuationAccounts(type, classification);
        if (!missing.isEmpty()) {
            throw new TreasuryWorkflowException(ENTITY, "valuation.gl.account.missing", "Deal type " + type.getCode() + " needs "
                    + String.join(", ", missing) + " before it can hold " + classification + " deals", type.getId(), classification.name());
        }
    }

    /** The valuation accounts a bill or bond at fair value of the type lacks; none for other holdings. */
    static List<String> missingValuationAccounts(final TreasuryDealType type, final DealClassification classification) {
        final List<String> missing = new ArrayList<>();
        if (!revalued(type.getInstrumentKind(), classification)) {
            return missing;
        }
        if (type.getFairValueAdjustmentGlAccountId() == null) {
            missing.add(FAIR_VALUE_ADJUSTMENT_GL_PARAM);
        }
        if (classification == DealClassification.FVTPL) {
            if (type.getFairValueGainGlAccountId() == null) {
                missing.add(FAIR_VALUE_GAIN_GL_PARAM);
            }
            if (type.getFairValueLossGlAccountId() == null) {
                missing.add(FAIR_VALUE_LOSS_GL_PARAM);
            }
        } else if (type.getFairValueReserveGlAccountId() == null) {
            missing.add(FAIR_VALUE_RESERVE_GL_PARAM);
        }
        if (type.getRealisedGainGlAccountId() == null) {
            missing.add(REALISED_GAIN_GL_PARAM);
        }
        if (type.getRealisedLossGlAccountId() == null) {
            missing.add(REALISED_LOSS_GL_PARAM);
        }
        return missing;
    }

    private void account(final String parameter, final Long glAccountId, final int classification, final boolean required) {
        if (glAccountId == null) {
            if (required) {
                throw new TreasuryWorkflowException(ENTITY, "gl.account.required", "The deal type needs " + parameter, parameter);
            }
            return;
        }
        final GlAccountInfo gl = this.referenceDataLookup.findGlAccount(glAccountId).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                "gl.account.not.found", "GL account " + glAccountId + " (" + parameter + ") does not exist", parameter, glAccountId));
        if (gl.classification() != classification || !gl.isDetail() || gl.disabled()) {
            throw new TreasuryWorkflowException(ENTITY, "gl.account.wrong.type", "GL account " + glAccountId + " (" + parameter
                    + ") must be an enabled detail account of type " + typeName(classification), parameter, glAccountId);
        }
    }

    private static String typeName(final int classification) {
        return switch (classification) {
            case ASSET -> "ASSET";
            case LIABILITY -> "LIABILITY";
            case EQUITY -> "EQUITY";
            case INCOME -> "INCOME";
            case EXPENSE -> "EXPENSE";
            default -> String.valueOf(classification);
        };
    }
}
