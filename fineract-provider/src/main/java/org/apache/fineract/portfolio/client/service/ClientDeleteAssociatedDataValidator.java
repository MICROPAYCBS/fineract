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

import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepository;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfileRepository;
import org.apache.fineract.portfolio.client.domain.ClientContactRepository;
import org.apache.fineract.portfolio.client.domain.ClientFamilyMembersRepository;
import org.apache.fineract.portfolio.client.domain.ClientIdentifierRepository;
import org.apache.fineract.portfolio.client.domain.ClientIncomeSourceRepository;
import org.apache.fineract.portfolio.client.domain.ClientOtherBankAccountRepository;
import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException;
import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException.AssociatedDataType;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ClientDeleteAssociatedDataValidator {

    private final ClientFamilyMembersRepository clientFamilyMembersRepository;
    private final ClientAddressRepository clientAddressRepository;
    private final ClientContactRepository clientContactRepository;
    private final ClientIncomeSourceRepository clientIncomeSourceRepository;
    private final ClientComplianceProfileRepository clientComplianceProfileRepository;
    private final ClientOtherBankAccountRepository clientOtherBankAccountRepository;
    private final ClientIdentifierRepository clientIdentifierRepository;

    public void validateNoAssociatedData(final Long clientId) {
        if (this.clientFamilyMembersRepository.existsByClient_Id(clientId)) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.FAMILY_MEMBERS);
        }
        if (this.clientAddressRepository.countByClient_Id(clientId) > 0) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.ADDRESSES);
        }
        if (this.clientContactRepository.existsByClient_Id(clientId)) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.CONTACTS);
        }
        if (this.clientIncomeSourceRepository.existsByClient_Id(clientId)) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.INCOME_SOURCES);
        }
        if (this.clientComplianceProfileRepository.findByClient_Id(clientId).isPresent()) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.COMPLIANCE_PROFILE);
        }
        if (this.clientOtherBankAccountRepository.existsByClient_Id(clientId)) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.OTHER_BANK_ACCOUNTS);
        }
        if (this.clientIdentifierRepository.existsByClient_Id(clientId)) {
            throw new ClientCannotBeDeletedHasAssociatedDataException(clientId, AssociatedDataType.IDENTIFIERS);
        }
    }
}
