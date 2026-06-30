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

package org.apache.fineract.portfolio.client.serialization;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.InvalidJsonException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class ClientComplianceProfileCommandFromApiJsonDeserializer {

    public static final String HAS_OTHER_BANK_ACCOUNTS = "hasOtherBankAccounts";
    public static final String IS_PEP = "isPep";
    public static final String PEP_POSITION = "pepPosition";
    public static final String PEP_RELATIVE_NAME = "pepRelativeName";
    public static final String US_CITIZEN_OR_RESIDENT = "usCitizenOrResident";
    public static final String FATCA_REGISTERED = "fatcaRegistered";
    public static final String FATCA_REGISTRATION_NO = "fatcaRegistrationNo";
    public static final String DPF_ALTERNATIVE_BANK_NAME = "dpfAlternativeBankName";
    public static final String DPF_ALTERNATIVE_ACCOUNT_NUMBER = "dpfAlternativeAccountNumber";
    public static final String OTHER_BANK_ACCOUNTS = "otherBankAccounts";
    public static final String BANK_NAME = "bankName";
    public static final String BRANCH_NAME = "branchName";
    public static final String ACCOUNT_NUMBER = "accountNumber";
    public static final String DISPLAY_ORDER = "displayOrder";
    public static final String COMPLIANCE_PROFILE = "complianceProfile";
    public static final String LOCALE = "locale";
    public static final String RESOURCE = "ComplianceProfile";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(Arrays.asList(HAS_OTHER_BANK_ACCOUNTS, IS_PEP, PEP_POSITION,
            PEP_RELATIVE_NAME, US_CITIZEN_OR_RESIDENT, FATCA_REGISTERED, FATCA_REGISTRATION_NO, DPF_ALTERNATIVE_BANK_NAME,
            DPF_ALTERNATIVE_ACCOUNT_NUMBER, OTHER_BANK_ACCOUNTS, LOCALE, COMPLIANCE_PROFILE, "id", "clientId"));

    private static final Set<String> OTHER_BANK_ACCOUNT_SUPPORTED_PARAMETERS = new HashSet<>(
            Arrays.asList(BANK_NAME, BRANCH_NAME, ACCOUNT_NUMBER, DISPLAY_ORDER, "id"));

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final long clientId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        baseDataValidator.reset().value(clientId).notBlank().integerGreaterThanZero();
        validateCommonFields(json, baseDataValidator);
        throwValidationErrors(dataValidationErrors);
    }

    public void validateForUpdate(final long clientId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        baseDataValidator.reset().value(clientId).notBlank().integerGreaterThanZero();
        validateCommonFields(json, baseDataValidator);
        throwValidationErrors(dataValidationErrors);
    }

    private void validateSupported(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, json, SUPPORTED_PARAMETERS);
    }

    private void validateCommonFields(final String json, final DataValidatorBuilder baseDataValidator) {
        final JsonElement element = this.fromApiJsonHelper.parse(json);

        if (this.fromApiJsonHelper.parameterExists(PEP_POSITION, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(PEP_POSITION, element);
            baseDataValidator.reset().parameter(PEP_POSITION).value(value).ignoreIfNull().notExceedingLengthOf(200);
        }

        if (this.fromApiJsonHelper.parameterExists(PEP_RELATIVE_NAME, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(PEP_RELATIVE_NAME, element);
            baseDataValidator.reset().parameter(PEP_RELATIVE_NAME).value(value).ignoreIfNull().notExceedingLengthOf(200);
        }

        if (this.fromApiJsonHelper.parameterExists(FATCA_REGISTRATION_NO, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(FATCA_REGISTRATION_NO, element);
            baseDataValidator.reset().parameter(FATCA_REGISTRATION_NO).value(value).ignoreIfNull().notExceedingLengthOf(100);
        }

        if (this.fromApiJsonHelper.parameterExists(DPF_ALTERNATIVE_BANK_NAME, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(DPF_ALTERNATIVE_BANK_NAME, element);
            baseDataValidator.reset().parameter(DPF_ALTERNATIVE_BANK_NAME).value(value).ignoreIfNull().notExceedingLengthOf(200);
        }

        if (this.fromApiJsonHelper.parameterExists(DPF_ALTERNATIVE_ACCOUNT_NUMBER, element)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(DPF_ALTERNATIVE_ACCOUNT_NUMBER, element);
            baseDataValidator.reset().parameter(DPF_ALTERNATIVE_ACCOUNT_NUMBER).value(value).ignoreIfNull().notExceedingLengthOf(50);
        }

        final Boolean hasOtherBankAccounts = extractBooleanNamed(HAS_OTHER_BANK_ACCOUNTS, element);
        final Boolean isPep = extractBooleanNamed(IS_PEP, element);
        final Boolean fatcaRegistered = extractBooleanNamed(FATCA_REGISTERED, element);

        int otherBankAccountCount = 0;
        if (Boolean.TRUE.equals(hasOtherBankAccounts) && this.fromApiJsonHelper.parameterExists(OTHER_BANK_ACCOUNTS, element)) {
            final JsonArray otherBankAccounts = element.getAsJsonObject().getAsJsonArray(OTHER_BANK_ACCOUNTS);
            int displayOrder = 0;
            for (JsonElement accountElement : otherBankAccounts) {
                if (isBlankOtherBankAccount(accountElement)) {
                    continue;
                }
                displayOrder++;
                validateOtherBankAccount(accountElement, baseDataValidator, displayOrder);
                otherBankAccountCount++;
            }
            if (otherBankAccountCount > 2) {
                baseDataValidator.reset().parameter(OTHER_BANK_ACCOUNTS).failWithCode("cannot.exceed.max.of.two");
            }
        }

        if (Boolean.TRUE.equals(hasOtherBankAccounts) && otherBankAccountCount == 0) {
            baseDataValidator.reset().parameter(OTHER_BANK_ACCOUNTS).failWithCode("required.when.has.other.bank.accounts.is.true");
        }

        if (Boolean.TRUE.equals(isPep)) {
            final String pepPosition = this.fromApiJsonHelper.extractStringNamed(PEP_POSITION, element);
            baseDataValidator.reset().parameter(PEP_POSITION).value(pepPosition).notBlank().notExceedingLengthOf(200);
        }

        if (Boolean.TRUE.equals(fatcaRegistered)) {
            final String fatcaRegistrationNo = this.fromApiJsonHelper.extractStringNamed(FATCA_REGISTRATION_NO, element);
            baseDataValidator.reset().parameter(FATCA_REGISTRATION_NO).value(fatcaRegistrationNo).notBlank().notExceedingLengthOf(100);
        }
    }

    private boolean isBlankOtherBankAccount(final JsonElement accountElement) {
        final String bankName = this.fromApiJsonHelper.extractStringNamed(BANK_NAME, accountElement);
        final String accountNumber = this.fromApiJsonHelper.extractStringNamed(ACCOUNT_NUMBER, accountElement);
        return StringUtils.isBlank(bankName) && StringUtils.isBlank(accountNumber);
    }

    private void validateOtherBankAccount(final JsonElement accountElement, final DataValidatorBuilder baseDataValidator,
            final int defaultDisplayOrder) {
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        this.fromApiJsonHelper.checkForUnsupportedParameters(typeOfMap, accountElement.toString(), OTHER_BANK_ACCOUNT_SUPPORTED_PARAMETERS);

        final String bankName = this.fromApiJsonHelper.extractStringNamed(BANK_NAME, accountElement);
        baseDataValidator.reset().parameter(BANK_NAME).value(bankName).notBlank().notExceedingLengthOf(200);

        if (this.fromApiJsonHelper.parameterExists(BRANCH_NAME, accountElement)) {
            final String branchName = this.fromApiJsonHelper.extractStringNamed(BRANCH_NAME, accountElement);
            baseDataValidator.reset().parameter(BRANCH_NAME).value(branchName).ignoreIfNull().notExceedingLengthOf(200);
        }

        final String accountNumber = this.fromApiJsonHelper.extractStringNamed(ACCOUNT_NUMBER, accountElement);
        baseDataValidator.reset().parameter(ACCOUNT_NUMBER).value(accountNumber).notBlank().notExceedingLengthOf(50);

        Integer displayOrder = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(DISPLAY_ORDER, accountElement);
        if (displayOrder == null) {
            displayOrder = defaultDisplayOrder;
        }
        baseDataValidator.reset().parameter(DISPLAY_ORDER).value(displayOrder).notNull().inMinMaxRange(1, 2);
    }

    private Boolean extractBooleanNamed(final String parameterName, final JsonElement element) {
        if (this.fromApiJsonHelper.parameterExists(parameterName, element)) {
            return this.fromApiJsonHelper.extractBooleanNamed(parameterName, element);
        }
        return null;
    }

    private void throwValidationErrors(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
