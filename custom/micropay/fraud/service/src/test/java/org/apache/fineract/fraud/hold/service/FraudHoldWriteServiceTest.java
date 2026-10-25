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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.fraud.hold.data.FraudHoldData;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.fraud.hold.data.FraudHoldSource;
import org.apache.fineract.fraud.hold.data.FraudHoldStatus;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FraudHoldWriteServiceTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private AppUser user;
    @Mock
    private FraudHoldRepository holdRepository;
    @Mock
    private FraudHoldTargetRepository targetRepository;
    @Mock
    private FraudHoldEffectService effectService;

    private FraudHoldWriteService underTest;

    @BeforeEach
    void setUp() {
        when(context.authenticatedUser()).thenReturn(user);
        when(user.getId()).thenReturn(1L);
        underTest = new FraudHoldWriteService(context, new FraudHoldDataValidator(fromJsonHelper), holdRepository, targetRepository,
                effectService);
    }

    @Test
    void createsHoldAndAppliesEffects() {
        when(targetRepository.savingsAccountExists(5L)).thenReturn(true);
        when(holdRepository.insertHold(eq(FraudHoldEntityType.SAVINGS_ACCOUNT), eq(5L), eq(FraudHoldSource.FRAUD_ENGINE), eq("spike"),
                eq("alert-9"), eq(1L), any())).thenReturn(20L);
        when(holdRepository.findEffects(20L)).thenReturn(List.of());

        final CommandProcessingResult result = underTest.createHold(command(null,
                "{\"entityType\":\"SAVINGS_ACCOUNT\",\"entityId\":5,\"reason\":\"spike\",\"source\":\"FRAUD_ENGINE\","
                        + "\"externalReference\":\"alert-9\"}"));

        assertEquals(20L, result.getResourceId());
        verify(effectService).applyEffects(20L, FraudHoldEntityType.SAVINGS_ACCOUNT, 5L, "spike");
    }

    @Test
    void sourceDefaultsToManual() {
        when(targetRepository.clientExists(3L)).thenReturn(true);
        when(holdRepository.insertHold(any(), any(), any(), any(), any(), any(), any())).thenReturn(21L);

        underTest.createHold(command(null, "{\"entityType\":\"CLIENT\",\"entityId\":3,\"reason\":\"sanctions match\"}"));

        verify(holdRepository).insertHold(eq(FraudHoldEntityType.CLIENT), eq(3L), eq(FraudHoldSource.MANUAL), eq("sanctions match"),
                eq(null), eq(1L), any());
    }

    @Test
    void rejectsSecondActiveHoldOnSameEntity() {
        when(targetRepository.loanExists(4L)).thenReturn(true);
        when(holdRepository.hasActiveHold(FraudHoldEntityType.LOAN, 4L)).thenReturn(true);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.createHold(command(null, "{\"entityType\":\"LOAN\",\"entityId\":4,\"reason\":\"r\"}")));
        verify(effectService, never()).applyEffects(any(), any(), any(), any());
    }

    @Test
    void rejectsMissingTarget() {
        when(targetRepository.loanExists(4L)).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> underTest.createHold(command(null, "{\"entityType\":\"LOAN\",\"entityId\":4,\"reason\":\"r\"}")));
    }

    @Test
    void rejectsUnknownEntityTypeAndMissingReason() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> underTest.createHold(command(null, "{\"entityType\":\"GROUP\",\"entityId\":4}")));
    }

    @Test
    void releasesActiveHold() {
        when(holdRepository.findById(20L)).thenReturn(Optional.of(hold(FraudHoldStatus.ACTIVE)));

        underTest.releaseHold(20L, command(20L, "{\"note\":\"customer verified\"}"));

        verify(holdRepository).markReleased(eq(20L), eq(1L), any(), eq("customer verified"));
        verify(effectService).revertEffects(20L);
    }

    @Test
    void rejectsReleasingReleasedHold() {
        when(holdRepository.findById(20L)).thenReturn(Optional.of(hold(FraudHoldStatus.RELEASED)));

        assertThrows(GeneralPlatformDomainRuleException.class, () -> underTest.releaseHold(20L, command(20L, "{}")));
        verify(effectService, never()).revertEffects(anyLong());
    }

    private JsonCommand command(Long resourceId, String json) {
        return JsonCommand.fromJsonElement(resourceId, JsonParser.parseString(json), fromJsonHelper);
    }

    private static FraudHoldData hold(FraudHoldStatus status) {
        return new FraudHoldData(20L, FraudHoldEntityType.SAVINGS_ACCOUNT, 5L, status, FraudHoldSource.MANUAL, "reason", null, 1L, null,
                null, null, null, List.of());
    }
}
