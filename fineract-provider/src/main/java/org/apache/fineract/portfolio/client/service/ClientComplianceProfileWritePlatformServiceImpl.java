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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.api.ClientApiConstants;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfile;
import org.apache.fineract.portfolio.client.domain.ClientComplianceProfileRepository;
import org.apache.fineract.portfolio.client.domain.ClientOtherBankAccount;
import org.apache.fineract.portfolio.client.domain.ClientOtherBankAccountRepository;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.apache.fineract.portfolio.client.serialization.ClientComplianceProfileCommandFromApiJsonDeserializer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClientComplianceProfileWritePlatformServiceImpl implements ClientComplianceProfileWritePlatformService {

    private final PlatformSecurityContext context;
    private final ClientComplianceProfileRepository complianceProfileRepository;
    private final ClientOtherBankAccountRepository otherBankAccountRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final ClientComplianceProfileCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult saveComplianceProfile(final Client client, final JsonCommand command) {
        this.context.authenticatedUser();
        final String json = command.jsonFragment(ClientApiConstants.complianceProfile);
        this.apiJsonDeserializer.validateForCreate(client.getId(), json);
        final JsonObject jsonObject = this.fromApiJsonHelper.parse(json).getAsJsonObject();
        return persistComplianceProfile(client, jsonObject, command.commandId());
    }

    @Override
    @Transactional
    public CommandProcessingResult updateComplianceProfile(final long clientId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(clientId, command.json());
        final Client client = this.clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);
        final JsonObject jsonObject = command.parsedJson().getAsJsonObject();
        return persistComplianceProfile(client, jsonObject, command.commandId());
    }

    private CommandProcessingResult persistComplianceProfile(final Client client, final JsonObject json, final Long commandId) {
        ClientComplianceProfile profile = this.complianceProfileRepository.findByClient_Id(client.getId()).orElse(null);
        if (profile == null) {
            profile = new ClientComplianceProfile();
            profile.setClient(client);
        }
        patchFromCommand(profile, json);
        applyBooleanDefaults(profile);
        profile = this.complianceProfileRepository.saveAndFlush(profile);

        if ("N".equalsIgnoreCase(profile.getHasOtherBankAccounts())) {
            deleteOtherBankAccounts(client.getId());
        } else {
            replaceOtherBankAccounts(client, json);
        }

        return new CommandProcessingResultBuilder().withCommandId(commandId).withEntityId(profile.getId()).withClientId(client.getId())
                .build();
    }

    private void patchFromCommand(final ClientComplianceProfile profile, final JsonObject json) {
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.HAS_OTHER_BANK_ACCOUNTS)
                && !json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.HAS_OTHER_BANK_ACCOUNTS).isJsonNull()) {
            final boolean value = json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.HAS_OTHER_BANK_ACCOUNTS).getAsBoolean();
            profile.setHasOtherBankAccounts(value ? "Y" : "N");
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.IS_PEP)
                && !json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.IS_PEP).isJsonNull()) {
            final boolean value = json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.IS_PEP).getAsBoolean();
            profile.setIsPep(value ? "Y" : "N");
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.PEP_POSITION)) {
            profile.setPepPosition(StringUtils
                    .trimToNull(json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.PEP_POSITION).getAsString()));
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.PEP_RELATIVE_NAME)) {
            profile.setPepRelativeName(StringUtils
                    .trimToNull(json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.PEP_RELATIVE_NAME).getAsString()));
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.US_CITIZEN_OR_RESIDENT)
                && !json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.US_CITIZEN_OR_RESIDENT).isJsonNull()) {
            final boolean value = json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.US_CITIZEN_OR_RESIDENT).getAsBoolean();
            profile.setUsCitizenOrResident(value ? "Y" : "N");
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.FATCA_REGISTERED)
                && !json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.FATCA_REGISTERED).isJsonNull()) {
            final boolean value = json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.FATCA_REGISTERED).getAsBoolean();
            profile.setFatcaRegistered(value ? "Y" : "N");
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.FATCA_REGISTRATION_NO)) {
            profile.setFatcaRegistrationNo(StringUtils
                    .trimToNull(json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.FATCA_REGISTRATION_NO).getAsString()));
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.DPF_ALTERNATIVE_BANK_NAME)) {
            profile.setDpfAlternativeBankName(StringUtils
                    .trimToNull(json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.DPF_ALTERNATIVE_BANK_NAME).getAsString()));
        }
        if (json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.DPF_ALTERNATIVE_ACCOUNT_NUMBER)) {
            profile.setDpfAlternativeAccountNumber(StringUtils.trimToNull(
                    json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.DPF_ALTERNATIVE_ACCOUNT_NUMBER).getAsString()));
        }
    }

    private void applyBooleanDefaults(final ClientComplianceProfile profile) {
        if (StringUtils.isBlank(profile.getHasOtherBankAccounts())) {
            profile.setHasOtherBankAccounts("N");
        }
        if (StringUtils.isBlank(profile.getIsPep())) {
            profile.setIsPep("N");
        }
        if (StringUtils.isBlank(profile.getUsCitizenOrResident())) {
            profile.setUsCitizenOrResident("N");
        }
        if (StringUtils.isBlank(profile.getFatcaRegistered())) {
            profile.setFatcaRegistered("N");
        }
    }

    private void deleteOtherBankAccounts(final Long clientId) {
        final List<ClientOtherBankAccount> existingAccounts = this.otherBankAccountRepository.findByClient_IdOrderByDisplayOrderAsc(clientId);
        if (!existingAccounts.isEmpty()) {
            this.otherBankAccountRepository.deleteAll(existingAccounts);
        }
    }

    private void replaceOtherBankAccounts(final Client client, final JsonObject json) {
        deleteOtherBankAccounts(client.getId());
        if (!json.has(ClientComplianceProfileCommandFromApiJsonDeserializer.OTHER_BANK_ACCOUNTS)
                || json.get(ClientComplianceProfileCommandFromApiJsonDeserializer.OTHER_BANK_ACCOUNTS).isJsonNull()) {
            return;
        }
        final JsonArray otherBankAccounts = json.getAsJsonArray(ClientComplianceProfileCommandFromApiJsonDeserializer.OTHER_BANK_ACCOUNTS);
        for (JsonElement element : otherBankAccounts) {
            final JsonObject accountJson = element.getAsJsonObject();
            final ClientOtherBankAccount account = new ClientOtherBankAccount();
            account.setClient(client);
            account.setBankName(StringUtils
                    .trimToNull(accountJson.get(ClientComplianceProfileCommandFromApiJsonDeserializer.BANK_NAME).getAsString()));
            if (accountJson.has(ClientComplianceProfileCommandFromApiJsonDeserializer.BRANCH_NAME)) {
                account.setBranchName(StringUtils
                        .trimToNull(accountJson.get(ClientComplianceProfileCommandFromApiJsonDeserializer.BRANCH_NAME).getAsString()));
            }
            account.setAccountNumber(StringUtils
                    .trimToNull(accountJson.get(ClientComplianceProfileCommandFromApiJsonDeserializer.ACCOUNT_NUMBER).getAsString()));
            account.setDisplayOrder(
                    accountJson.get(ClientComplianceProfileCommandFromApiJsonDeserializer.DISPLAY_ORDER).getAsInt());
            this.otherBankAccountRepository.saveAndFlush(account);
        }
    }
}
