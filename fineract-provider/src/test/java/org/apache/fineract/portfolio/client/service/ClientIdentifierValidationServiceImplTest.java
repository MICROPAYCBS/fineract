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
package org.apache.fineract.portfolio.client.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierStatus;
import org.apache.fineract.portfolio.client.domain.IdentityTypeRepository;
import org.apache.fineract.portfolio.client.domain.LegalForm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ClientIdentifierValidationServiceImplTest {

    @Mock
    private IdentityTypeRepository identityTypeRepository;

    @Mock
    private ClientIdentifierRepository clientIdentifierRepository;

    private ClientIdentifierValidationServiceImpl validationService;

    @BeforeEach
    void setUp() {
        validationService = new ClientIdentifierValidationServiceImpl(identityTypeRepository, clientIdentifierRepository,
                new FromJsonHelper());
    }

    @Test
    void validateAtLeastOneIdentifierForPersonCreate_withValidIdentifier_doesNotThrow() {
        final String json = """
                {
                  "legalFormId": 1,
                  "clientIdentifiers": [
                    { "documentTypeId": 1, "documentKey": "CM123456", "status": "Active" }
                  ]
                }
                """;

        assertDoesNotThrow(() -> validationService.validateAtLeastOneIdentifierForPersonCreate(new FromJsonHelper().parse(json),
                LegalForm.PERSON.getValue()));
    }

    @Test
    void validateAtLeastOneIdentifierForPersonCreate_withoutIdentifiers_throws() {
        final String json = """
                {
                  "legalFormId": 1
                }
                """;

        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> validationService.validateAtLeastOneIdentifierForPersonCreate(new FromJsonHelper().parse(json),
                        LegalForm.PERSON.getValue()));

        assertTrue(ex.getErrors().stream().anyMatch(e -> ClientApiConstants.clientIdentifiers.equals(e.getParameterName())));
    }

    @Test
    void validateAtLeastOneIdentifierForPersonCreate_forEntity_doesNotThrowWithoutIdentifiers() {
        final String json = """
                {
                  "legalFormId": 2
                }
                """;

        assertDoesNotThrow(() -> validationService.validateAtLeastOneIdentifierForPersonCreate(new FromJsonHelper().parse(json),
                LegalForm.ENTITY.getValue()));
    }

    @Test
    void validateAtLeastOneIdentifierForPersonClient_withoutIdentifiers_throws() {
        when(clientIdentifierRepository.countByClient_IdAndStatus(1L, ClientIdentifierStatus.ACTIVE.getValue())).thenReturn(0L);

        PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> validationService.validateAtLeastOneIdentifierForPersonClient(1L, LegalForm.PERSON.getValue()));

        assertTrue(ex.getErrors().stream().anyMatch(e -> ClientApiConstants.clientIdentifiers.equals(e.getParameterName())));
    }

    @Test
    void validateAtLeastOneIdentifierForPersonClient_withIdentifier_doesNotThrow() {
        when(clientIdentifierRepository.countByClient_IdAndStatus(1L, ClientIdentifierStatus.ACTIVE.getValue())).thenReturn(1L);

        assertDoesNotThrow(() -> validationService.validateAtLeastOneIdentifierForPersonClient(1L, LegalForm.PERSON.getValue()));
    }
}
