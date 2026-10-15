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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.loanaccount.domain.arrears.LoanArrearsData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoanDeliberateNpaWritePlatformService {

    private static final int NOTE_MAX_LENGTH = 500;

    private final PlatformSecurityContext context;
    private final LoanAssembler loanAssembler;
    private final CodeValueRepositoryWrapper codeValueRepository;
    private final LoanRepository loanRepository;
    private final LoanArrearsAgingService loanArrearsAgingService;

    @Transactional
    public CommandProcessingResult markAsNpa(final JsonCommand command) {
        final Long reasonId = command.longValueOfParameterNamed(LoanApiConstants.npaReasonIdParamName);
        final String note = blankToNull(command.stringValueOfParameterNamedAllowingNull(LoanApiConstants.noteParamName));
        validateMark(reasonId, note);

        final Loan loan = this.loanAssembler.assembleFrom(command.getLoanId());
        if (!loan.isOpen()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.is.not.active",
                    "Loan: " + loan.getId() + " Deliberate NPA classification is not allowed. Loan Account is not Active", loan.getId());
        }
        if (loan.isDeliberateNpa()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.npa.already.deliberate",
                    "Loan " + loan.getId() + " is already deliberately classified as NPA", loan.getId());
        }

        final CodeValue reason = this.codeValueRepository.findOneByCodeNameAndIdWithNotFoundDetection(LoanApiConstants.LOAN_NPA_REASONS,
                reasonId);
        if (!reason.isActive()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.npa.reason.inactive", "NPA reason " + reasonId + " is not active",
                    reasonId);
        }

        final AppUser currentUser = this.context.authenticatedUser();
        final LocalDate classifiedOn = DateUtils.getBusinessLocalDate();
        loan.markDeliberateNpa(reason, note, currentUser, classifiedOn);
        this.loanRepository.saveAndFlush(loan);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(LoanApiConstants.npaReasonIdParamName, reasonId);
        changes.put("deliberateNpa", true);
        changes.put("isNPA", true);
        if (note != null) {
            changes.put(LoanApiConstants.noteParamName, note);
        }

        return commandResult(command, loan, changes);
    }

    @Transactional
    public CommandProcessingResult undoMarkAsNpa(final JsonCommand command) {
        final Loan loan = this.loanAssembler.assembleFrom(command.getLoanId());
        if (!loan.isOpen()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.is.not.active",
                    "Loan: " + loan.getId() + " Undo deliberate NPA classification is not allowed. Loan Account is not Active",
                    loan.getId());
        }
        if (!loan.isDeliberateNpa()) {
            throw new GeneralPlatformDomainRuleException("error.msg.loan.npa.not.deliberate",
                    "Loan " + loan.getId() + " is not deliberately classified as NPA", loan.getId());
        }

        final boolean stillMeetsOverdueRule = meetsOverdueNpaRule(loan);
        loan.undoDeliberateNpa(stillMeetsOverdueRule);
        this.loanRepository.saveAndFlush(loan);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("deliberateNpa", false);
        changes.put("isNPA", loan.isNpa());
        return commandResult(command, loan, changes);
    }

    private void validateMark(final Long reasonId, final String note) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource("loan.npa");
        validator.reset().parameter(LoanApiConstants.npaReasonIdParamName).value(reasonId).notNull().longGreaterThanZero();
        if (note != null) {
            validator.reset().parameter(LoanApiConstants.noteParamName).value(note).notExceedingLengthOf(NOTE_MAX_LENGTH);
        }
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
    }

    private boolean meetsOverdueNpaRule(final Loan loan) {
        final Integer overdueDaysForNpa = loan.loanProduct().getOverdueDaysForNPA();
        if (overdueDaysForNpa == null) {
            return false;
        }
        final LoanArrearsData arrears = this.loanArrearsAgingService.calculateArrearsForLoan(loan);
        final LocalDate overdueSince = arrears.getOverDueSince();
        if (overdueSince == null) {
            return false;
        }
        final LocalDate threshold = DateUtils.getBusinessLocalDate().minusDays(overdueDaysForNpa);
        return DateUtils.isBefore(overdueSince, threshold);
    }

    private static String blankToNull(final String note) {
        return StringUtils.isBlank(note) ? null : note;
    }

    private CommandProcessingResult commandResult(final JsonCommand command, final Loan loan, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(loan.getId())
                .withEntityExternalId(loan.getExternalId()).withOfficeId(loan.getOfficeId()).withClientId(loan.getClientId())
                .withGroupId(loan.getGroupId()).withLoanId(loan.getId()).with(changes).build();
    }
}
