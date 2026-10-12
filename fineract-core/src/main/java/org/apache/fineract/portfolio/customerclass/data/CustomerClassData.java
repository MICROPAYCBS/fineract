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
@Schema(description = "Customer class")
public class CustomerClassData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Customer class identifier.", example = "1")
    private Long id;

    @Schema(description = "Customer class code. Maximum length 20.", example = "RETAIL")
    private String classCode;

    @Schema(description = "Customer class name. Maximum length 100.", example = "Retail individual")
    private String className;

    @Schema(description = "Description. Maximum length 255.", example = "Standard retail customers")
    private String description;

    @Schema(description = "Legal form. Allowed values: 1 (Person), 2 (Entity).", example = "1")
    private Integer legalFormId;

    @Schema(description = "Customer type. Allowed values: INDIVIDUAL, CORPORATE, GROUP, JOINT.", example = "GROUP")
    private String customerType;

    @Schema(description = "Risk level. Allowed values: LOW, MEDIUM, HIGH.", example = "LOW")
    private String riskLevel;

    @Schema(description = "KYC level. Allowed values: BASIC, STANDARD, ENHANCED.", example = "STANDARD")
    private String kycLevel;

    @Schema(description = "Whether clients in this class may take loans.", example = "true")
    private Boolean loanEligible;

    @Schema(description = "Restriction identifier.", example = "1")
    private Long restrictionId;

    @Schema(description = "Restriction code.", example = "NONE")
    private String restrictionCode;

    @Schema(description = "Restriction name.", example = "No restriction")
    private String restrictionName;

    @Schema(description = "Whether overdraft is allowed.", example = "false")
    private Boolean overdraftAllowed;

    @Schema(description = "Whether enhanced due diligence is required.", example = "false")
    private Boolean enhancedDueDiligence;

    @Schema(description = "Whether the client may be reclassified.", example = "true")
    private Boolean reclassificationAllowed;

    @Schema(description = "Minimum age. Zero or greater. Not used for entity legal form.", example = "18")
    private Integer minAge;

    @Schema(description = "Maximum age. Greater than zero and at least minAge. Not used for entity legal form.", example = "65")
    private Integer maxAge;

    @Schema(description = "Whether a customer photo is required.", example = "true")
    private Boolean enforceCustPhoto;

    @Schema(description = "Whether a customer signature is required.", example = "true")
    private Boolean enforceCustSignature;

    @Schema(description = "Whether a customer document is required.", example = "true")
    private Boolean enforceCustDocument;

    @Schema(description = "Whether an account is created automatically.", example = "false")
    private Boolean autoCreateAccount;

    @Schema(description = "Status. Allowed values: ACTIVE, INACTIVE.", example = "ACTIVE")
    private String status;
}
