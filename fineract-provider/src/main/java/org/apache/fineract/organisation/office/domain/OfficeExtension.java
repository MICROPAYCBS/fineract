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
package org.apache.fineract.organisation.office.domain;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_office_extension")
@AttributeOverride(name = "id", column = @Column(name = "office_id"))
@Getter
@Setter
@NoArgsConstructor
public class OfficeExtension extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "office_code", length = 10, unique = true)
    private String officeCode;

    @Column(name = "branch_type", length = 30)
    private String branchType;

    @Column(name = "region_code", length = 20)
    private String regionCode;

    @Column(name = "address", length = 255)
    private String address;

    @Column(name = "city", length = 50)
    private String city;

    @Column(name = "country_code", length = 10)
    private String countryCode;

    @Column(name = "phone_no", length = 30)
    private String phoneNo;

    @Column(name = "email_address", length = 100)
    private String emailAddress;

    @Column(name = "manager_staff_id")
    private Long managerStaffId;

    @Column(name = "swift_code", length = 30)
    private String swiftCode;

    @Column(name = "latitude", length = 50)
    private String latitude;

    @Column(name = "longitude", length = 50)
    private String longitude;

    @Column(name = "cash_limit", precision = 21, scale = 6)
    private BigDecimal cashLimit;

    @Column(name = "working_hours", length = 20)
    private String workingHours;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
