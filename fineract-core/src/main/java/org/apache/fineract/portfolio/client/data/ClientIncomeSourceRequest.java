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
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Client income source create or update request")
public class ClientIncomeSourceRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Optional income source identifier. The path supplies the identifier on update.", example = "1")
    private Long id;

    @Schema(description = "Optional client identifier. The path supplies the client on create.", example = "1")
    private Long clientId;

    @Schema(description = "Income source type code value identifier. Required on create and must be greater than zero. On update, optional, and must be greater than zero when supplied.", example = "1")
    private Long incomeSourceTypeId;

    @Schema(description = "Source of funds code value identifier. Optional. Must be greater than zero when supplied.", example = "2")
    private Long sourceOfFundsId;

    @Schema(description = "Employer or business name. Optional. Maximum length 200.", example = "Example Ltd", maxLength = 200)
    private String employerBusinessName;

    @Schema(description = "Employer address. Optional. Maximum length 500.", example = "1 High Street", maxLength = 500)
    private String employerAddress;

    @Schema(description = "Occupation. Optional. Maximum length 100.", example = "Accountant", maxLength = 100)
    private String occupation;

    @Schema(description = "Sub-industry identifier. Optional. Must be greater than zero when supplied.", example = "3")
    private Long subIndustryId;

    @Schema(description = "Monthly income. Optional. Zero or greater. Parsed with locale in this request.", example = "1500.00")
    private BigDecimal monthlyIncome;

    @Schema(description = "ISO currency code. Optional. Maximum length 3.", example = "UGX", maxLength = 3)
    private String incomeCurrencyCode;

    @Schema(description = "Income frequency code value identifier. Optional. Must be greater than zero when supplied.", example = "4")
    private Long incomeFrequencyId;

    @Schema(description = "Date the income started. A date string interpreted with dateFormat and locale in this request.", example = "15 January 2024")
    private String startDate;

    @Schema(description = "Date the income ended. A date string interpreted with dateFormat and locale in this request. Must be on or after startDate when both are set.", example = "31 December 2024")
    private String endDate;

    @Schema(description = "Whether this is the client's primary income source. Optional. Setting true clears the primary flag on the client's other income sources.", example = "true")
    private Boolean isPrimarySource;

    @Schema(description = "Verification status code value identifier. Optional.", example = "5")
    private Long verificationStatusId;

    @Schema(description = "Supporting document reference. Optional. Maximum length 255.", example = "payslip-2024-01.pdf", maxLength = 255)
    private String supportingDocument;

    @Schema(description = "Remarks. Optional. Maximum length 500.", example = "Confirmed with employer", maxLength = 500)
    private String remarks;

    @Schema(description = "Income source status. Optional. Defaults to ACTIVE on create when omitted.", example = "ACTIVE")
    private String status;

    @Schema(description = "Pattern used with locale to interpret startDate and endDate. Defaults to dd MMMM yyyy when omitted.", example = "dd MMMM yyyy")
    private String dateFormat;

    @Schema(description = "Locale used with dateFormat to interpret startDate and endDate, and to parse monthlyIncome.", example = "en")
    private String locale;
}
