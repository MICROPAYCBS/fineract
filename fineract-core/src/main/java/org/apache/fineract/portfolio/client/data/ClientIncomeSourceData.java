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

package org.apache.fineract.portfolio.client.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;

@Getter
@Builder
@Schema(description = "Client income source. Template lookups are populated only by the template resource.")
public final class ClientIncomeSourceData implements Serializable {

    @Schema(description = "Income source identifier.", example = "1")
    private final Long id;

    @Schema(description = "Client identifier.", example = "1")
    private final Long clientId;

    @Schema(description = "Income source type code value identifier.", example = "1")
    private final Long incomeSourceTypeId;

    @Schema(description = "Income source type name.", example = "Employment")
    private final String incomeSourceType;

    @Schema(description = "Source of funds code value identifier.", example = "2")
    private final Long sourceOfFundsId;

    @Schema(description = "Source of funds name.", example = "Salary")
    private final String sourceOfFunds;

    @Schema(description = "Employer or business name. Maximum length 200.", example = "Example Ltd")
    private final String employerBusinessName;

    @Schema(description = "Employer address. Maximum length 500.", example = "1 High Street")
    private final String employerAddress;

    @Schema(description = "Occupation. Maximum length 100.", example = "Accountant")
    private final String occupation;

    @Schema(description = "Sub-industry identifier.", example = "3")
    private final Long subIndustryId;

    @Schema(description = "Sub-industry name.", example = "Retail trade")
    private final String subIndustryName;

    @Schema(description = "Monthly income. Zero or greater.", example = "1500.00")
    private final BigDecimal monthlyIncome;

    @Schema(description = "ISO currency code. Maximum length 3.", example = "UGX")
    private final String incomeCurrencyCode;

    @Schema(description = "Income frequency code value identifier.", example = "4")
    private final Long incomeFrequencyId;

    @Schema(description = "Income frequency name.", example = "Monthly")
    private final String incomeFrequency;

    @Schema(description = "Date the income started.", example = "2024-01-15", format = "date")
    private final LocalDate startDate;

    @Schema(description = "Date the income ended.", example = "2024-12-31", format = "date")
    private final LocalDate endDate;

    @Schema(description = "Whether this is the client's primary income source.", example = "true")
    private final Boolean isPrimarySource;

    @Schema(description = "Verification status code value identifier.", example = "5")
    private final Long verificationStatusId;

    @Schema(description = "Verification status name.", example = "Verified")
    private final String verificationStatus;

    @Schema(description = "User identifier of the verifier.", example = "1")
    private final Long verifiedBy;

    @Schema(description = "Username of the verifier.", example = "mifos")
    private final String verifiedByUsername;

    @Schema(description = "UTC timestamp when the income source was verified.", example = "2024-01-15T10:30:00Z", format = "date-time")
    private final OffsetDateTime verifiedOnUtc;

    @Schema(description = "Supporting document reference. Maximum length 255.", example = "payslip-2024-01.pdf")
    private final String supportingDocument;

    @Schema(description = "Remarks. Maximum length 500.", example = "Confirmed with employer")
    private final String remarks;

    @Schema(description = "Income source status. Defaults to ACTIVE when omitted on create.", example = "ACTIVE")
    private final String status;

    @Schema(description = "Income source type code values. Populated on the template response.", example = "[{\"id\":1,\"name\":\"Employment\"}]")
    private final Collection<CodeValueData> incomeSourceTypeOptions;

    @Schema(description = "Source of funds code values. Populated on the template response.", example = "[{\"id\":2,\"name\":\"Salary\"}]")
    private final Collection<CodeValueData> sourceOfFundsOptions;

    @Schema(description = "Income frequency code values. Populated on the template response.", example = "[{\"id\":4,\"name\":\"Monthly\"}]")
    private final Collection<CodeValueData> incomeFrequencyOptions;

    @Schema(description = "Verification status code values. Populated on the template response.", example = "[{\"id\":5,\"name\":\"Verified\"}]")
    private final Collection<CodeValueData> verificationStatusOptions;
}
