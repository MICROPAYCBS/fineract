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
package org.apache.fineract.portfolio.account.service;

import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import lombok.AllArgsConstructor;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormat;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatEnumerations.AccountNumberPrefixType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberGeneratorService;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberStructuredGenerationService;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.service.ConfigurationReadPlatformService;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientRepository;
import org.apache.fineract.portfolio.group.domain.Group;
import org.apache.fineract.portfolio.loanaccount.domain.Loan;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepository;
import org.apache.fineract.portfolio.shareaccounts.domain.ShareAccount;
import org.apache.fineract.portfolio.workingcapitalloan.domain.WorkingCapitalLoan;
import org.apache.fineract.portfolio.workingcapitalloan.repository.WorkingCapitalLoanRepository;
import org.springframework.stereotype.Component;

/**
 * Example {@link AccountNumberGenerator} for clients that takes an entities auto generated database id and zero fills
 * it ensuring the identifier is always of a given <code>maxLength</code>.
 */
@Component
@AllArgsConstructor
public class AccountNumberGenerator implements AccountNumberGeneratorService {

    private static final int maxLength = 9;

    private static final String ID = "id";
    private static final String ENTITY_TYPE = "entityType";
    private static final String CLIENT_TYPE = "clientType";
    private static final String OFFICE_NAME = "officeName";
    private static final String LOAN_PRODUCT_SHORT_NAME = "loanProductShortName";
    private static final String SAVINGS_PRODUCT_SHORT_NAME = "savingsProductShortName";
    private static final String SHARE_PRODUCT_SHORT_NAME = "sharesProductShortName";
    private static final String PREFIX_SHORT_NAME = "prefixShortName";
    private final ConfigurationReadPlatformService configurationReadPlatformService;
    private final AccountNumberStructuredGenerationService accountNumberStructuredGenerationService;
    private final ClientRepository clientRepository;
    private final LoanRepository loanRepository;
    private final SavingsAccountRepository savingsAccountRepository;
    private final WorkingCapitalLoanRepository workingCapitalLoanRepository;

