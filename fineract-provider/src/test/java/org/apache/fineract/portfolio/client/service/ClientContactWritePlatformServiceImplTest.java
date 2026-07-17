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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientContact;
import org.apache.fineract.portfolio.client.domain.ClientContactRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ContactType;
import org.apache.fineract.portfolio.client.serialization.ClientContactCommandFromApiJsonDeserializer;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientContactWritePlatformServiceImplTest {

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private ClientContactRepository clientContactRepository;
    @Mock
    private ContactTypeWritePlatformService contactTypeWritePlatformService;
    @Mock
    private ClientContactValidationService clientContactValidationService;
    @Mock
    private AppUser appUser;
    @Mock
    private Client client;

    private final FromJsonHelper fromApiJsonHelper = new FromJsonHelper();
    private ClientContactCommandFromApiJsonDeserializer apiJsonDeserializer;
    private ClientContactWritePlatformServiceImpl subject;

    @BeforeEach
    void setUp() {
        apiJsonDeserializer = new ClientContactCommandFromApiJsonDeserializer(fromApiJsonHelper);
        subject = new ClientContactWritePlatformServiceImpl(context, clientRepository, clientContactRepository,
                contactTypeWritePlatformService, clientContactValidationService, apiJsonDeserializer, fromApiJsonHelper);
        when(context.authenticatedUser()).thenReturn(appUser);
        when(client.getId()).thenReturn(10L);
        when(client.officeId()).thenReturn(1L);
    }

    @Test
    void addClientContacts_persistsMultipleContactTypes() {
        final ContactType mobile = contactType(1L, "MOBILE", "^\\+?[0-9]{10,15}$");
        final ContactType email = contactType(2L, "EMAIL", "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
        when(contactTypeWritePlatformService.findWithNotFoundDetection(1L)).thenReturn(mobile);
        when(contactTypeWritePlatformService.findWithNotFoundDetection(2L)).thenReturn(email);
        when(clientContactRepository.saveAndFlush(any(ClientContact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final JsonCommand command = jsonCommand("""
                {
                  "contacts": [
                    { "contactTypeId": 1, "contactValue": "+256700000001", "primary": true },
                    { "contactTypeId": 2, "contactValue": "customer@example.com", "primary": true }
                  ]
                }
                """);

        subject.addClientContacts(client, command);

        verify(clientContactRepository, times(2)).saveAndFlush(any(ClientContact.class));
        verify(clientContactValidationService).validateContactValue(eq(mobile), eq("+256700000001"));
        verify(clientContactValidationService).validateContactValue(eq(email), eq("customer@example.com"));
        verify(clientContactValidationService).clearOtherPrimaryFlags(eq(10L), eq(1L), isNull());
        verify(clientContactValidationService).clearOtherPrimaryFlags(eq(10L), eq(2L), isNull());
    }

    @Test
    void addClientContacts_rejectsDuplicatePrimaryForSameType() {
        final JsonCommand command = jsonCommand("""
                {
                  "contacts": [
                    { "contactTypeId": 1, "contactValue": "+256700000001", "primary": true },
                    { "contactTypeId": 1, "contactValue": "+256700000002", "primary": true }
                  ]
                }
                """);

        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> subject.addClientContacts(client, command));

        assertEquals("validation.msg.client.contact.duplicate.primary.for.type", ex.getErrors().get(0).getUserMessageGlobalisationCode());
        verify(clientContactRepository, never()).saveAndFlush(any(ClientContact.class));
    }

    @Test
    void addClientContacts_propagatesContactValueValidationFailure() {
        final ContactType mobile = contactType(1L, "MOBILE", "^\\+?[0-9]{10,15}$");
        when(contactTypeWritePlatformService.findWithNotFoundDetection(1L)).thenReturn(mobile);
        org.mockito.Mockito.doThrow(new PlatformApiDataValidationException(java.util.List.of())).when(clientContactValidationService)
                .validateContactValue(eq(mobile), eq("not-a-phone"));

        final JsonCommand command = jsonCommand("""
                {
                  "contacts": [
                    { "contactTypeId": 1, "contactValue": "not-a-phone", "primary": true }
                  ]
                }
                """);

        assertThrows(PlatformApiDataValidationException.class, () -> subject.addClientContacts(client, command));
        verify(clientContactRepository, never()).saveAndFlush(any(ClientContact.class));
    }

    @Test
    void addClientContacts_allowsEmptyContactsArray() {
        final JsonCommand command = jsonCommand("""
                { "contacts": [] }
                """);

        assertDoesNotThrow(() -> subject.addClientContacts(client, command));
        verify(clientContactRepository, never()).saveAndFlush(any(ClientContact.class));
    }

    @Test
    void addClientContacts_savesContactLinkedToClient() {
        final ContactType mobile = contactType(1L, "MOBILE", null);
        when(contactTypeWritePlatformService.findWithNotFoundDetection(1L)).thenReturn(mobile);
        when(clientContactRepository.saveAndFlush(any(ClientContact.class))).thenAnswer(invocation -> invocation.getArgument(0));

        final JsonCommand command = jsonCommand("""
                {
                  "contacts": [
                    { "contactTypeId": 1, "contactValue": "+256700000001", "primary": false }
                  ]
                }
                """);

        subject.addClientContacts(client, command);

        final ArgumentCaptor<ClientContact> captor = ArgumentCaptor.forClass(ClientContact.class);
        verify(clientContactRepository).saveAndFlush(captor.capture());
        final ClientContact saved = captor.getValue();
        assertEquals(client, saved.getClient());
        assertEquals(1L, saved.getContactTypeId());
        assertEquals("+256700000001", saved.getContactValue());
        verify(clientContactValidationService, never()).clearOtherPrimaryFlags(anyLong(), anyLong(), any());
    }

    private static ContactType contactType(final Long id, final String code, final String regex) {
        final ContactType contactType = new ContactType();
        contactType.setId(id);
        contactType.setTypeCode(code);
        contactType.setTypeName(code);
        contactType.setValidationRegex(regex);
        contactType.setStatus("ACTIVE");
        return contactType;
    }

    private JsonCommand jsonCommand(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromApiJsonHelper, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null);
    }
}
