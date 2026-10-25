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
package org.apache.fineract.fraud.hold.service;

import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.ENTITY_ID_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.ENTITY_TYPE_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.EXTERNAL_REFERENCE_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.NOTE_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.REASON_PARAM;
import static org.apache.fineract.fraud.hold.service.FraudHoldConstants.SOURCE_PARAM;

import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.fraud.hold.data.FraudHoldData;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.fraud.hold.data.FraudHoldSource;
import org.apache.fineract.fraud.hold.exception.FraudHoldNotFoundException;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class FraudHoldWriteService {

    private final PlatformSecurityContext context;
    private final FraudHoldDataValidator validator;
    private final FraudHoldRepository holdRepository;
    private final FraudHoldTargetRepository targetRepository;
    private final FraudHoldEffectService effectService;

    @Transactional
    public CommandProcessingResult createHold(JsonCommand command) {
        final Long userId = context.authenticatedUser().getId();
        validator.validateForCreate(command.json());

        final FraudHoldEntityType entityType = FraudHoldEntityType.valueOf(command.stringValueOfParameterNamed(ENTITY_TYPE_PARAM));
        final Long entityId = command.longValueOfParameterNamed(ENTITY_ID_PARAM);
        final String reason = command.stringValueOfParameterNamed(REASON_PARAM);
        final String sourceValue = command.stringValueOfParameterNamedAllowingNull(SOURCE_PARAM);
        final FraudHoldSource source = sourceValue == null ? FraudHoldSource.MANUAL : FraudHoldSource.valueOf(sourceValue);
        final String externalReference = command.stringValueOfParameterNamedAllowingNull(EXTERNAL_REFERENCE_PARAM);

        validateTargetExists(entityType, entityId);
        if (holdRepository.hasActiveHold(entityType, entityId)) {
            throw new GeneralPlatformDomainRuleException("error.msg.fraud.hold.already.active",
                    entityType + " " + entityId + " already has an active fraud hold", entityType, entityId);
        }

        final Long holdId = holdRepository.insertHold(entityType, entityId, source, reason, externalReference, userId,
                DateUtils.getAuditLocalDateTime());
        effectService.applyEffects(holdId, entityType, entityId, reason);

        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(ENTITY_TYPE_PARAM, entityType);
        changes.put(ENTITY_ID_PARAM, entityId);
        changes.put("effects", holdRepository.findEffects(holdId).size());
        return result(command, holdId, entityType, entityId).with(changes).build();
    }

    @Transactional
    public CommandProcessingResult releaseHold(Long holdId, JsonCommand command) {
        final Long userId = context.authenticatedUser().getId();
        validator.validateForRelease(command.json());

        final FraudHoldData hold = holdRepository.findById(holdId).orElseThrow(() -> new FraudHoldNotFoundException(holdId));
        if (!hold.isActive()) {
            throw new GeneralPlatformDomainRuleException("error.msg.fraud.hold.not.active", "Fraud hold " + holdId + " is not active",
                    holdId);
        }

        final String note = command.parameterExists(NOTE_PARAM) ? command.stringValueOfParameterNamedAllowingNull(NOTE_PARAM) : null;
        holdRepository.markReleased(holdId, userId, DateUtils.getAuditLocalDateTime(), note);
        effectService.revertEffects(holdId);

        return result(command, holdId, hold.getEntityType(), hold.getEntityId()).with(Map.of("status", "RELEASED")).build();
    }

    private void validateTargetExists(FraudHoldEntityType entityType, Long entityId) {
        final boolean exists = switch (entityType) {
            case CLIENT -> targetRepository.clientExists(entityId);
            case SAVINGS_ACCOUNT -> targetRepository.savingsAccountExists(entityId);
            case LOAN -> targetRepository.loanExists(entityId);
        };
        if (!exists) {
            throw new GeneralPlatformDomainRuleException("error.msg.fraud.hold.target.not.found",
                    entityType + " " + entityId + " does not exist", entityType, entityId);
        }
    }

    private static CommandProcessingResultBuilder result(JsonCommand command, Long holdId, FraudHoldEntityType entityType, Long entityId) {
        final CommandProcessingResultBuilder builder = new CommandProcessingResultBuilder().withCommandId(command.commandId())
                .withEntityId(holdId);
        switch (entityType) {
            case CLIENT -> builder.withClientId(entityId);
            case SAVINGS_ACCOUNT -> builder.withSavingsId(entityId);
            case LOAN -> builder.withLoanId(entityId);
        }
        return builder;
    }
}