    public String generate(Client client, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return accountNumberStructuredGenerationService.generate(EntityAccountType.CLIENT, client, accountNumberFormat, false);
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, client.getId().toString());
        propertyMap.put(OFFICE_NAME, client.getOffice().getName());
        propertyMap.put(ENTITY_TYPE, "client");
        CodeValue clientType = client.clientType();
        if (clientType != null) {
            propertyMap.put(CLIENT_TYPE, clientType.getLabel());
        }
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }

    public String generate(Loan loan, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return accountNumberStructuredGenerationService.generate(EntityAccountType.LOAN, loan, accountNumberFormat, false);
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, loan.getId().toString());
        propertyMap.put(OFFICE_NAME, loan.getOffice().getName());
        propertyMap.put(LOAN_PRODUCT_SHORT_NAME, loan.loanProduct().getShortName());
        propertyMap.put(ENTITY_TYPE, "loan");
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }

    public String generate(SavingsAccount savingsAccount, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return generateUniqueStructuredNumber(EntityAccountType.SAVINGS, savingsAccount, accountNumberFormat, accountNumber -> {
                final SavingsAccount existing = this.savingsAccountRepository.findSavingsAccountByAccountNumber(accountNumber);
                return existing != null && (savingsAccount.getId() == null || !savingsAccount.getId().equals(existing.getId()));
            });
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, savingsAccount.getId().toString());
        propertyMap.put(OFFICE_NAME, savingsAccount.office().getName());
        propertyMap.put(SAVINGS_PRODUCT_SHORT_NAME, savingsAccount.savingsProduct().getShortName());
        propertyMap.put(ENTITY_TYPE, "savingsAccount");
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }

    public String generate(ShareAccount shareaccount, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return accountNumberStructuredGenerationService.generate(EntityAccountType.SHARES, shareaccount, accountNumberFormat, false);
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, shareaccount.getId().toString());
        propertyMap.put(SHARE_PRODUCT_SHORT_NAME, shareaccount.getShareProduct().getShortName());
        propertyMap.put(ENTITY_TYPE, "shareAccount");
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }

    public String generate(WorkingCapitalLoan wcl, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return accountNumberStructuredGenerationService.generate(EntityAccountType.WORKING_CAPITAL_LOAN, wcl, accountNumberFormat,
                    false);
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, wcl.getId().toString());
        propertyMap.put(OFFICE_NAME,
                wcl.getClient() != null && wcl.getClient().getOffice() != null ? wcl.getClient().getOffice().getName() : "");
        propertyMap.put(LOAN_PRODUCT_SHORT_NAME, wcl.getLoanProduct() != null ? wcl.getLoanProduct().getShortName() : "");
        propertyMap.put(ENTITY_TYPE, "workingCapitalLoan");
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }

    @Override
    public String generate(EntityAccountType type, Object entity, AccountNumberFormat format) {
        return switch (type) {
            case CLIENT -> generate((Client) entity, format);
            case LOAN -> generate((Loan) entity, format);
            case SAVINGS -> generate((SavingsAccount) entity, format);
            case SHARES -> generate((ShareAccount) entity, format);
            case WORKING_CAPITAL_LOAN -> generate((WorkingCapitalLoan) entity, format);
            case CENTER, GROUP ->
                throw new UnsupportedOperationException("Use generateCenterAccountNumber / generateGroupAccountNumber for " + type);
        };
    }

    private boolean useStructuredGeneration(final AccountNumberFormat accountNumberFormat) {
        final GlobalConfigurationPropertyData structuredFormats = this.configurationReadPlatformService
                .retrieveGlobalConfiguration("structured-account-number-formats");
        return structuredFormats.isEnabled() && accountNumberFormat != null && Boolean.TRUE.equals(accountNumberFormat.getStructuredEnabled());
    }

    private String generateAccountNumber(Map<String, String> propertyMap, AccountNumberFormat accountNumberFormat) {
        int accountMaxLength = AccountNumberGenerator.maxLength;
        String accountNumber = StringUtils.leftPad(propertyMap.get(ID), accountMaxLength, '0');

        // find if the custom length is defined
        final GlobalConfigurationPropertyData customLength = this.configurationReadPlatformService
                .retrieveGlobalConfiguration(GlobalConfigurationConstants.CUSTOM_ACCOUNT_NUMBER_LENGTH);

        if (customLength.isEnabled()) {
            // if it is enabled, and has the value, get it from the repository.
            if (customLength.getValue() != null) {
                accountMaxLength = customLength.getValue().intValue();
            }
        }

        final GlobalConfigurationPropertyData randomAccountNumber = this.configurationReadPlatformService
                .retrieveGlobalConfiguration(GlobalConfigurationConstants.RANDOM_ACCOUNT_NUMBER);

        if (randomAccountNumber.isEnabled()) {
            accountNumber = randomNumberGenerator(accountMaxLength, propertyMap);
        }

        accountNumber = StringUtils.leftPad(accountNumber, accountMaxLength, '0');
        if (accountNumberFormat != null && accountNumberFormat.getPrefixEnum() != null) {
            AccountNumberPrefixType accountNumberPrefixType = AccountNumberPrefixType.fromInt(accountNumberFormat.getPrefixEnum());
            String prefix = null;
            switch (accountNumberPrefixType) {
                case CLIENT_TYPE:
                    prefix = propertyMap.get(CLIENT_TYPE);
                break;

                case OFFICE_NAME:
                    prefix = propertyMap.get(OFFICE_NAME);
                break;

                case LOAN_PRODUCT_SHORT_NAME:
                    prefix = propertyMap.get(LOAN_PRODUCT_SHORT_NAME);
                break;

                case SAVINGS_PRODUCT_SHORT_NAME:
                    prefix = propertyMap.get(SAVINGS_PRODUCT_SHORT_NAME);
                break;

                case PREFIX_SHORT_NAME:
                    if (StringUtils.isBlank(accountNumberFormat.getPrefixCharacter())) {
                        break;
                    }
                    generatePrefix(propertyMap, propertyMap.get(ID), accountMaxLength, accountNumberFormat);
                    prefix = propertyMap.get(PREFIX_SHORT_NAME);
                break;
            }

            // FINERACT-590
            // Because account_no is limited to 20 chars, we can only use the
            // first 10 chars of prefix - trim if necessary
            if (prefix != null) {
                prefix = prefix.substring(0, Math.min(prefix.length(), 10));
            }
            if (accountNumberPrefixType == AccountNumberPrefixType.PREFIX_SHORT_NAME && prefix != null) {
                Integer prefixLength = prefix.length();

                if (randomAccountNumber.isEnabled()) {
                    accountNumber = accountNumber.substring(prefixLength);
                } else {
                    Integer numberLength = accountMaxLength - prefixLength;
                    accountNumber = StringUtils.leftPad(propertyMap.get(ID), numberLength, '0');
                }
            } else {
                accountNumber = StringUtils.leftPad(accountNumber, Integer.valueOf(propertyMap.get(ID).length()), '0');
            }

            accountNumber = StringUtils.overlay(accountNumber, prefix, 0, 0);
        }

        if (randomAccountNumber.isEnabled()) { // calling the main function itself until new randomNo.
            Boolean randomNumberConflict = checkAccountNumberConflict(propertyMap, accountNumberFormat, accountNumber);
            if (randomNumberConflict) {
                accountNumber = generateAccountNumber(propertyMap, accountNumberFormat);
            }
        } else if (Boolean.TRUE.equals(checkAccountNumberConflict(propertyMap, accountNumberFormat, accountNumber))) {
            // Id-based numbers collide when that value is already stored on another account
            // (for example prefix "1" + id 1 => 1000000001). Skip ahead to a free number.
            accountNumber = nextUnusedAccountNumber(propertyMap, accountNumberFormat, accountNumber);
        }
        return accountNumber;
    }

    private String generateUniqueStructuredNumber(final EntityAccountType entityAccountType, final Object entity,
            final AccountNumberFormat accountNumberFormat, final Predicate<String> alreadyUsed) {
        String accountNumber = null;
        for (int attempt = 0; attempt < 50; attempt++) {
            accountNumber = this.accountNumberStructuredGenerationService.generate(entityAccountType, entity, accountNumberFormat, false);
            if (!alreadyUsed.test(accountNumber)) {
                return accountNumber;
            }
        }
        throw new GeneralPlatformDomainRuleException("error.msg.account.number.duplicate",
                "Account number " + accountNumber + " already exists", accountNumber);
    }

    private String nextUnusedAccountNumber(final Map<String, String> propertyMap, final AccountNumberFormat accountNumberFormat,
            final String accountNumber) {
        String candidate = accountNumber;
        for (int attempt = 0; attempt < 100; attempt++) {
            candidate = incrementAccountNumber(candidate);
            if (!Boolean.TRUE.equals(checkAccountNumberConflict(propertyMap, accountNumberFormat, candidate))) {
                return candidate;
            }
        }
        throw new GeneralPlatformDomainRuleException("error.msg.account.number.duplicate",
                "Account number " + accountNumber + " already exists", accountNumber);
    }

    private String incrementAccountNumber(final String accountNumber) {
        if (StringUtils.isNumeric(accountNumber)) {
            final String next = new BigInteger(accountNumber).add(BigInteger.ONE).toString();
            return next.length() < accountNumber.length() ? StringUtils.leftPad(next, accountNumber.length(), '0') : next;
        }
        return accountNumber + "1";
    }

    private String randomNumberGenerator(int accountMaxLength, Map<String, String> propertyMap) {
        String randomNumber = RandomStringUtils.secure().next(accountMaxLength, false, true); // NOSONAR

        BigInteger accNumber = new BigInteger(randomNumber);
        if (accNumber.equals(BigInteger.ZERO)) { // to avoid account no. 00 in randomisation
            randomNumber = randomNumberGenerator(accountMaxLength, propertyMap);
        }

        String accountNumber = randomNumber.substring(0, accountMaxLength);
        return accountNumber;
    }

    public Boolean checkAccountNumberConflict(Map<String, String> propertyMap, AccountNumberFormat accountNumberFormat,
            String accountNumber) {

        String entityType = propertyMap.get(ENTITY_TYPE);
        if (entityType == null) { // No entityType in map -> cannot check for conflicts.
            return false;
        }

        boolean randomNumberConflict = false;

        switch (entityType) {
            case "client": // avoid duplication it will loop until it finds new random account no.
                Client client = this.clientRepository.getClientByAccountNumber(accountNumber);
                if (client != null && conflictsWithAnotherEntity(propertyMap, client.getId())) {
                    randomNumberConflict = true;
                }
            break;

            case "loan":
                Loan loan = this.loanRepository.findLoanAccountByAccountNumber(accountNumber);
                if (loan != null && conflictsWithAnotherEntity(propertyMap, loan.getId())) {
                    randomNumberConflict = true;
                }
            break;

            case "savingsAccount":
                SavingsAccount savingsAccount = this.savingsAccountRepository.findSavingsAccountByAccountNumber(accountNumber);
                if (savingsAccount != null && conflictsWithAnotherEntity(propertyMap, savingsAccount.getId())) {
                    randomNumberConflict = true;
                }
            break;

            case "workingCapitalLoan":
                if (this.workingCapitalLoanRepository.existsByAccountNumber(accountNumber)) {
                    randomNumberConflict = true;
                }
            break;

            default:
            break;
        }

        return randomNumberConflict;
    }

    private boolean conflictsWithAnotherEntity(final Map<String, String> propertyMap, final Long existingEntityId) {
        if (existingEntityId == null) {
            return true;
        }
        final String currentId = propertyMap.get(ID);
        return currentId == null || !currentId.equals(String.valueOf(existingEntityId));
    }

    private Map<String, String> generatePrefix(Map<String, String> propertyMap, String accountNumber, Integer accountMaxLength,
            AccountNumberFormat accountNumberFormat) {

        String prefix = accountNumberFormat.getPrefixCharacter();
        Integer prefixLength = prefix.length();

        Integer totalLength = prefixLength + Integer.valueOf(propertyMap.get(ID).length());

        prefixLength = totalLength - accountMaxLength;

        if (prefixLength > 0) {
            prefix = prefix.substring(0, prefix.length() - prefixLength);
        }

        propertyMap.put(PREFIX_SHORT_NAME, prefix);

        return propertyMap;
    }

    public String generateGroupAccountNumber(Group group, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return accountNumberStructuredGenerationService.generate(EntityAccountType.GROUP, group, accountNumberFormat, false);
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, group.getId().toString());
        propertyMap.put(OFFICE_NAME, group.getOffice().getName());
        propertyMap.put(ENTITY_TYPE, "group");
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }

    public String generateCenterAccountNumber(Group group, AccountNumberFormat accountNumberFormat) {
        if (useStructuredGeneration(accountNumberFormat)) {
            return accountNumberStructuredGenerationService.generate(EntityAccountType.CENTER, group, accountNumberFormat, false);
        }
        Map<String, String> propertyMap = new HashMap<>();
        propertyMap.put(ID, group.getId().toString());
        propertyMap.put(OFFICE_NAME, group.getOffice().getName());
        propertyMap.put(ENTITY_TYPE, "center");
        return generateAccountNumber(propertyMap, accountNumberFormat);
    }
}
