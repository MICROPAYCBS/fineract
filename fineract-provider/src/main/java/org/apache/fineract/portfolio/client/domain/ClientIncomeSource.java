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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_client_income_source")
@Getter
@Setter
@NoArgsConstructor
public class ClientIncomeSource extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @Column(name = "income_source_type_id")
    private Long incomeSourceTypeId;

    @Column(name = "source_of_funds_id")
    private Long sourceOfFundsId;

    @Column(name = "employer_business_name", length = 200)
    private String employerBusinessName;

    @Column(name = "employer_address", length = 500)
    private String employerAddress;

    @Column(name = "occupation", length = 100)
    private String occupation;

    @Column(name = "sub_industry_id")
    private Long subIndustryId;

    @Column(name = "monthly_income", precision = 18, scale = 2)
    private BigDecimal monthlyIncome;

    @Column(name = "income_currency_code", length = 3)
    private String incomeCurrencyCode;

    @Column(name = "income_frequency_id")
    private Long incomeFrequencyId;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(name = "is_primary_source", length = 1, nullable = false)
    private String isPrimarySource = "N";

    @Column(name = "verification_status_id")
    private Long verificationStatusId;

    @Column(name = "verified_by")
    private Long verifiedBy;

    @Column(name = "verified_on_utc")
    private OffsetDateTime verifiedOnUtc;

    @Column(name = "supporting_document", length = 255)
    private String supportingDocument;

    @Column(name = "remarks", length = 500)
    private String remarks;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
