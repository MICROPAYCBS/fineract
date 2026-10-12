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
import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Client compliance profile")
public final class ClientComplianceProfileData implements Serializable {

    @Schema(description = "Compliance profile identifier. Absent when the client has no saved profile.", example = "1")
    private final Long id;

    @Schema(description = "Client identifier.", example = "1")
    private final Long clientId;

    @Schema(description = "Whether the client has accounts at other banks.", example = "false")
    private final Boolean hasOtherBankAccounts;

    @Schema(description = "Whether the client is a politically exposed person.", example = "false")
    private final Boolean isPep;

    @Schema(description = "PEP position. Maximum length 200.", example = "Minister")
    private final String pepPosition;

    @Schema(description = "Name of a PEP relative. Maximum length 200.", example = "Jane Doe")
    private final String pepRelativeName;

    @Schema(description = "Whether the client is a United States citizen or resident.", example = "false")
    private final Boolean usCitizenOrResident;

    @Schema(description = "Whether the client is FATCA registered.", example = "false")
    private final Boolean fatcaRegistered;

    @Schema(description = "FATCA registration number. Maximum length 100.", example = "FATCA-10001")
    private final String fatcaRegistrationNo;

    @Schema(description = "Deposit protection fund alternative bank name. Maximum length 200.", example = "Example Bank")
    private final String dpfAlternativeBankName;

    @Schema(description = "Deposit protection fund alternative account number. Maximum length 50.", example = "0012345678")
    private final String dpfAlternativeAccountNumber;

    @Schema(description = "Other bank accounts held by the client. At most two accounts.", example = "[{\"id\":1,\"bankName\":\"Example Bank\",\"accountNumber\":\"0012345678\",\"displayOrder\":1}]")
    private final List<ClientOtherBankAccountData> otherBankAccounts;
}
