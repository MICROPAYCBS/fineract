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
package org.apache.fineract.organisation.office.service;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.office.domain.OfficeExtension;
import org.apache.fineract.organisation.office.domain.OfficeExtensionRepository;
import org.apache.fineract.organisation.staff.domain.StaffRepository;
import org.apache.fineract.organisation.staff.exception.StaffNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OfficeExtensionWritePlatformServiceImpl implements OfficeExtensionWritePlatformService {

    public static final String BRANCH_PROFILE = "branchProfile";
    public static final String OFFICE_CODE = "officeCode";
    public static final String BRANCH_TYPE = "branchType";
    public static final String REGION_CODE = "regionCode";
    public static final String ADDRESS = "address";
    public static final String CITY = "city";
    public static final String COUNTRY_CODE = "countryCode";
    public static final String PHONE_NO = "phoneNo";
    public static final String EMAIL_ADDRESS = "emailAddress";
    public static final String MANAGER_STAFF_ID = "managerStaffId";
    public static final String SWIFT_CODE = "swiftCode";
    public static final String LATITUDE = "latitude";
    public static final String LONGITUDE = "longitude";
    public static final String CASH_LIMIT = "cashLimit";
    public static final String WORKING_HOURS = "workingHours";
    public static final String STATUS = "status";

    private final OfficeExtensionRepository officeExtensionRepository;
    private final StaffRepository staffRepository;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public Map<String, Object> saveOrUpdate(final Long officeId, final JsonCommand command) {
        if (!command.parameterExists(BRANCH_PROFILE)) {
            return Map.of();
        }
        final JsonElement branchProfileElement = command.parsedJson().getAsJsonObject().get(BRANCH_PROFILE);
        if (branchProfileElement == null || branchProfileElement.isJsonNull()) {
            return Map.of();
        }
        final JsonObject branchProfile = branchProfileElement.getAsJsonObject();
        final boolean isNew = !this.officeExtensionRepository.existsById(officeId);
        final OfficeExtension extension = this.officeExtensionRepository.findById(officeId).orElseGet(() -> {
            final OfficeExtension created = new OfficeExtension();
            created.setId(officeId);
            return created;
        });

        final Map<String, Object> changes = new HashMap<>();
        if (branchProfile.has(OFFICE_CODE)) {
            final String officeCode = this.fromApiJsonHelper.extractStringNamed(OFFICE_CODE, branchProfile);
            if (officeCode != null && this.officeExtensionRepository.existsByOfficeCodeAndIdNot(officeCode, officeId)) {
                throw new PlatformDataIntegrityException("error.msg.office.duplicate.code",
                        "Office with code `" + officeCode + "` already exists.", OFFICE_CODE, officeCode);
            }
            if (valueChanged(extension.getOfficeCode(), officeCode)) {
                extension.setOfficeCode(officeCode);
                changes.put(OFFICE_CODE, officeCode);
            }
        }
        patchString(branchProfile, BRANCH_TYPE, extension::getBranchType, extension::setBranchType, changes);
        patchString(branchProfile, REGION_CODE, extension::getRegionCode, extension::setRegionCode, changes);
        patchString(branchProfile, ADDRESS, extension::getAddress, extension::setAddress, changes);
        patchString(branchProfile, CITY, extension::getCity, extension::setCity, changes);
        patchString(branchProfile, COUNTRY_CODE, extension::getCountryCode, extension::setCountryCode, changes);
        patchString(branchProfile, PHONE_NO, extension::getPhoneNo, extension::setPhoneNo, changes);
        patchString(branchProfile, EMAIL_ADDRESS, extension::getEmailAddress, extension::setEmailAddress, changes);
        patchString(branchProfile, SWIFT_CODE, extension::getSwiftCode, extension::setSwiftCode, changes);
        patchString(branchProfile, LATITUDE, extension::getLatitude, extension::setLatitude, changes);
        patchString(branchProfile, LONGITUDE, extension::getLongitude, extension::setLongitude, changes);
        patchString(branchProfile, WORKING_HOURS, extension::getWorkingHours, extension::setWorkingHours, changes);
        patchString(branchProfile, STATUS, extension::getStatus, extension::setStatus, changes);
        if (branchProfile.has(MANAGER_STAFF_ID)) {
            final Long managerStaffId = this.fromApiJsonHelper.extractLongNamed(MANAGER_STAFF_ID, branchProfile);
            if (managerStaffId != null && !this.staffRepository.existsById(managerStaffId)) {
                throw new StaffNotFoundException(managerStaffId);
            }
            if (valueChanged(extension.getManagerStaffId(), managerStaffId)) {
                extension.setManagerStaffId(managerStaffId);
                changes.put(MANAGER_STAFF_ID, managerStaffId);
            }
        }
        if (branchProfile.has(CASH_LIMIT)) {
            final var cashLimit = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(CASH_LIMIT, branchProfile);
            if (valueChanged(extension.getCashLimit(), cashLimit)) {
                extension.setCashLimit(cashLimit);
                changes.put(CASH_LIMIT, cashLimit);
            }
        }
        if (!changes.isEmpty() || isNew) {
            if (extension.getId() == null) {
                extension.setId(officeId);
            }
            this.officeExtensionRepository.saveAndFlush(extension);
        }
        return changes;
    }

    private void patchString(final JsonObject json, final String paramName, final java.util.function.Supplier<String> getter,
            final java.util.function.Consumer<String> setter, final Map<String, Object> changes) {
        if (json.has(paramName)) {
            final String value = this.fromApiJsonHelper.extractStringNamed(paramName, json);
            if (valueChanged(getter.get(), value)) {
                setter.accept(value);
                changes.put(paramName, value);
            }
        }
    }

    private boolean valueChanged(final Object oldValue, final Object newValue) {
        if (oldValue == null) {
            return newValue != null;
        }
        return !oldValue.equals(newValue);
    }
}
