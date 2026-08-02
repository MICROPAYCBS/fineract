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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Collections;
import org.apache.fineract.commands.service.CommandProcessingService;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberFormatRepositoryWrapper;
import org.apache.fineract.infrastructure.accountnumberformat.service.AccountNumberManualEntryValidator;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.ExternalId;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ExternalIdFactory;
import org.apache.fineract.infrastructure.dataqueries.service.EntityDatatableChecksWritePlatformService;
import org.apache.fineract.infrastructure.event.business.service.BusinessEventNotifierService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.organisation.staff.domain.StaffRepositoryWrapper;
import org.apache.fineract.portfolio.account.service.AccountNumberGenerator;
import org.apache.fineract.portfolio.address.service.AddressWritePlatformService;
import org.apache.fineract.portfolio.client.data.ClientDataValidator;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientNonPersonRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException;
import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException.AssociatedDataType;
import org.apache.fineract.portfolio.client.exception.ClientMustBePendingToBeDeletedException;
import org.apache.fineract.portfolio.customerclass.domain.CustomerClassRepository;
import org.apache.fineract.portfolio.customerclass.service.CustomerClassClientValidationService;
import org.apache.fineract.portfolio.group.domain.GroupRepository;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.apache.fineract.portfolio.note.domain.NoteRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsProductRepository;
import org.apache.fineract.portfolio.savings.service.SavingsApplicationProcessWritePlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientWritePlatformServiceJpaRepositoryImplDeleteClientTest {

    private static final Long CLIENT_ID = 25L;

    @Mock
    private ClientRepositoryWrapper clientRepository;
    @Mock
    private ClientNonPersonRepositoryWrapper clientNonPersonRepository;
    @Mock
    private ClientDeleteAssociatedDataValidator clientDeleteAssociatedDataValidator;
    @Mock
    private NoteRepository noteRepository;
    @Mock
    private Client client;

    private ClientWritePlatformServiceJpaRepositoryImpl underTest;

    @BeforeEach
    void setUp() {
        underTest = new ClientWritePlatformServiceJpaRepositoryImpl(mock(PlatformSecurityContext.class), clientRepository,
                clientNonPersonRepository, clientDeleteAssociatedDataValidator, mock(OfficeRepositoryWrapper.class), noteRepository,
                mock(GroupRepository.class), mock(ClientDataValidator.class), mock(AccountNumberGenerator.class),
                mock(StaffRepositoryWrapper.class), mock(CodeValueRepositoryWrapper.class), mock(LoanRepositoryWrapper.class),
                mock(SavingsAccountRepositoryWrapper.class), mock(SavingsProductRepository.class),
                mock(SavingsApplicationProcessWritePlatformService.class), mock(CommandProcessingService.class),
                mock(ConfigurationDomainService.class), mock(AccountNumberFormatRepositoryWrapper.class),
                mock(AccountNumberManualEntryValidator.class), mock(FromJsonHelper.class), mock(AddressWritePlatformService.class),
                mock(ClientFamilyMembersWritePlatformService.class), mock(ClientIncomeSourcesWritePlatformService.class),
                mock(ClientComplianceProfileWritePlatformService.class), mock(ClientIdentifierWritePlatformService.class),
                mock(ClientIdentifierValidationService.class), mock(BusinessEventNotifierService.class),
                mock(EntityDatatableChecksWritePlatformService.class), mock(ExternalIdFactory.class),
                mock(CustomerClassRepository.class), mock(CustomerClassClientValidationService.class),
                mock(ClientTitleWritePlatformService.class), mock(ClientContactValidationService.class),
                mock(ClientContactWritePlatformService.class));

        when(clientRepository.findOneWithNotFoundDetection(CLIENT_ID)).thenReturn(client);
        when(client.isDraftOrPending()).thenReturn(true);
        when(client.officeId()).thenReturn(1L);
        when(client.getExternalId()).thenReturn(ExternalId.empty());
        when(noteRepository.findByClient(client)).thenReturn(Collections.emptyList());
        when(clientNonPersonRepository.findOneByClientId(CLIENT_ID)).thenReturn(null);
        doNothing().when(clientDeleteAssociatedDataValidator).validateNoAssociatedData(CLIENT_ID);
    }

    @Test
    void deleteClient_deletesPendingClientWhenNoAssociatedDataExists() {
        underTest.deleteClient(CLIENT_ID);

        verify(clientDeleteAssociatedDataValidator).validateNoAssociatedData(CLIENT_ID);
        verify(clientRepository).delete(client);
        verify(clientRepository).flush();
    }

    @Test
    void deleteClient_throwsWhenClientIsNotDraftOrPending() {
        when(client.isDraftOrPending()).thenReturn(false);

        assertThrows(ClientMustBePendingToBeDeletedException.class, () -> underTest.deleteClient(CLIENT_ID));
    }

    @Test
    void deleteClient_propagatesAssociatedDataValidationFailure() {
        doThrow(new ClientCannotBeDeletedHasAssociatedDataException(CLIENT_ID, AssociatedDataType.ADDRESSES))
                .when(clientDeleteAssociatedDataValidator).validateNoAssociatedData(CLIENT_ID);

        final ClientCannotBeDeletedHasAssociatedDataException exception = assertThrows(
                ClientCannotBeDeletedHasAssociatedDataException.class, () -> underTest.deleteClient(CLIENT_ID));

        assertEquals(AssociatedDataType.ADDRESSES.errorCode(), exception.getGlobalisationMessageCode());
    }

    @Test
    void deleteClient_mapsForeignKeyViolationToAssociatedDataException() {
        final String postgresMessage = "ERROR: update or delete on table \"m_client\" violates RESTRICT setting of foreign key constraint \""
                + AssociatedDataType.CONTACTS.foreignKeyConstraintName() + "\" on table \"m_client_contact\"";
        doThrow(new JpaSystemException(new RuntimeException(postgresMessage))).when(clientRepository).flush();

        final ClientCannotBeDeletedHasAssociatedDataException exception = assertThrows(
                ClientCannotBeDeletedHasAssociatedDataException.class, () -> underTest.deleteClient(CLIENT_ID));

        assertEquals(AssociatedDataType.CONTACTS.errorCode(), exception.getGlobalisationMessageCode());
    }

    @Test
    void deleteClient_mapsUnknownForeignKeyViolationToDataIntegrityException() {
        doThrow(new DataIntegrityViolationException("ERROR: unknown constraint")).when(clientRepository).flush();

        assertThrows(PlatformDataIntegrityException.class, () -> underTest.deleteClient(CLIENT_ID));
    }
}
