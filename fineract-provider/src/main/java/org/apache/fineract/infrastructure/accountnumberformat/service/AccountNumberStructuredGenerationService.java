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
package org.apache.fineract.infrastructure.accountnumberformat.service;

import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.accountnumberformat.data.AccountNumberGenerationContext;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.StructuredAccountNumberRuleDefaults;
import org.apache.fineract.infrastructure.accountnumberformat.exception.OfficeCodeRequiredForAccountNumberException;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeExtension;
import org.apache.fineract.organisation.office.domain.OfficeExtensionRepository;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanproduct.domain.LoanProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccount;
import org.apache.fineract.portfolio.shareproducts.domain.ShareProduct;
import org.apache.fineract.portfolio.workingcapitalloan.domain.WorkingCapitalLoan;
import org.springframework.stereotype.Service;

@Service
public class AccountNumberStructuredGenerationService {

    private final AccountNumberFormatEngine accountNumberFormatEngine;
    private final OfficeExtensionRepository officeExtensionRepository;

    public AccountNumberStructuredGenerationService(final AccountNumberFormatEngine accountNumberFormatEngine,
            final OfficeExtensionRepository officeExtensionRepository) {
        this.accountNumberFormatEngine = accountNumberFormatEngine;
        this.officeExtensionRepository = officeExtensionRepository;
    }

    public String generate(final EntityAccountType entityAccountType, final Object entity, final AccountNumberFormat accountNumberFormat,
            final boolean preview) {
        final AccountNumberGenerationContext context = buildContext(entityAccountType, entity, accountNumberFormat, preview);
        return accountNumberFormatEngine.generate(context);
    }

    public AccountNumberGenerationContext buildContext(final EntityAccountType entityAccountType, final Object entity,
            final AccountNumberFormat accountNumberFormat, final boolean preview) {
        final StructuredAccountNumberRuleDefaults defaults = StructuredAccountNumberRuleDefaults.forEntityType(entityAccountType);
        final String formatPattern = resolveFormatPattern(accountNumberFormat, defaults);
        final AccountNumberSequenceScope sequenceScope = resolveSequenceScope(accountNumberFormat, defaults);
        final CheckDigitAlgorithm checkDigitAlgorithm = resolveCheckDigitAlgorithm(accountNumberFormat, defaults);

        Office office = null;
        String productCode = null;
        String clientTypeCode = null;

        switch (entityAccountType) {
            case CLIENT -> {
                final Client client = (Client) entity;
                office = client.getOffice();
                clientTypeCode = resolveClientTypeCode(client.clientType());
            }
            case LOAN -> {
                final Loan loan = (Loan) entity;
                office = loan.getOffice();
                productCode = normalizeProductCode(loan.loanProduct().getShortName());
            }
            case SAVINGS -> {
                final SavingsAccount savingsAccount = (SavingsAccount) entity;
                office = savingsAccount.office();
                productCode = normalizeProductCode(savingsAccount.savingsProduct().getShortName());
            }
            case SHARES -> {
                final ShareAccount shareAccount = (ShareAccount) entity;
                office = shareAccount.getClient() != null ? shareAccount.getClient().getOffice() : null;
                productCode = normalizeProductCode(shareAccount.getShareProduct().getShortName());
            }
            case WORKING_CAPITAL_LOAN -> {
                final WorkingCapitalLoan workingCapitalLoan = (WorkingCapitalLoan) entity;
                if (workingCapitalLoan.getClient() != null) {
                    office = workingCapitalLoan.getClient().getOffice();
                }
                if (workingCapitalLoan.getLoanProduct() != null) {
                    productCode = normalizeProductCode(workingCapitalLoan.getLoanProduct().getShortName());
                }
            }
            case GROUP, CENTER -> {
                final Group group = (Group) entity;
                office = group.getOffice();
            }
        }

        final OfficeExtension officeExtension = office != null ? officeExtensionRepository.findById(office.getId()).orElse(null) : null;
        final String officeCode = officeExtension != null ? officeExtension.getOfficeCode() : null;
        if (AccountNumberFormatPatternParser.requiresOfficeCode(formatPattern) && StringUtils.isBlank(officeCode)) {
            throw new OfficeCodeRequiredForAccountNumberException(office != null ? office.getId() : null);
        }

        return AccountNumberGenerationContext.builder().entityAccountType(entityAccountType).office(office).officeCode(officeCode)
                .regionCode(officeExtension != null ? officeExtension.getRegionCode() : null)
                .branchType(officeExtension != null ? officeExtension.getBranchType() : null).productCode(productCode)
                .clientTypeCode(clientTypeCode).entityTypeCode(String.valueOf(entityAccountType.getValue())).formatPattern(formatPattern)
                .sequenceScope(sequenceScope).checkDigitAlgorithm(checkDigitAlgorithm).preview(preview).build();
    }

