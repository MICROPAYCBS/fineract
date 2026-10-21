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
package org.apache.fineract.portfolio.customerclass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_customer_class")
@Getter
@Setter
@NoArgsConstructor
public class CustomerClass extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "class_code", length = 20, nullable = false, unique = true)
    private String classCode;

    @Column(name = "class_name", length = 100, nullable = false)
    private String className;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "legal_form_enum", nullable = false)
    private Integer legalFormEnum = 1;

    @Column(name = "customer_type", length = 20)
    private String customerType;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "kyc_level", length = 20)
    private String kycLevel;

    @Column(name = "loan_eligible", length = 1, nullable = false)
    private String loanEligible = "Y";

    @Column(name = "restriction_id")
    private Long restrictionId;

    @Column(name = "overdraft_allowed", length = 1, nullable = false)
    private String overdraftAllowed = "N";

    @Column(name = "enhanced_due_diligence", length = 1, nullable = false)
    private String enhancedDueDiligence = "N";

    @Column(name = "reclassification_allowed", length = 1, nullable = false)
    private String reclassificationAllowed = "Y";

    @Column(name = "min_age")
    private Integer minAge;

    @Column(name = "max_age")
    private Integer maxAge;

    @Column(name = "enforce_cust_photo", length = 1, nullable = false)
    private String enforceCustPhoto = "Y";

    @Column(name = "enforce_cust_signature", length = 1, nullable = false)
    private String enforceCustSignature = "Y";

    @Column(name = "enforce_cust_document", length = 1, nullable = false)
    private String enforceCustDocument = "Y";

    @Column(name = "auto_create_account", length = 1, nullable = false)
    private String autoCreateAccount = "N";

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
