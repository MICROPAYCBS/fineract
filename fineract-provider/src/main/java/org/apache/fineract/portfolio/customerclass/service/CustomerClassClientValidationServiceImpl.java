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

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfile;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfileRepository;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierStatus;
import org.apache.fineract.portfolio.client.domain.LegalForm;
import org.apache.fineract.portfolio.customerclass.domain.CustomerClass;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerClassClientValidationServiceImpl implements CustomerClassClientValidationService {

    private static final String CLIENT_ENTITY_TYPE = "clients";
    private static final String CLIENT_SIGNATURE_DOCUMENT_NAME = "clientSignature";

    private static final Set<String> LOW_RISK_PROFILE_VALUES = Set.of("very low", "low");
    private static final Set<String> MEDIUM_RISK_PROFILE_VALUES = Set.of("medium");
    private static final Set<String> HIGH_RISK_PROFILE_VALUES = Set.of("high", "very high");

    private final JdbcTemplate jdbcTemplate;
    private final ClientComplianceProfileRepository complianceProfileRepository;

    @Override
    public void validateAssignment(final CustomerClass customerClass, final Client client) {
        final List<ApiParameterError> errors = new ArrayList<>();
        validateClassActive(customerClass, errors);
        validateLegalFormEligibility(customerClass, client, errors);
        validateAgeEligibility(customerClass, client, errors);
        validateCustomerTypeEligibility(customerClass, client, errors);
        validateRiskLevelEligibility(customerClass, client, errors);
        validateLinkedRestriction(customerClass, errors);
        validateNotBlacklistedForAccountOpening(client, errors);
        throwIfErrors(errors);
    }

    @Override
    public void validateReclassification(final CustomerClass currentCustomerClass, final CustomerClass newCustomerClass) {
        if (currentCustomerClass == null || newCustomerClass == null) {
            return;
        }
        if (currentCustomerClass.getId().equals(newCustomerClass.getId())) {
            return;
        }
        if (!isYes(currentCustomerClass.getReclassificationAllowed())) {
            final ApiParameterError error = ApiParameterError.parameterError("validation.msg.client.customerClassId.reclassification.not.allowed",
                    "The current customer class does not allow reclassification to another class.", ClientApiConstants.customerClassIdParamName,
                    currentCustomerClass.getClassCode());
            throw new PlatformApiDataValidationException(List.of(error));
        }
    }

    @Override
    public void validateReadinessForActivation(final CustomerClass customerClass, final Client client) {
        final List<ApiParameterError> errors = new ArrayList<>();
        if (isYes(customerClass.getEnforceCustPhoto()) && client.getImageId() == null) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClass.photo.required",
                    "Customer class `" + customerClass.getClassName() + "` requires a profile photo before activation.",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
        if (isYes(customerClass.getEnforceCustSignature()) && !hasClientSignatureDocument(client.getId())) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClass.signature.required",
                    "Customer class `" + customerClass.getClassName() + "` requires a customer signature before activation.",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
        if (isYes(customerClass.getEnforceCustDocument()) && countActiveClientIdentifiers(client.getId()) == 0) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClass.document.required",
                    "Customer class `" + customerClass.getClassName() + "` requires at least one identification document before activation.",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
        validateKycLevelReadiness(customerClass, client, errors);
        if (isYes(customerClass.getEnhancedDueDiligence())) {
            validateEnhancedDueDiligenceReadiness(client, errors, customerClass.getClassName());
        }
        throwIfErrors(errors);
    }

    private void validateClassActive(final CustomerClass customerClass, final List<ApiParameterError> errors) {
        if (!"ACTIVE".equalsIgnoreCase(customerClass.getStatus())) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.inactive",
                    "Only an active customer class can be assigned to a client.", ClientApiConstants.customerClassIdParamName,
                    customerClass.getId()));
        }
    }

    private void validateAgeEligibility(final CustomerClass customerClass, final Client client, final List<ApiParameterError> errors) {
        if (LegalForm.ENTITY.getValue().equals(customerClass.getLegalFormEnum())) {
            return;
        }
        final Integer minAge = customerClass.getMinAge();
        final Integer maxAge = customerClass.getMaxAge();
        if (minAge == null && maxAge == null) {
            return;
        }
        final LocalDate dateOfBirth = client.getDateOfBirth();
        if (dateOfBirth == null) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.dateOfBirth.required",
                    "Date of birth is required to assign customer class `" + customerClass.getClassName() + "`.",
                    ClientApiConstants.dateOfBirthParamName));
            return;
        }
        final int age = Period.between(dateOfBirth, DateUtils.getBusinessLocalDate()).getYears();
        if (minAge != null && age < minAge) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.age.below.minimum",
                    "Customer age " + age + " is below the minimum age " + minAge + " for class `" + customerClass.getClassName() + "`.",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
        if (maxAge != null && age > maxAge) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.age.above.maximum",
                    "Customer age " + age + " is above the maximum age " + maxAge + " for class `" + customerClass.getClassName() + "`.",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
    }

    private void validateLegalFormEligibility(final CustomerClass customerClass, final Client client,
            final List<ApiParameterError> errors) {
        final Integer classLegalForm = customerClass.getLegalFormEnum();
        if (classLegalForm == null) {
            return;
        }
        final LegalForm clientLegalForm = LegalForm.fromInt(client.getLegalForm());
        if (clientLegalForm == null) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.legalForm.required",
                    "Legal form is required to assign customer class `" + customerClass.getClassName() + "`.",
                    ClientApiConstants.legalFormIdParamName));
            return;
        }
        if (!classLegalForm.equals(clientLegalForm.getValue())) {
            final LegalForm requiredLegalForm = LegalForm.fromInt(classLegalForm);
            final String requiredLabel = requiredLegalForm != null ? requiredLegalForm.getLabel() : "matching";
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.legalForm.mismatch",
                    "Customer class `" + customerClass.getClassName() + "` is for " + requiredLabel
                            + " customers; this client's legal form is " + clientLegalForm.getLabel() + ".",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
    }

    private void validateCustomerTypeEligibility(final CustomerClass customerClass, final Client client,
            final List<ApiParameterError> errors) {
        final String customerType = customerClass.getCustomerType();
        if (StringUtils.isBlank(customerType)) {
            return;
        }
        switch (customerType.toUpperCase(Locale.ENGLISH)) {
            case "GROUP" -> {
                if (countGroupMemberships(client.getId()) == 0) {
                    errors.add(typeMismatchError(customerClass, "Group customer classes require the client to belong to a group."));
                }
            }
            case "JOINT" -> errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.joint.not.supported",
                    "Joint customer classes are not supported yet.", ClientApiConstants.customerClassIdParamName,
                    customerClass.getClassCode()));
            case "INDIVIDUAL", "CORPORATE" -> {
                // Legacy segment values; legal form eligibility is enforced separately.
            }
            default -> {
                // no-op for unknown future types
            }
        }
    }

    private ApiParameterError typeMismatchError(final CustomerClass customerClass, final String message) {
        return ApiParameterError.parameterError("validation.msg.client.customerClassId.customerType.mismatch", message,
                ClientApiConstants.customerClassIdParamName, customerClass.getClassCode());
    }

    private void validateRiskLevelEligibility(final CustomerClass customerClass, final Client client, final List<ApiParameterError> errors) {
        final String classRiskLevel = customerClass.getRiskLevel();
        if (StringUtils.isBlank(classRiskLevel)) {
            return;
        }
        final CodeValue riskProfile = client.getCustomerRiskProfile();
        if (riskProfile == null || StringUtils.isBlank(riskProfile.getLabel())) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.riskProfile.required",
                    "Customer risk profile is required for customer class `" + customerClass.getClassName() + "`.",
                    ClientApiConstants.customerRiskProfileIdParamName));
            return;
        }
        final String profileValue = riskProfile.getLabel().trim().toLowerCase(Locale.ENGLISH);
        final boolean matches = switch (classRiskLevel.toUpperCase(Locale.ENGLISH)) {
            case "LOW" -> LOW_RISK_PROFILE_VALUES.contains(profileValue);
            case "MEDIUM" -> MEDIUM_RISK_PROFILE_VALUES.contains(profileValue);
            case "HIGH" -> HIGH_RISK_PROFILE_VALUES.contains(profileValue);
            default -> true;
        };
        if (!matches) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.riskLevel.mismatch",
                    "Customer risk profile `" + riskProfile.getLabel() + "` does not match the `" + classRiskLevel
                            + "` risk level required by class `" + customerClass.getClassName() + "`.",
                    ClientApiConstants.customerRiskProfileIdParamName, customerClass.getClassCode()));
        }
    }

    private void validateLinkedRestriction(final CustomerClass customerClass, final List<ApiParameterError> errors) {
        if (customerClass.getRestrictionId() == null) {
            return;
        }
        try {
            final RestrictionSnapshot restriction = this.jdbcTemplate.queryForObject(
                    "SELECT status, start_date, end_date FROM m_restriction WHERE id = ?",
                    (rs, rowNum) -> new RestrictionSnapshot(rs.getString("status"), rs.getObject("start_date", LocalDate.class),
                            rs.getObject("end_date", LocalDate.class)),
                    customerClass.getRestrictionId());
            if (restriction == null || !"ACTIVE".equalsIgnoreCase(restriction.status())) {
                errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.restriction.inactive",
                        "The restriction linked to customer class `" + customerClass.getClassName() + "` is not active.",
                        ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
                return;
            }
            final LocalDate today = DateUtils.getBusinessLocalDate();
            if (restriction.startDate() != null && today.isBefore(restriction.startDate())) {
                errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.restriction.not.effective",
                        "The restriction linked to customer class `" + customerClass.getClassName() + "` is not yet effective.",
                        ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
            }
            if (restriction.endDate() != null && today.isAfter(restriction.endDate())) {
                errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.restriction.expired",
                        "The restriction linked to customer class `" + customerClass.getClassName() + "` has expired.",
                        ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
            }
        } catch (final EmptyResultDataAccessException e) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.restriction.not.found",
                    "The restriction linked to customer class `" + customerClass.getClassName() + "` was not found.",
                    ClientApiConstants.customerClassIdParamName, customerClass.getClassCode()));
        }
    }

    private void validateNotBlacklistedForAccountOpening(final Client client, final List<ApiParameterError> errors) {
        if (client.getId() == null) {
            return;
        }
        final Integer count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_client_blacklist WHERE client_id = ? AND status = 'ACTIVE' AND block_account_opening = 'Y'",
                Integer.class, client.getId());
        if (count != null && count > 0) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.blacklisted",
                    "The client is blacklisted and cannot be assigned a customer class.", ClientApiConstants.customerClassIdParamName));
        }
    }

    private void validateKycLevelReadiness(final CustomerClass customerClass, final Client client, final List<ApiParameterError> errors) {
        final String kycLevel = customerClass.getKycLevel();
        if (StringUtils.isBlank(kycLevel)) {
            return;
        }
        final long identifierCount = countActiveClientIdentifiers(client.getId());
        switch (kycLevel.toUpperCase(Locale.ENGLISH)) {
            case "BASIC" -> {
                if (identifierCount == 0 && StringUtils.isBlank(client.getMobileNo())) {
                    errors.add(kycError(customerClass, "Basic KYC requires a mobile number or identification document."));
                }
            }
            case "STANDARD" -> {
                if (identifierCount == 0) {
                    errors.add(kycError(customerClass, "Standard KYC requires at least one identification document."));
                }
                if (client.getDateOfBirth() == null) {
                    errors.add(kycError(customerClass, "Standard KYC requires date of birth."));
                }
                if (StringUtils.isBlank(client.getMobileNo()) && StringUtils.isBlank(client.getEmailAddress())) {
                    errors.add(kycError(customerClass, "Standard KYC requires a mobile number or email address."));
                }
            }
            case "ENHANCED" -> {
                if (identifierCount == 0) {
                    errors.add(kycError(customerClass, "Enhanced KYC requires at least one identification document."));
                }
                if (client.getDateOfBirth() == null) {
                    errors.add(kycError(customerClass, "Enhanced KYC requires date of birth."));
                }
                if (StringUtils.isBlank(client.getMobileNo()) && StringUtils.isBlank(client.getEmailAddress())) {
                    errors.add(kycError(customerClass, "Enhanced KYC requires a mobile number or email address."));
                }
                if (!hasClientAddress(client.getId())) {
                    errors.add(kycError(customerClass, "Enhanced KYC requires at least one client address."));
                }
            }
            default -> {
                // no-op
            }
        }
    }

    private ApiParameterError kycError(final CustomerClass customerClass, final String message) {
        return ApiParameterError.parameterError("validation.msg.client.customerClassId.kyc.not.met", message,
                ClientApiConstants.customerClassIdParamName, customerClass.getClassCode());
    }

    private void validateEnhancedDueDiligenceReadiness(final Client client, final List<ApiParameterError> errors,
            final String className) {
        final ClientComplianceProfile profile = this.complianceProfileRepository.findByClient_Id(client.getId()).orElse(null);
        if (profile == null) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.edd.profile.required",
                    "Customer class `" + className + "` requires a compliance profile before activation.",
                    ClientApiConstants.complianceProfile));
            return;
        }
        if (isYes(profile.getIsPep()) && StringUtils.isBlank(profile.getPepPosition())) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.edd.pep.incomplete",
                    "PEP position is required on the compliance profile for class `" + className + "`.",
                    ClientApiConstants.complianceProfile));
        }
        if (isYes(profile.getUsCitizenOrResident()) && !isYes(profile.getFatcaRegistered())) {
            errors.add(ApiParameterError.parameterError("validation.msg.client.customerClassId.edd.fatca.required",
                    "FATCA registration is required on the compliance profile for class `" + className + "`.",
                    ClientApiConstants.complianceProfile));
        }
    }

    private int countActiveClientIdentifiers(final Long clientId) {
        if (clientId == null) {
            return 0;
        }
        final Integer count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_client_identifier WHERE client_id = ? AND status = ?",
                Integer.class, clientId, ClientIdentifierStatus.ACTIVE.getValue());
        return count == null ? 0 : count;
    }

    private boolean hasClientSignatureDocument(final Long clientId) {
        if (clientId == null) {
            return false;
        }
        final Integer count = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM m_document WHERE parent_entity_type = ? AND parent_entity_id = ? AND name = ?",
                Integer.class, CLIENT_ENTITY_TYPE, clientId, CLIENT_SIGNATURE_DOCUMENT_NAME);
        return count != null && count > 0;
    }

    private boolean hasClientAddress(final Long clientId) {
        if (clientId == null) {
            return false;
        }
        final Integer count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_client_address WHERE client_id = ?",
                Integer.class, clientId);
        return count != null && count > 0;
    }

    private int countGroupMemberships(final Long clientId) {
        if (clientId == null) {
            return 0;
        }
        final Integer count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_group_client WHERE client_id = ?",
                Integer.class, clientId);
        return count == null ? 0 : count;
    }

    private static boolean isYes(final String value) {
        return "Y".equalsIgnoreCase(value);
    }

    private static void throwIfErrors(final List<ApiParameterError> errors) {
        if (errors.isEmpty()) {
            return;
        }
        if (errors.size() == 1) {
            throw new PlatformApiDataValidationException(errors);
        }
        final String summary = errors.stream().map(ApiParameterError::getDefaultUserMessage).filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(" "));
        throw new PlatformApiDataValidationException("validation.msg.client.customerClass.requirements.not.met", summary, errors);
    }

    private record RestrictionSnapshot(String status, LocalDate startDate, LocalDate endDate) {
    }
}
