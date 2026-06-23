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
package org.apache.fineract.portfolio.address.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.codes.domain.CodeValueRepository;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.address.domain.Address;
import org.apache.fineract.portfolio.address.domain.AddressRepository;
import org.apache.fineract.portfolio.address.exception.AddressNotFoundException;
import org.apache.fineract.portfolio.address.serialization.AddressCommandFromApiJsonDeserializer;
import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.client.domain.ClientAddress;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepository;
import org.apache.fineract.portfolio.client.domain.ClientAddressRepositoryWrapper;
import org.apache.fineract.portfolio.client.domain.ClientRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AddressWritePlatformServiceImpl implements AddressWritePlatformService {

    private final PlatformSecurityContext context;
    private final CodeValueRepository codeValueRepository;
    private final ClientAddressRepository clientAddressRepository;
    private final ClientRepositoryWrapper clientRepositoryWrapper;
    private final AddressRepository addressRepository;
    private final ClientAddressRepositoryWrapper clientAddressRepositoryWrapper;
    private final AddressCommandFromApiJsonDeserializer fromApiJsonDeserializer;

    @Override
    @Transactional
    public CommandProcessingResult addClientAddress(final Long clientId, final Long addressTypeId, final JsonCommand command) {
        JsonObject jsonObject = command.parsedJson().getAsJsonObject();
        context.authenticatedUser();
        fromApiJsonDeserializer.validateForCreate(jsonObject.toString(), false);

        final CodeValue addressTypeIdCodeValue = codeValueRepository.getReferenceById(addressTypeId);
        final Client client = clientRepositoryWrapper.findOneWithNotFoundDetection(clientId);

        final Address address = createAddress(jsonObject);
        addressRepository.save(address);

        final ClientAddress clientAddress = createClientAddress(client, jsonObject, addressTypeIdCodeValue, address);
        ensurePrimaryOnCreate(client, clientAddress);
        applyPrimaryAddress(client, clientAddress, clientAddress.isPrimary());
        clientAddressRepository.saveAndFlush(clientAddress);
        ensureClientHasPrimaryAddress(client.getId());

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(clientAddress.getId()) //
                .build();
    }

    @Override
    @Transactional
    public CommandProcessingResult addNewClientAddress(final Client client, final JsonCommand command) {
        ClientAddress clientAddress = new ClientAddress();
        final JsonArray addressArray = command.arrayOfParameterNamed("address");
        final List<ClientAddress> createdAddresses = new ArrayList<>();

        if (addressArray != null) {
            for (int i = 0; i < addressArray.size(); i++) {
                final JsonObject jsonObject = addressArray.get(i).getAsJsonObject();

                fromApiJsonDeserializer.validateForCreate(jsonObject.toString(), true);

                final long addressTypeId = jsonObject.get("addressTypeId").getAsLong();
                final CodeValue addressTypeIdCodeValue = codeValueRepository.getReferenceById(addressTypeId);

                final Address address = createAddress(jsonObject);
                addressRepository.save(address);

                clientAddress = createClientAddress(client, jsonObject, addressTypeIdCodeValue, address);
                if (createdAddresses.isEmpty() && !clientAddress.isPrimary() && clientAddress.isIs_active()) {
                    clientAddress.setPrimary(true);
                }
                applyPrimaryAddress(client, clientAddress, clientAddress.isPrimary());
                clientAddressRepository.saveAndFlush(clientAddress);
                createdAddresses.add(clientAddress);
            }
            validateSinglePrimaryInBatch(addressArray);
            ensureClientHasPrimaryAddress(client.getId());
        }

        // This is confusing because only the last client address id is returned
        // TODO: clean this up
        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(clientAddress.getId()) //
                .build();
    }

    private ClientAddress createClientAddress(Client client, JsonObject jsonObject, CodeValue addressTypeIdCodeValue, Address address) {
        boolean clientAddressIsActive = true;
        if (jsonObject.get("isActive") != null) {
            clientAddressIsActive = jsonObject.get("isActive").getAsBoolean();
        }
        boolean clientAddressIsPrimary = false;
        if (jsonObject.get("isPrimary") != null) {
            clientAddressIsPrimary = jsonObject.get("isPrimary").getAsBoolean();
        }
        return ClientAddress.fromJson(clientAddressIsActive, clientAddressIsPrimary, client, address, addressTypeIdCodeValue);
    }

    private void ensurePrimaryOnCreate(final Client client, final ClientAddress clientAddress) {
        if (this.clientAddressRepository.countByClient_Id(client.getId()) == 0 && clientAddress.isIs_active()) {
            clientAddress.setPrimary(true);
        }
    }

    private void validateSinglePrimaryInBatch(final JsonArray addressArray) {
        int primaryCount = 0;
        for (int i = 0; i < addressArray.size(); i++) {
            final JsonObject jsonObject = addressArray.get(i).getAsJsonObject();
            final boolean isPrimary = jsonObject.has("isPrimary") && !jsonObject.get("isPrimary").isJsonNull()
                    && jsonObject.get("isPrimary").getAsBoolean();
            if (isPrimary) {
                primaryCount++;
                final boolean isActive = jsonObject.get("isActive") == null || jsonObject.get("isActive").getAsBoolean();
                if (!isActive) {
                    throwInactiveAddressCannotBePrimaryValidationError();
                }
            }
        }
        if (primaryCount > 1) {
            final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
            final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("Address");
            baseDataValidator.reset().parameter("isPrimary").failWithCode("multiple.primary.addresses.not.allowed",
                    "Only one address may be marked as primary.");
            throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                    dataValidationErrors);
        }
    }

    private void ensureClientHasPrimaryAddress(final Long clientId) {
        final List<ClientAddress> addresses = this.clientAddressRepository.findByClient_Id(clientId);
        for (ClientAddress address : addresses) {
            if (address.isPrimary() && !address.isIs_active()) {
                address.setPrimary(false);
                this.clientAddressRepository.saveAndFlush(address);
            }
        }
        final boolean hasActivePrimary = addresses.stream().anyMatch(a -> a.isPrimary() && a.isIs_active());
        if (!hasActivePrimary) {
            final Optional<ClientAddress> firstActive = addresses.stream().filter(ClientAddress::isIs_active).findFirst();
            if (firstActive.isPresent()) {
                applyPrimaryAddress(firstActive.get().getClient(), firstActive.get(), true);
                this.clientAddressRepository.saveAndFlush(firstActive.get());
            }
        }
    }

    private void applyPrimaryAddress(final Client client, final ClientAddress clientAddress, final boolean primaryFlag) {
        if (!primaryFlag) {
            return;
        }
        if (!clientAddress.isIs_active()) {
            throwInactiveAddressCannotBePrimaryValidationError();
        }
        clearOtherPrimaryAddresses(client.getId(), clientAddress.getId());
        clientAddress.setPrimary(true);
    }

    /**
     * Clears primary on all other addresses for the client and flushes each change before the new primary is saved,
     * so the partial unique index on (client_id) WHERE is_primary is not violated during flush ordering.
     */
    private void clearOtherPrimaryAddresses(final Long clientId, final Long exceptAddressId) {
        final List<ClientAddress> existingPrimary = this.clientAddressRepository.findByClient_IdAndIsPrimary(clientId, true);
        for (ClientAddress other : existingPrimary) {
            if (exceptAddressId == null || !other.getId().equals(exceptAddressId)) {
                other.setPrimary(false);
                this.clientAddressRepository.saveAndFlush(other);
            }
        }
    }

    private Address createAddress(JsonObject jsonObject) {
        CodeValue stateIdCodeValue = null;
        if (jsonObject.get("stateProvinceId") != null) {
            long stateId = jsonObject.get("stateProvinceId").getAsLong();
            stateIdCodeValue = codeValueRepository.getReferenceById(stateId);
        }

        CodeValue countryIdCodeValue = null;
        if (jsonObject.get("countryId") != null) {
            long countryId = jsonObject.get("countryId").getAsLong();
            countryIdCodeValue = codeValueRepository.getReferenceById(countryId);
        }

        final Address address = Address.fromJsonObject(jsonObject, stateIdCodeValue, countryIdCodeValue);
        address.setCreatedOn(LocalDate.now(DateUtils.getDateTimeZoneOfTenant()));
        address.setUpdatedOn(LocalDate.now(DateUtils.getDateTimeZoneOfTenant()));
        return address;
    }

    @Override
    @Transactional
    public CommandProcessingResult updateClientAddress(final Long clientId, final JsonCommand command) {
        this.context.authenticatedUser();

        long stateId;

        long countryId;

        CodeValue stateIdobj;

        CodeValue countryIdObj;

        boolean is_address_update = false;

        this.fromApiJsonDeserializer.validateForUpdate(command.json());

        final long addressId = command.longValueOfParameterNamed("addressId");

        final ClientAddress clientAddressObj = this.clientAddressRepositoryWrapper.findOneByClientIdAndAddressId(clientId, addressId);

        if (clientAddressObj == null) {
            throw new AddressNotFoundException(clientId);
        }

        final Address addobj = this.addressRepository.getReferenceById(addressId);

        if (!command.stringValueOfParameterNamed("addressLine1").isEmpty()) {

            is_address_update = true;
            final String addressLine1 = command.stringValueOfParameterNamed("addressLine1");
            addobj.setAddressLine1(addressLine1);

        }

        if (!command.stringValueOfParameterNamed("addressLine2").isEmpty()) {

            is_address_update = true;
            final String addressLine2 = command.stringValueOfParameterNamed("addressLine2");
            addobj.setAddressLine2(addressLine2);

        }

        if (!command.stringValueOfParameterNamed("addressLine3").isEmpty()) {
            is_address_update = true;
            final String addressLine3 = command.stringValueOfParameterNamed("addressLine3");
            addobj.setAddressLine3(addressLine3);

        }

        if (!command.stringValueOfParameterNamed("townVillage").isEmpty()) {

            is_address_update = true;
            final String townVillage = command.stringValueOfParameterNamed("townVillage");
            addobj.setTownVillage(townVillage);
        }

        if (!command.stringValueOfParameterNamed("city").isEmpty()) {
            is_address_update = true;
            final String city = command.stringValueOfParameterNamed("city");
            addobj.setCity(city);
        }

        if (!command.stringValueOfParameterNamed("countyDistrict").isEmpty()) {
            is_address_update = true;
            final String countyDistrict = command.stringValueOfParameterNamed("countyDistrict");
            addobj.setCountyDistrict(countyDistrict);
        }

        if (command.longValueOfParameterNamed("stateProvinceId") != null) {
            if (command.longValueOfParameterNamed("stateProvinceId") != 0) {
                is_address_update = true;
                stateId = command.longValueOfParameterNamed("stateProvinceId");
                stateIdobj = this.codeValueRepository.getReferenceById(stateId);
                addobj.setStateProvince(stateIdobj);
            }

        }
        if (command.longValueOfParameterNamed("countryId") != null) {
            if (command.longValueOfParameterNamed("countryId") != 0) {
                is_address_update = true;
                countryId = command.longValueOfParameterNamed("countryId");
                countryIdObj = this.codeValueRepository.getReferenceById(countryId);
                addobj.setCountry(countryIdObj);
            }

        }

        if (!command.stringValueOfParameterNamed("postalCode").isEmpty()) {
            is_address_update = true;
            final String postalCode = command.stringValueOfParameterNamed("postalCode");
            addobj.setPostalCode(postalCode);
        }

        if (command.bigDecimalValueOfParameterNamed("latitude") != null) {

            is_address_update = true;
            final BigDecimal latitude = command.bigDecimalValueOfParameterNamed("latitude");

            addobj.setLatitude(latitude);
        }
        if (command.bigDecimalValueOfParameterNamed("longitude") != null) {
            is_address_update = true;
            final BigDecimal longitude = command.bigDecimalValueOfParameterNamed("longitude");
            addobj.setLongitude(longitude);

        }

        if (is_address_update) {
            addobj.setUpdatedOn(LocalDate.now(DateUtils.getDateTimeZoneOfTenant()));
            this.addressRepository.save(addobj);

        }

        final Boolean testActive = command.booleanPrimitiveValueOfParameterNamed("isActive");
        if (testActive != null) {
            final boolean active = command.booleanPrimitiveValueOfParameterNamed("isActive");
            if (!active && clientAddressObj.isPrimary()) {
                clientAddressObj.setPrimary(false);
            }
            clientAddressObj.setIs_active(active);
        }

        if (command.parameterExists("isPrimary")) {
            final boolean primary = command.booleanPrimitiveValueOfParameterNamed("isPrimary");
            if (!primary && clientAddressObj.isPrimary()) {
                final long addressCount = this.clientAddressRepository.countByClient_Id(clientId);
                if (addressCount <= 1) {
                    throwPrimaryAddressRequiredValidationError();
                }
                final List<ClientAddress> otherPrimaryCandidates = this.clientAddressRepository.findByClient_Id(clientId).stream()
                        .filter(address -> !address.getId().equals(clientAddressObj.getId())).toList();
                if (otherPrimaryCandidates.isEmpty()) {
                    throwPrimaryAddressRequiredValidationError();
                }
            }
            if (primary) {
                applyPrimaryAddress(clientAddressObj.getClient(), clientAddressObj, true);
            } else {
                clientAddressObj.setPrimary(false);
            }
        }

        this.clientAddressRepository.saveAndFlush(clientAddressObj);
        ensureClientHasPrimaryAddress(clientId);

        return new CommandProcessingResultBuilder() //
                .withCommandId(command.commandId()) //
                .withEntityId(clientAddressObj.getId()) //
                .build();
    }

    private void throwPrimaryAddressRequiredValidationError() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("Address");
        baseDataValidator.reset().parameter("isPrimary").failWithCode("primary.address.required",
                "Customer must have a primary address. Mark another address as primary before removing this one.");
        throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                dataValidationErrors);
    }

    private void throwInactiveAddressCannotBePrimaryValidationError() {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("Address");
        baseDataValidator.reset().parameter("isPrimary").failWithCode("inactive.address.cannot.be.primary",
                "An inactive address cannot be marked as primary.");
        throw new PlatformApiDataValidationException("validation.msg.validation.errors.exist", "Validation errors exist.",
                dataValidationErrors);
    }
}
