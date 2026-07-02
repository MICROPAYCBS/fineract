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
package org.apache.fineract.portfolio.subindustry.service;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.industry.domain.IndustryRepository;
import org.apache.fineract.portfolio.industry.exception.IndustryNotFoundException;
import org.apache.fineract.portfolio.subindustry.domain.SubIndustry;
import org.apache.fineract.portfolio.subindustry.domain.SubIndustryRepository;
import org.apache.fineract.portfolio.subindustry.exception.SubIndustryNotFoundException;
import org.apache.fineract.portfolio.subindustry.serialization.SubIndustryCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SubIndustryWritePlatformServiceImpl implements SubIndustryWritePlatformService {

    private final PlatformSecurityContext context;
    private final SubIndustryRepository subIndustryRepository;
    private final IndustryRepository industryRepository;
    private final SubIndustryCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createSubIndustry(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String subIndustryCode = this.fromApiJsonHelper
                .extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE, json);
        if (this.subIndustryRepository.existsBySubIndustryCode(subIndustryCode)) {
            throw new PlatformDataIntegrityException("error.msg.subindustry.duplicate.code",
                    "Sub-industry with code `" + subIndustryCode + "` already exists.",
                    SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE, subIndustryCode);
        }

        final SubIndustry subIndustry = mapFromJson(new SubIndustry(), json, true);
        validateIndustry(subIndustry.getIndustryId());
        this.subIndustryRepository.saveAndFlush(subIndustry);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(subIndustry.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateSubIndustry(final Long subIndustryId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(subIndustryId, command.json());

        final SubIndustry subIndustry = findWithNotFoundDetection(subIndustryId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE)) {
            final String subIndustryCode = this.fromApiJsonHelper
                    .extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE, json);
            if (this.subIndustryRepository.existsBySubIndustryCodeAndIdNot(subIndustryCode, subIndustryId)) {
                throw new PlatformDataIntegrityException("error.msg.subindustry.duplicate.code",
                        "Sub-industry with code `" + subIndustryCode + "` already exists.",
                        SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE, subIndustryCode);
            }
        }
        mapFromJson(subIndustry, json, false);
        validateIndustry(subIndustry.getIndustryId());
        this.subIndustryRepository.saveAndFlush(subIndustry);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(subIndustry.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteSubIndustry(final Long subIndustryId, final JsonCommand command) {
        this.context.authenticatedUser();
        final SubIndustry subIndustry = findWithNotFoundDetection(subIndustryId);
        try {
            this.subIndustryRepository.delete(subIndustry);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.subindustry.in.use",
                    "Sub-industry cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(subIndustryId).build();
    }

    @Override
    public SubIndustry findWithNotFoundDetection(final Long subIndustryId) {
        return this.subIndustryRepository.findById(subIndustryId).orElseThrow(() -> new SubIndustryNotFoundException(subIndustryId));
    }

    private void validateIndustry(final Long industryId) {
        if (industryId == null) {
            return;
        }
        if (!this.industryRepository.existsById(industryId)) {
            throw new IndustryNotFoundException(industryId);
        }
    }

    private SubIndustry mapFromJson(final SubIndustry subIndustry, final JsonObject json, final boolean create) {
        if (create || json.has(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE)) {
            subIndustry.setSubIndustryCode(
                    this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_CODE, json));
        }
        if (create || json.has(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_NAME)) {
            subIndustry.setSubIndustryName(
                    this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.SUB_INDUSTRY_NAME, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.DESCRIPTION)) {
            subIndustry.setDescription(
                    this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.DESCRIPTION, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.INDUSTRY_ID)) {
            subIndustry.setIndustryId(this.fromApiJsonHelper.extractLongNamed(SubIndustryCommandFromApiJsonDeserializer.INDUSTRY_ID, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.REGULATORY_CODE)) {
            subIndustry.setRegulatoryCode(
                    this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.REGULATORY_CODE, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.RISK_LEVEL)) {
            subIndustry.setRiskLevel(this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.RISK_LEVEL, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.AML_RISK_LEVEL)) {
            subIndustry.setAmlRiskLevel(
                    this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.AML_RISK_LEVEL, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.CREDIT_RISK_LEVEL)) {
            subIndustry.setCreditRiskLevel(
                    this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.CREDIT_RISK_LEVEL, json));
        }
        patchBoolean(json, SubIndustryCommandFromApiJsonDeserializer.PRIORITY, subIndustry::setPriorityFlag);
        patchBoolean(json, SubIndustryCommandFromApiJsonDeserializer.PROHIBITED, subIndustry::setProhibitedFlag);
        patchBoolean(json, SubIndustryCommandFromApiJsonDeserializer.REQUIRES_EDD, subIndustry::setRequiresEdd);
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.EXPOSURE_LIMIT)) {
            subIndustry.setExposureLimit(
                    this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(SubIndustryCommandFromApiJsonDeserializer.EXPOSURE_LIMIT,
                            json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MIN)) {
            subIndustry.setExpectedTurnoverMin(this.fromApiJsonHelper
                    .extractBigDecimalWithLocaleNamed(SubIndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MIN, json));
        }
        if (json.has(SubIndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MAX)) {
            subIndustry.setExpectedTurnoverMax(this.fromApiJsonHelper
                    .extractBigDecimalWithLocaleNamed(SubIndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MAX, json));
        }
        if (create || json.has(SubIndustryCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(SubIndustryCommandFromApiJsonDeserializer.STATUS, json);
            if (status != null) {
                subIndustry.setStatus(status);
            }
        }
        return subIndustry;
    }

    private void patchBoolean(final JsonObject json, final String paramName, final java.util.function.Consumer<String> setter) {
        if (json.has(paramName)) {
            final Boolean value = this.fromApiJsonHelper.extractBooleanNamed(paramName, json);
            setter.accept(Boolean.TRUE.equals(value) ? "Y" : "N");
        }
    }
}
