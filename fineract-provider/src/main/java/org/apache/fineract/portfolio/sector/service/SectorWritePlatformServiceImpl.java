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
package org.apache.fineract.portfolio.sector.service;

import com.google.gson.JsonObject;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.sector.domain.Sector;
import org.apache.fineract.portfolio.sector.domain.SectorRepository;
import org.apache.fineract.portfolio.sector.exception.SectorNotFoundException;
import org.apache.fineract.portfolio.sector.serialization.SectorCommandFromApiJsonDeserializer;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SectorWritePlatformServiceImpl implements SectorWritePlatformService {

    private final PlatformSecurityContext context;
    private final SectorRepository sectorRepository;
    private final SectorCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createSector(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final String sectorCode = this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.SECTOR_CODE, json);
        if (this.sectorRepository.existsBySectorCode(sectorCode)) {
            throw new PlatformDataIntegrityException("error.msg.sector.duplicate.code",
                    "Sector with code `" + sectorCode + "` already exists.", SectorCommandFromApiJsonDeserializer.SECTOR_CODE,
                    sectorCode);
        }

        final Sector sector = mapFromJson(new Sector(), json, true);
        validateParent(sector.getId(), sector.getParentId());
        this.sectorRepository.saveAndFlush(sector);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(sector.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateSector(final Long sectorId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(sectorId, command.json());

        final Sector sector = findWithNotFoundDetection(sectorId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        if (json.has(SectorCommandFromApiJsonDeserializer.SECTOR_CODE)) {
            final String sectorCode = this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.SECTOR_CODE, json);
            if (this.sectorRepository.existsBySectorCodeAndIdNot(sectorCode, sectorId)) {
                throw new PlatformDataIntegrityException("error.msg.sector.duplicate.code",
                        "Sector with code `" + sectorCode + "` already exists.", SectorCommandFromApiJsonDeserializer.SECTOR_CODE,
                        sectorCode);
            }
        }
        mapFromJson(sector, json, false);
        validateParent(sectorId, sector.getParentId());
        this.sectorRepository.saveAndFlush(sector);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(sector.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteSector(final Long sectorId, final JsonCommand command) {
        this.context.authenticatedUser();
        final Sector sector = findWithNotFoundDetection(sectorId);
        try {
            this.sectorRepository.delete(sector);
        } catch (final DataIntegrityViolationException e) {
            throw new PlatformDataIntegrityException("error.msg.sector.in.use",
                    "Sector cannot be deleted because it is referenced by other records.");
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(sectorId).build();
    }

    @Override
    public Sector findWithNotFoundDetection(final Long sectorId) {
        return this.sectorRepository.findById(sectorId).orElseThrow(() -> new SectorNotFoundException(sectorId));
    }

    private void validateParent(final Long sectorId, final Long parentId) {
        if (parentId == null) {
            return;
        }
        if (sectorId != null && parentId.equals(sectorId)) {
            throw new PlatformDataIntegrityException("error.msg.sector.invalid.parent",
                    "Sector cannot be its own parent.", SectorCommandFromApiJsonDeserializer.PARENT_ID, parentId);
        }
        if (!this.sectorRepository.existsById(parentId)) {
            throw new SectorNotFoundException(parentId);
        }
    }

    private Sector mapFromJson(final Sector sector, final JsonObject json, final boolean create) {
        if (create || json.has(SectorCommandFromApiJsonDeserializer.SECTOR_CODE)) {
            sector.setSectorCode(
                    this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.SECTOR_CODE, json));
        }
        if (create || json.has(SectorCommandFromApiJsonDeserializer.SECTOR_NAME)) {
            sector.setSectorName(
                    this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.SECTOR_NAME, json));
        }
        if (json.has(SectorCommandFromApiJsonDeserializer.DESCRIPTION)) {
            sector.setDescription(
                    this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.DESCRIPTION, json));
        }
        if (json.has(SectorCommandFromApiJsonDeserializer.PARENT_ID)) {
            sector.setParentId(this.fromApiJsonHelper.extractLongNamed(SectorCommandFromApiJsonDeserializer.PARENT_ID, json));
        }
        if (json.has(SectorCommandFromApiJsonDeserializer.RISK_LEVEL)) {
            sector.setRiskLevel(this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.RISK_LEVEL, json));
        }
        if (json.has(SectorCommandFromApiJsonDeserializer.REGULATORY_CODE)) {
            sector.setRegulatoryCode(
                    this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.REGULATORY_CODE, json));
        }
        if (create || json.has(SectorCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(SectorCommandFromApiJsonDeserializer.STATUS, json);
            if (status != null) {
                sector.setStatus(status);
            }
        }
        return sector;
    }
}
