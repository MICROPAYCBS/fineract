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

package org.apache.fineract.portfolio.client.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_client_compliance_profile")
@Getter
@Setter
@NoArgsConstructor
public class ClientComplianceProfile extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "has_other_bank_accounts", length = 1, nullable = false)
    private String hasOtherBankAccounts = "N";

    @Column(name = "is_pep", length = 1, nullable = false)
    private String isPep = "N";

    @Column(name = "pep_position", length = 200)
    private String pepPosition;

    @Column(name = "pep_relative_name", length = 200)
    private String pepRelativeName;

    @Column(name = "us_citizen_or_resident", length = 1, nullable = false)
    private String usCitizenOrResident = "N";

    @Column(name = "fatca_registered", length = 1, nullable = false)
    private String fatcaRegistered = "N";

    @Column(name = "fatca_registration_no", length = 100)
    private String fatcaRegistrationNo;

    @Column(name = "dpf_alternative_bank_name", length = 200)
    private String dpfAlternativeBankName;

    @Column(name = "dpf_alternative_account_number", length = 50)
    private String dpfAlternativeAccountNumber;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
