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
package org.apache.fineract.portfolio.industry.service;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.industry.domain.Industry;
import org.apache.fineract.portfolio.industry.domain.IndustryRepository;
import org.apache.fineract.portfolio.industry.exception.IndustryNotFoundException;
import org.apache.fineract.portfolio.industry.serialization.IndustryCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.sector.domain.SectorRepository;
import org.apache.fineract.portfolio.sector.exception.SectorNotFoundException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class IndustryWritePlatformServiceImpl implements IndustryWritePlatformService {

    private final PlatformSecurityContext context;
    private final IndustryRepository industryRepository;
    private final SectorRepository sectorRepository;
    private final IndustryCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createIndustry(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String industryCode = this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE,
                json);
        if (this.industryRepository.existsByIndustryCode(industryCode)) {
            throw new PlatformDataIntegrityException("error.msg.industry.duplicate.code",
                    "Industry with code `" + industryCode + "` already exists.", IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE,
                    industryCode);
        }

        final Industry industry = mapFromJson(new Industry(), json, true);
        validateSector(industry.getSectorId());
        this.industryRepository.saveAndFlush(industry);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(industry.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateIndustry(final Long industryId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(industryId, command.json());

        final Industry industry = findWithNotFoundDetection(industryId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE)) {
            final String industryCode = this.fromApiJsonHelper
                    .extractStringNamed(IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE, json);
            if (this.industryRepository.existsByIndustryCodeAndIdNot(industryCode, industryId)) {
                throw new PlatformDataIntegrityException("error.msg.industry.duplicate.code",
                        "Industry with code `" + industryCode + "` already exists.",
                        IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE, industryCode);
            }
        }
        mapFromJson(industry, json, false);
        validateSector(industry.getSectorId());
        this.industryRepository.saveAndFlush(industry);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(industry.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteIndustry(final Long industryId, final JsonCommand command) {
        this.context.authenticatedUser();
        final Industry industry = findWithNotFoundDetection(industryId);
        try {
            this.industryRepository.delete(industry);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.industry.in.use",
                    "Industry cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(industryId).build();
    }

    @Override
    public Industry findWithNotFoundDetection(final Long industryId) {
        return this.industryRepository.findById(industryId).orElseThrow(() -> new IndustryNotFoundException(industryId));
    }

    private void validateSector(final Long sectorId) {
        if (sectorId == null) {
            return;
        }
        if (!this.sectorRepository.existsById(sectorId)) {
            throw new SectorNotFoundException(sectorId);
        }
    }

    private Industry mapFromJson(final Industry industry, final JsonObject json, final boolean create) {
        if (create || json.has(IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE)) {
            industry.setIndustryCode(
                    this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.INDUSTRY_CODE, json));
        }
        if (create || json.has(IndustryCommandFromApiJsonDeserializer.INDUSTRY_NAME)) {
            industry.setIndustryName(
                    this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.INDUSTRY_NAME, json));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.DESCRIPTION)) {
            industry.setDescription(
                    this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.DESCRIPTION, json));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.SECTOR_ID)) {
            industry.setSectorId(this.fromApiJsonHelper.extractLongNamed(IndustryCommandFromApiJsonDeserializer.SECTOR_ID, json));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.REGULATORY_CODE)) {
            industry.setRegulatoryCode(
                    this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.REGULATORY_CODE, json));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.RISK_LEVEL)) {
            industry.setRiskLevel(this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.RISK_LEVEL, json));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.AML_RISK_LEVEL)) {
            industry.setAmlRiskLevel(
                    this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.AML_RISK_LEVEL, json));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.CREDIT_RISK_LEVEL)) {
            industry.setCreditRiskLevel(
                    this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.CREDIT_RISK_LEVEL, json));
        }
        patchBoolean(json, IndustryCommandFromApiJsonDeserializer.PRIORITY_INDUSTRY, industry::setPriorityIndustryFlag);
        patchBoolean(json, IndustryCommandFromApiJsonDeserializer.PROHIBITED_INDUSTRY, industry::setProhibitedIndustryFlag);
        patchBoolean(json, IndustryCommandFromApiJsonDeserializer.REQUIRES_EDD, industry::setRequiresEdd);
        if (json.has(IndustryCommandFromApiJsonDeserializer.EXPOSURE_LIMIT)) {
            industry.setExposureLimit(extractBigDecimal(json, IndustryCommandFromApiJsonDeserializer.EXPOSURE_LIMIT));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MIN)) {
            industry.setExpectedTurnoverMin(extractBigDecimal(json, IndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MIN));
        }
        if (json.has(IndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MAX)) {
            industry.setExpectedTurnoverMax(extractBigDecimal(json, IndustryCommandFromApiJsonDeserializer.EXPECTED_TURNOVER_MAX));
        }
        if (create || json.has(IndustryCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(IndustryCommandFromApiJsonDeserializer.STATUS, json);
            if (status != null) {
                industry.setStatus(status);
            }
        }
        return industry;
    }

    private BigDecimal extractBigDecimal(final JsonObject json, final String paramName) {
        return this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(paramName, json);
    }

    private void patchBoolean(final JsonObject json, final String paramName, final java.util.function.Consumer<String> setter) {
        if (json.has(paramName)) {
            final Boolean value = this.fromApiJsonHelper.extractBooleanNamed(paramName, json);
            setter.accept(Boolean.TRUE.equals(value) ? "Y" : "N");
        }
    }
}
