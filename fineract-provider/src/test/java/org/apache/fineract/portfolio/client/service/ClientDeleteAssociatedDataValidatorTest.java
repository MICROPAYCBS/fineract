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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepository;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfile;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfileRepository;
import org.apache.fineract.portfolio.client.domain.ClientContactRepository;
import org.apache.fineract.portfolio.client.domain.ClientFamilyMembersRepository;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.apache.fineract.portfolio.client.domain.ClientIncomeSourceRepository;
import org.apache.fineract.portfolio.client.domain.ClientOtherBankAccountRepository;
import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException;
import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException.AssociatedDataType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ClientDeleteAssociatedDataValidatorTest {

    private static final Long CLIENT_ID = 25L;

    @Mock
    private ClientFamilyMembersRepository clientFamilyMembersRepository;
    @Mock
    private ClientAddressRepository clientAddressRepository;
    @Mock
    private ClientContactRepository clientContactRepository;
    @Mock
    private ClientIncomeSourceRepository clientIncomeSourceRepository;
    @Mock
    private ClientComplianceProfileRepository clientComplianceProfileRepository;
    @Mock
    private ClientOtherBankAccountRepository clientOtherBankAccountRepository;
    @Mock
    private ClientIdentifierRepository clientIdentifierRepository;

    private ClientDeleteAssociatedDataValidator validator;

    @BeforeEach
    void setUp() {
        validator = new ClientDeleteAssociatedDataValidator(clientFamilyMembersRepository, clientAddressRepository,
                clientContactRepository, clientIncomeSourceRepository, clientComplianceProfileRepository,
                clientOtherBankAccountRepository, clientIdentifierRepository);
        stubNoAssociatedData();
    }

    @Test
    void validateNoAssociatedData_doesNotThrowWhenClientHasNoChildRecords() {
        assertDoesNotThrow(() -> validator.validateNoAssociatedData(CLIENT_ID));
    }

    @ParameterizedTest
    @EnumSource(AssociatedDataType.class)
    void validateNoAssociatedData_throwsWhenAssociatedDataExists(final AssociatedDataType type) {
        stubNoAssociatedData();
        stubAssociatedDataExists(type);

        final ClientCannotBeDeletedHasAssociatedDataException exception = assertThrows(
                ClientCannotBeDeletedHasAssociatedDataException.class, () -> validator.validateNoAssociatedData(CLIENT_ID));

        assertEquals(type.errorCode(), exception.getGlobalisationMessageCode());
        assertEquals(type.message(CLIENT_ID), exception.getDefaultUserMessage());
    }

    @Test
    void validateNoAssociatedData_reportsFamilyMembersBeforeOtherAssociations() {
        stubAssociatedDataExists(AssociatedDataType.FAMILY_MEMBERS);
        when(clientAddressRepository.countByClient_Id(CLIENT_ID)).thenReturn(2L);

        final ClientCannotBeDeletedHasAssociatedDataException exception = assertThrows(
                ClientCannotBeDeletedHasAssociatedDataException.class, () -> validator.validateNoAssociatedData(CLIENT_ID));

        assertEquals(AssociatedDataType.FAMILY_MEMBERS.errorCode(), exception.getGlobalisationMessageCode());
    }

    private void stubNoAssociatedData() {
        when(clientFamilyMembersRepository.existsByClient_Id(CLIENT_ID)).thenReturn(false);
        when(clientAddressRepository.countByClient_Id(CLIENT_ID)).thenReturn(0L);
        when(clientContactRepository.existsByClient_Id(CLIENT_ID)).thenReturn(false);
        when(clientIncomeSourceRepository.existsByClient_Id(CLIENT_ID)).thenReturn(false);
        when(clientComplianceProfileRepository.findByClient_Id(CLIENT_ID)).thenReturn(Optional.empty());
        when(clientOtherBankAccountRepository.existsByClient_Id(CLIENT_ID)).thenReturn(false);
        when(clientIdentifierRepository.existsByClient_Id(CLIENT_ID)).thenReturn(false);
    }

    private void stubAssociatedDataExists(final AssociatedDataType type) {
        switch (type) {
            case FAMILY_MEMBERS -> when(clientFamilyMembersRepository.existsByClient_Id(CLIENT_ID)).thenReturn(true);
            case ADDRESSES -> when(clientAddressRepository.countByClient_Id(CLIENT_ID)).thenReturn(1L);
            case CONTACTS -> when(clientContactRepository.existsByClient_Id(CLIENT_ID)).thenReturn(true);
            case INCOME_SOURCES -> when(clientIncomeSourceRepository.existsByClient_Id(CLIENT_ID)).thenReturn(true);
            case COMPLIANCE_PROFILE -> when(clientComplianceProfileRepository.findByClient_Id(CLIENT_ID))
                    .thenReturn(Optional.of(mock(ClientComplianceProfile.class)));
            case OTHER_BANK_ACCOUNTS -> when(clientOtherBankAccountRepository.existsByClient_Id(CLIENT_ID)).thenReturn(true);
            case IDENTIFIERS -> when(clientIdentifierRepository.existsByClient_Id(CLIENT_ID)).thenReturn(true);
        }
    }
}