    public AccountNumberGenerationContext buildPreviewContext(final EntityAccountType entityAccountType, final Long officeId,
            final String productShortName, final String clientTypeLabel, final AccountNumberFormat accountNumberFormat) {
        final StructuredAccountNumberRuleDefaults defaults = StructuredAccountNumberRuleDefaults.forEntityType(entityAccountType);
        final String formatPattern = resolveFormatPattern(accountNumberFormat, defaults);
        final OfficeExtension officeExtension = officeId != null ? officeExtensionRepository.findById(officeId).orElse(null) : null;
        final String officeCode = officeExtension != null ? officeExtension.getOfficeCode() : "001";
        return AccountNumberGenerationContext.builder().entityAccountType(entityAccountType).officeCode(officeCode)
                .regionCode(officeExtension != null ? officeExtension.getRegionCode() : null)
                .branchType(officeExtension != null ? officeExtension.getBranchType() : null)
                .productCode(normalizeProductCode(productShortName)).clientTypeCode(resolveClientTypeCode(clientTypeLabel))
                .entityTypeCode(String.valueOf(entityAccountType.getValue())).formatPattern(formatPattern)
                .sequenceScope(resolveSequenceScope(accountNumberFormat, defaults))
                .checkDigitAlgorithm(resolveCheckDigitAlgorithm(accountNumberFormat, defaults)).preview(true).build();
    }

    public String preview(final EntityAccountType entityAccountType, final Long officeId, final String productShortName,
            final String clientTypeLabel, final AccountNumberFormat accountNumberFormat) {
        return accountNumberFormatEngine.generate(
                buildPreviewContext(entityAccountType, officeId, productShortName, clientTypeLabel, accountNumberFormat));
    }

    private String resolveFormatPattern(final AccountNumberFormat accountNumberFormat, final StructuredAccountNumberRuleDefaults defaults) {
        if (accountNumberFormat != null && accountNumberFormat.isStructuredRuleActive()) {
            return accountNumberFormat.getFormatPattern();
        }
        if (accountNumberFormat != null && StringUtils.isNotBlank(accountNumberFormat.getFormatPattern())) {
            return accountNumberFormat.getFormatPattern();
        }
        return defaults.getFormatPattern();
    }

    private AccountNumberSequenceScope resolveSequenceScope(final AccountNumberFormat accountNumberFormat,
            final StructuredAccountNumberRuleDefaults defaults) {
        if (accountNumberFormat != null && accountNumberFormat.getSequenceScope() != null) {
            return accountNumberFormat.getSequenceScope();
        }
        return defaults.getSequenceScope();
    }

    private CheckDigitAlgorithm resolveCheckDigitAlgorithm(final AccountNumberFormat accountNumberFormat,
            final StructuredAccountNumberRuleDefaults defaults) {
        if (accountNumberFormat != null && accountNumberFormat.getCheckDigitAlgorithm() != null) {
            return accountNumberFormat.getCheckDigitAlgorithm();
        }
        return defaults.getCheckDigitAlgorithm();
    }

    private String normalizeProductCode(final String shortName) {
        if (StringUtils.isBlank(shortName)) {
            return "00";
        }
        return shortName.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
    }

    private String resolveClientTypeCode(final CodeValue clientType) {
        if (clientType == null || StringUtils.isBlank(clientType.getLabel())) {
            return "0";
        }
        return resolveClientTypeCode(clientType.getLabel());
    }

    private String resolveClientTypeCode(final String clientTypeLabel) {
        if (StringUtils.isBlank(clientTypeLabel)) {
            return "0";
        }
        final String normalized = clientTypeLabel.trim().toUpperCase().replaceAll("[^A-Z0-9]", "");
        return normalized.isEmpty() ? "0" : normalized.substring(0, 1);
    }
}
