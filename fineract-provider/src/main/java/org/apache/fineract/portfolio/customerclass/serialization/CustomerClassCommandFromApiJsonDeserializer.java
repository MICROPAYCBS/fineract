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
package org.apache.fineract.portfolio.customerclass.serialization;

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
import org.apache.fineract.portfolio.client.domain.LegalForm;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public final class CustomerClassCommandFromApiJsonDeserializer {

    public static final String CLASS_CODE = "classCode";
    public static final String CLASS_NAME = "className";
    public static final String DESCRIPTION = "description";
    public static final String LEGAL_FORM_ID = "legalFormId";
    public static final String CUSTOMER_TYPE = "customerType";
    public static final String RISK_LEVEL = "riskLevel";
    public static final String KYC_LEVEL = "kycLevel";
    public static final String LOAN_ELIGIBLE = "loanEligible";
    public static final String RESTRICTION_ID = "restrictionId";
    public static final String OVERDRAFT_ALLOWED = "overdraftAllowed";
    public static final String ENHANCED_DUE_DILIGENCE = "enhancedDueDiligence";
    public static final String RECLASSIFICATION_ALLOWED = "reclassificationAllowed";
    public static final String MIN_AGE = "minAge";
    public static final String MAX_AGE = "maxAge";
    public static final String ENFORCE_CUST_PHOTO = "enforceCustPhoto";
    public static final String ENFORCE_CUST_SIGNATURE = "enforceCustSignature";
    public static final String ENFORCE_CUST_DOCUMENT = "enforceCustDocument";
    public static final String AUTO_CREATE_ACCOUNT = "autoCreateAccount";
    public static final String STATUS = "status";
    public static final String RESOURCE = "CustomerClass";

    private static final Set<String> SUPPORTED_PARAMETERS = new HashSet<>(Arrays.asList(CLASS_CODE, CLASS_NAME, DESCRIPTION, LEGAL_FORM_ID,
            CUSTOMER_TYPE, RISK_LEVEL, KYC_LEVEL, LOAN_ELIGIBLE, RESTRICTION_ID, OVERDRAFT_ALLOWED, ENHANCED_DUE_DILIGENCE,
            RECLASSIFICATION_ALLOWED, MIN_AGE, MAX_AGE, ENFORCE_CUST_PHOTO, ENFORCE_CUST_SIGNATURE, ENFORCE_CUST_DOCUMENT,
            AUTO_CREATE_ACCOUNT, STATUS));

    private static final List<Integer> LEGAL_FORM_IDS = List.of(LegalForm.PERSON.getValue(), LegalForm.ENTITY.getValue());
    private static final List<String> CUSTOMER_TYPES = List.of("INDIVIDUAL", "CORPORATE", "GROUP", "JOINT");
    private static final List<String> SEGMENT_CUSTOMER_TYPES = List.of("GROUP", "JOINT");
    private static final List<String> RISK_LEVELS = List.of("LOW", "MEDIUM", "HIGH");
    private static final List<String> KYC_LEVELS = List.of("BASIC", "STANDARD", "ENHANCED");
    private static final List<String> STATUSES = List.of("ACTIVE", "INACTIVE");

    private final FromJsonHelper fromApiJsonHelper;

    public void validateForCreate(final String json) {
        validateSupported(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, baseDataValidator, true);
        throwValidationErrors(dataValidationErrors);
    }

    public void validateForUpdate(final Long customerClassId, final String json) {
        validateSupported(json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        baseDataValidator.reset().parameter("id").value(customerClassId).notNull().integerGreaterThanZero();
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        validateCommonFields(element, baseDataValidator, false);
        throwValidationErrors(dataValidationErrors);
    }

    private void validateCommonFields(final JsonElement element, final DataValidatorBuilder baseDataValidator, final boolean create) {
        if (create || this.fromApiJsonHelper.parameterExists(CLASS_CODE, element)) {
            final String classCode = this.fromApiJsonHelper.extractStringNamed(CLASS_CODE, element);
            baseDataValidator.reset().parameter(CLASS_CODE).value(classCode).notBlank().notExceedingLengthOf(20);
        }
        if (create || this.fromApiJsonHelper.parameterExists(CLASS_NAME, element)) {
            final String className = this.fromApiJsonHelper.extractStringNamed(CLASS_NAME, element);
            baseDataValidator.reset().parameter(CLASS_NAME).value(className).notBlank().notExceedingLengthOf(100);
        }
        if (this.fromApiJsonHelper.parameterExists(DESCRIPTION, element)) {
            final String description = this.fromApiJsonHelper.extractStringNamed(DESCRIPTION, element);
            baseDataValidator.reset().parameter(DESCRIPTION).value(description).ignoreIfNull().notExceedingLengthOf(255);
        }
        if (create || this.fromApiJsonHelper.parameterExists(LEGAL_FORM_ID, element)) {
            final Integer legalFormId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LEGAL_FORM_ID, element);
            baseDataValidator.reset().parameter(LEGAL_FORM_ID).value(legalFormId).notNull().isOneOfTheseValues(LEGAL_FORM_IDS);
        }
        if (this.fromApiJsonHelper.parameterExists(CUSTOMER_TYPE, element)) {
            final String customerType = this.fromApiJsonHelper.extractStringNamed(CUSTOMER_TYPE, element);
            baseDataValidator.reset().parameter(CUSTOMER_TYPE).value(customerType).ignoreIfNull()
                    .isOneOfTheseStringValues(create ? SEGMENT_CUSTOMER_TYPES : CUSTOMER_TYPES);
        }
        if (this.fromApiJsonHelper.parameterExists(RISK_LEVEL, element)) {
            final String riskLevel = this.fromApiJsonHelper.extractStringNamed(RISK_LEVEL, element);
            baseDataValidator.reset().parameter(RISK_LEVEL).value(riskLevel).ignoreIfNull().isOneOfTheseStringValues(RISK_LEVELS);
        }
        if (this.fromApiJsonHelper.parameterExists(KYC_LEVEL, element)) {
            final String kycLevel = this.fromApiJsonHelper.extractStringNamed(KYC_LEVEL, element);
            baseDataValidator.reset().parameter(KYC_LEVEL).value(kycLevel).ignoreIfNull().isOneOfTheseStringValues(KYC_LEVELS);
        }
        validateBooleanField(element, baseDataValidator, LOAN_ELIGIBLE);
        if (this.fromApiJsonHelper.parameterExists(RESTRICTION_ID, element)) {
            final Long restrictionId = this.fromApiJsonHelper.extractLongNamed(RESTRICTION_ID, element);
            baseDataValidator.reset().parameter(RESTRICTION_ID).value(restrictionId).ignoreIfNull().integerGreaterThanZero();
        }
        validateBooleanField(element, baseDataValidator, OVERDRAFT_ALLOWED);
        validateBooleanField(element, baseDataValidator, ENHANCED_DUE_DILIGENCE);
        validateBooleanField(element, baseDataValidator, RECLASSIFICATION_ALLOWED);
        if (this.fromApiJsonHelper.parameterExists(MIN_AGE, element)) {
            final Integer minAge = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(MIN_AGE, element);
            baseDataValidator.reset().parameter(MIN_AGE).value(minAge).ignoreIfNull().integerZeroOrGreater();
        }
        if (this.fromApiJsonHelper.parameterExists(MAX_AGE, element)) {
            final Integer maxAge = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(MAX_AGE, element);
            baseDataValidator.reset().parameter(MAX_AGE).value(maxAge).ignoreIfNull().integerGreaterThanZero();
        }
        validateBooleanField(element, baseDataValidator, ENFORCE_CUST_PHOTO);
        validateBooleanField(element, baseDataValidator, ENFORCE_CUST_SIGNATURE);
        validateBooleanField(element, baseDataValidator, ENFORCE_CUST_DOCUMENT);
        validateBooleanField(element, baseDataValidator, AUTO_CREATE_ACCOUNT);
        if (create || this.fromApiJsonHelper.parameterExists(STATUS, element)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(STATUS, element);
            baseDataValidator.reset().parameter(STATUS).value(status).ignoreIfNull().isOneOfTheseStringValues(STATUSES);
        }
        validateAgeRange(element, baseDataValidator);
        validateAgeForLegalForm(element, baseDataValidator);
    }

    private void validateAgeForLegalForm(final JsonElement element, final DataValidatorBuilder baseDataValidator) {
        if (!this.fromApiJsonHelper.parameterExists(LEGAL_FORM_ID, element)) {
            return;
        }
        final Integer legalFormId = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(LEGAL_FORM_ID, element);
        if (!LegalForm.ENTITY.getValue().equals(legalFormId)) {
            return;
        }
        if (this.fromApiJsonHelper.parameterExists(MIN_AGE, element)) {
            final Integer minAge = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(MIN_AGE, element);
            if (minAge != null) {
                baseDataValidator.reset().parameter(MIN_AGE).failWithCode("not.supported.for.entity.legal.form");
            }
        }
        if (this.fromApiJsonHelper.parameterExists(MAX_AGE, element)) {
            final Integer maxAge = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(MAX_AGE, element);
            if (maxAge != null) {
                baseDataValidator.reset().parameter(MAX_AGE).failWithCode("not.supported.for.entity.legal.form");
            }
        }
    }

    private void validateAgeRange(final JsonElement element, final DataValidatorBuilder baseDataValidator) {
        if (this.fromApiJsonHelper.parameterExists(MIN_AGE, element) && this.fromApiJsonHelper.parameterExists(MAX_AGE, element)) {
            final Integer minAge = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(MIN_AGE, element);
            final Integer maxAge = this.fromApiJsonHelper.extractIntegerSansLocaleNamed(MAX_AGE, element);
            if (minAge != null && maxAge != null && minAge > maxAge) {
                baseDataValidator.reset().parameter(MAX_AGE).failWithCode("must.be.greater.than.or.equal.to.minAge");
            }
        }
    }

    private void validateBooleanField(final JsonElement element, final DataValidatorBuilder baseDataValidator, final String paramName) {
        if (this.fromApiJsonHelper.parameterExists(paramName, element)) {
            final Boolean value = this.fromApiJsonHelper.extractBooleanNamed(paramName, element);
            baseDataValidator.reset().parameter(paramName).value(value).ignoreIfNull().validateForBooleanValue();
        }
    }

    private void validateSupported(final String json) {
        if (StringUtils.isBlank(json)) {
            throw new InvalidJsonException();
        }
        final Type typeOfMap = new TypeToken<Map<String, Object>>() {}.getType();
        final Map<String, Object> request = this.fromApiJsonHelper.extractObjectMap(typeOfMap, json);
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource(RESOURCE);
        for (final String key : request.keySet()) {
            if (!SUPPORTED_PARAMETERS.contains(key)) {
                baseDataValidator.reset().parameter(key).failWithCode("is.not.one.of.expected.parameters");
            }
        }
        throwValidationErrors(dataValidationErrors);
    }

    private void throwValidationErrors(final List<ApiParameterError> dataValidationErrors) {
        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
