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
package org.apache.fineract.portfolio.customerclass.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Customer class create or update request")
public class CustomerClassRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Customer class code. Required on create and when supplied on update. Maximum length 20.", example = "RETAIL", maxLength = 20)
    private String classCode;

    @Schema(description = "Customer class name. Required on create and when supplied on update. Maximum length 100.", example = "Retail individual", maxLength = 100)
    private String className;

    @Schema(description = "Description. Optional. Maximum length 255.", example = "Standard retail customers", maxLength = 255)
    private String description;

    @Schema(description = "Legal form. Required on create and when supplied on update. Allowed values: 1 (Person), 2 (Entity).", example = "1")
    private Integer legalFormId;

    @Schema(description = "Customer type. Optional. On create, allowed values are GROUP and JOINT. On update, allowed values are INDIVIDUAL, CORPORATE, GROUP, and JOINT.", example = "GROUP")
    private String customerType;

    @Schema(description = "Risk level. Optional. Allowed values: LOW, MEDIUM, HIGH.", example = "LOW")
    private String riskLevel;

    @Schema(description = "KYC level. Optional. Allowed values: BASIC, STANDARD, ENHANCED.", example = "STANDARD")
    private String kycLevel;

    @Schema(description = "Whether clients in this class may take loans. Optional.", example = "true")
    private Boolean loanEligible;

    @Schema(description = "Restriction identifier. Optional. Must be greater than zero when supplied.", example = "1")
    private Long restrictionId;

    @Schema(description = "Whether overdraft is allowed. Optional.", example = "false")
    private Boolean overdraftAllowed;

    @Schema(description = "Whether enhanced due diligence is required. Optional.", example = "false")
    private Boolean enhancedDueDiligence;

    @Schema(description = "Whether the client may be reclassified. Optional.", example = "true")
    private Boolean reclassificationAllowed;

    @Schema(description = "Minimum age. Optional. Zero or greater. Not allowed when legalFormId is 2 (Entity).", example = "18")
    private Integer minAge;

    @Schema(description = "Maximum age. Optional. Greater than zero and at least minAge. Not allowed when legalFormId is 2 (Entity).", example = "65")
    private Integer maxAge;

    @Schema(description = "Whether a customer photo is required. Optional.", example = "true")
    private Boolean enforceCustPhoto;

    @Schema(description = "Whether a customer signature is required. Optional.", example = "true")
    private Boolean enforceCustSignature;

    @Schema(description = "Whether a customer document is required. Optional.", example = "true")
    private Boolean enforceCustDocument;

    @Schema(description = "Whether an account is created automatically. Optional.", example = "false")
    private Boolean autoCreateAccount;

    @Schema(description = "Status. Optional. Allowed values: ACTIVE, INACTIVE.", example = "ACTIVE")
    private String status;
}
