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
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Client compliance profile update request")
public class ClientComplianceProfileRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Whether the client has accounts at other banks. When true, otherBankAccounts must contain one or two accounts.", example = "false")
    private Boolean hasOtherBankAccounts;

    @Schema(description = "Whether the client is a politically exposed person. When true, pepPosition is required.", example = "false")
    private Boolean isPep;

    @Schema(description = "PEP position. Optional unless isPep is true, in which case it is required. Maximum length 200.", example = "Minister", maxLength = 200)
    private String pepPosition;

    @Schema(description = "Name of a PEP relative. Optional. Maximum length 200.", example = "Jane Doe", maxLength = 200)
    private String pepRelativeName;

    @Schema(description = "Whether the client is a United States citizen or resident.", example = "false")
    private Boolean usCitizenOrResident;

    @Schema(description = "Whether the client is FATCA registered. When true, fatcaRegistrationNo is required.", example = "false")
    private Boolean fatcaRegistered;

    @Schema(description = "FATCA registration number. Optional unless fatcaRegistered is true, in which case it is required. Maximum length 100.", example = "FATCA-10001", maxLength = 100)
    private String fatcaRegistrationNo;

    @Schema(description = "Deposit protection fund alternative bank name. Optional. Maximum length 200.", example = "Example Bank", maxLength = 200)
    private String dpfAlternativeBankName;

    @Schema(description = "Deposit protection fund alternative account number. Optional. Maximum length 50.", example = "0012345678", maxLength = 50)
    private String dpfAlternativeAccountNumber;

    @Schema(description = "Other bank accounts. Required, with one or two entries, when hasOtherBankAccounts is true. Blank entries are ignored.", example = "[{\"bankName\":\"Example Bank\",\"accountNumber\":\"0012345678\",\"displayOrder\":1}]")
    private List<ClientOtherBankAccountRequest> otherBankAccounts;

    @Schema(description = "Optional locale accepted with this request.", example = "en")
    private String locale;
}
