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
package org.apache.fineract.portfolio.customerclass.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.customerclass.domain.CustomerClass;
import org.apache.fineract.portfolio.customerclass.domain.CustomerClassRepository;
import org.apache.fineract.portfolio.customerclass.exception.CustomerClassNotFoundException;
import org.apache.fineract.portfolio.customerclass.serialization.CustomerClassCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CustomerClassWritePlatformServiceImpl implements CustomerClassWritePlatformService {

    private final PlatformSecurityContext context;
    private final CustomerClassRepository customerClassRepository;
    private final CustomerClassCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createCustomerClass(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String classCode = this.fromApiJsonHelper.extractStringNamed(CustomerClassCommandFromApiJsonDeserializer.CLASS_CODE, json);
        if (this.customerClassRepository.existsByClassCode(classCode)) {
            throw new PlatformDataIntegrityException("error.msg.customer.class.duplicate.code",
                    "Customer class with code `" + classCode + "` already exists.", "classCode", classCode);
        }

        final CustomerClass customerClass = mapFromJson(new CustomerClass(), json, true);
        this.customerClassRepository.saveAndFlush(customerClass);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(customerClass.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateCustomerClass(final Long customerClassId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(customerClassId, command.json());

        final CustomerClass customerClass = this.customerClassRepository.findById(customerClassId)
                .orElseThrow(() -> new CustomerClassNotFoundException(customerClassId));

        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(CustomerClassCommandFromApiJsonDeserializer.CLASS_CODE)) {
            final String classCode = this.fromApiJsonHelper.extractStringNamed(CustomerClassCommandFromApiJsonDeserializer.CLASS_CODE,
                    json);
            if (this.customerClassRepository.existsByClassCodeAndIdNot(classCode, customerClassId)) {
                throw new PlatformDataIntegrityException("error.msg.customer.class.duplicate.code",
                        "Customer class with code `" + classCode + "` already exists.", "classCode", classCode);
            }
        }

        mapFromJson(customerClass, json, false);
        this.customerClassRepository.saveAndFlush(customerClass);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(customerClass.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteCustomerClass(final Long customerClassId, final JsonCommand command) {
        this.context.authenticatedUser();
        final CustomerClass customerClass = this.customerClassRepository.findById(customerClassId)
                .orElseThrow(() -> new CustomerClassNotFoundException(customerClassId));
        try {
            this.customerClassRepository.delete(customerClass);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.customer.class.in.use",
                    "Customer class cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(customerClassId).build();
    }

    private CustomerClass mapFromJson(final CustomerClass customerClass, final JsonObject json, final boolean create) {
        if (create || json.has(CustomerClassCommandFromApiJsonDeserializer.CLASS_CODE)) {
            customerClass.setClassCode(
                    this.fromApiJsonHelper.extractStringNamed(CustomerClassCommandFromApiJsonDeserializer.CLASS_CODE, json));
        }
        if (create || json.has(CustomerClassCommandFromApiJsonDeserializer.CLASS_NAME)) {
            customerClass.setClassName(
                    this.fromApiJsonHelper.extractStringNamed(CustomerClassCommandFromApiJsonDeserializer.CLASS_NAME, json));
        }
        patchString(json, CustomerClassCommandFromApiJsonDeserializer.DESCRIPTION, customerClass::setDescription);
        patchString(json, CustomerClassCommandFromApiJsonDeserializer.CUSTOMER_TYPE, customerClass::setCustomerType);
        patchString(json, CustomerClassCommandFromApiJsonDeserializer.RISK_LEVEL, customerClass::setRiskLevel);
        patchString(json, CustomerClassCommandFromApiJsonDeserializer.KYC_LEVEL, customerClass::setKycLevel);
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.LOAN_ELIGIBLE, customerClass::setLoanEligible);
        if (json.has(CustomerClassCommandFromApiJsonDeserializer.RESTRICTION_ID)) {
            customerClass.setRestrictionId(
                    this.fromApiJsonHelper.extractLongNamed(CustomerClassCommandFromApiJsonDeserializer.RESTRICTION_ID, json));
        }
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.OVERDRAFT_ALLOWED, customerClass::setOverdraftAllowed);
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.ENHANCED_DUE_DILIGENCE, customerClass::setEnhancedDueDiligence);
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.RECLASSIFICATION_ALLOWED,
                customerClass::setReclassificationAllowed);
        if (json.has(CustomerClassCommandFromApiJsonDeserializer.MIN_AGE)) {
            customerClass.setMinAge(
                    this.fromApiJsonHelper.extractIntegerSansLocaleNamed(CustomerClassCommandFromApiJsonDeserializer.MIN_AGE, json));
        }
        if (json.has(CustomerClassCommandFromApiJsonDeserializer.MAX_AGE)) {
            customerClass.setMaxAge(
                    this.fromApiJsonHelper.extractIntegerSansLocaleNamed(CustomerClassCommandFromApiJsonDeserializer.MAX_AGE, json));
        }
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.ENFORCE_CUST_PHOTO, customerClass::setEnforceCustPhoto);
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.ENFORCE_CUST_SIGNATURE, customerClass::setEnforceCustSignature);
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.ENFORCE_CUST_DOCUMENT, customerClass::setEnforceCustDocument);
        patchBoolean(json, CustomerClassCommandFromApiJsonDeserializer.AUTO_CREATE_ACCOUNT, customerClass::setAutoCreateAccount);
        if (create || json.has(CustomerClassCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(CustomerClassCommandFromApiJsonDeserializer.STATUS, json);
            if (status != null) {
                customerClass.setStatus(status);
            }
        }
        return customerClass;
    }

    private void patchString(final JsonObject json, final String paramName, final java.util.function.Consumer<String> setter) {
        if (json.has(paramName)) {
            setter.accept(this.fromApiJsonHelper.extractStringNamed(paramName, json));
        }
    }

    private void patchBoolean(final JsonObject json, final String paramName, final java.util.function.Consumer<String> setter) {
        if (json.has(paramName)) {
            final Boolean value = this.fromApiJsonHelper.extractBooleanNamed(paramName, json);
            setter.accept(Boolean.TRUE.equals(value) ? "Y" : "N");
        }
    }
}
