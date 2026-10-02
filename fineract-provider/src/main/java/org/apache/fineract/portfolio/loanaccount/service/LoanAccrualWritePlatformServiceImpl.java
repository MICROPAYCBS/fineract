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
package org.apache.fineract.portfolio.loanaccount.service;

import static org.apache.fineract.accounting.accrual.api.AccrualAccountingConstants.ACCRUE_TILL_PARAM_NAME;
import static org.apache.fineract.accounting.accrual.api.AccrualAccountingConstants.DATE_FORMAT_PARAM_NAME;
import static org.apache.fineract.accounting.accrual.api.AccrualAccountingConstants.LOCALE_PARAM_NAME;

import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.MultiException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanInterestRecalculationDetails;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanAccrualWritePlatformServiceImpl implements LoanAccrualWritePlatformService {

    private static final Set<String> SUPPORTED_PARAMETERS = Set.of(ACCRUE_TILL_PARAM_NAME, LOCALE_PARAM_NAME, DATE_FORMAT_PARAM_NAME);
    private static final String RESOURCE_NAME = "loan";

    private final LoanRepositoryWrapper loanRepositoryWrapper;
    private final LoanAccrualsProcessingService loanAccrualsProcessingService;
    private final FromJsonHelper fromApiJsonHelper;

    @Transactional
    @Override
    public CommandProcessingResult accrueLoan(final JsonCommand command) {
        final Loan loan = this.loanRepositoryWrapper.findOneWithNotFoundDetection(command.getLoanId(), true);
        validateLoanCanAccrue(loan);
        final LocalDate tillDate = resolveTillDate(command);

        final LoanInterestRecalculationDetails recalculationDetails = loan.getLoanInterestRecalculationDetails();
        final boolean compoundingPostedAsTransaction = recalculationDetails != null
                && recalculationDetails.isCompoundingToBePostedAsTransaction();
        if (compoundingPostedAsTransaction) {
            if (loan.isProgressiveSchedule()) {
                throw new GeneralPlatformDomainRuleException("error.msg.loan.accrual.progressive.compounding.unsupported",
                        "Single-loan accrual is not available for a progressive loan that posts compounding income as transactions");
            }
            addIncomePostingAccruals(loan);
        } else {
            addPeriodicAccruals(loan, tillDate);
        }
        return CommandProcessingResult.resourceResult(loan.getId(), command.commandId());
    }

    private void validateLoanCanAccrue(final Loan loan) {
        if (!loan.isPeriodicAccrualAccountingEnabledOnLoanProduct()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accrual.accounting.rule.not.periodic",
                    "Accruals can be posted only when the loan product uses periodic accrual accounting");
        }
        if (!loan.isOpen()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accrual.not.active",
                    "Accruals can be posted only for an active loan");
        }
        if (loan.isNpa()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accrual.npa",
                    "Accruals are not posted for a non-performing loan");
        }
        if (loan.isChargedOff()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accrual.charged.off",
                    "Accruals are not posted for a charged-off loan");
        }
        if (loan.isContractTermination()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.accrual.contract.terminated",
                    "Accruals are not posted for a contract-terminated loan");
        }
    }

    private LocalDate resolveTillDate(final JsonCommand command) {
        if (StringUtils.isBlank(command.json())) {
            return DateUtils.getBusinessLocalDate();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, command.json(), SUPPORTED_PARAMETERS);
        if (!command.parameterExists(ACCRUE_TILL_PARAM_NAME)) {
            return DateUtils.getBusinessLocalDate();
        }
        final LocalDate tillDate = command.localDateValueOfParameterNamed(ACCRUE_TILL_PARAM_NAME);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);
        baseDataValidator.reset().parameter(ACCRUE_TILL_PARAM_NAME).value(tillDate).notNull()
                .validateDateBeforeOrEqual(DateUtils.getBusinessLocalDate());
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
        return tillDate;
    }

    private void addPeriodicAccruals(final Loan loan, final LocalDate tillDate) {
        try {
            this.loanAccrualsProcessingService.addPeriodicAccruals(tillDate, loan);
        } catch (final MultiException e) {
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE_NAME);
            baseDataValidator.reset().failWithCodeNoParameterAddedToErrorCode("accrual.execution.failed", e.getMessage());
            throw new PlatformApiDataValidationException(dataValidationErrors, e);
        }
        this.loanRepositoryWrapper.saveAndFlush(loan);
    }

    private void addIncomePostingAccruals(final Loan loan) {
        try {
            this.loanAccrualsProcessingService.addIncomePostingAndAccruals(loan.getId());
        } catch (final RuntimeException e) {
            throw e;
        } catch (final Exception e) {
            throw new IllegalStateException("Failed to add income posting accruals for loan " + loan.getId(), e);
        }
    }
}
