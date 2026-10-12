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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Other bank account submitted with a compliance profile")
public class ClientOtherBankAccountRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Optional existing other-bank-account identifier.", example = "1")
    private Long id;

    @Schema(description = "Bank name. Required when the account is not blank. Maximum length 200.", example = "Example Bank", maxLength = 200)
    private String bankName;

    @Schema(description = "Branch name. Optional. Maximum length 200.", example = "Main Branch", maxLength = 200)
    private String branchName;

    @Schema(description = "Account number. Required when the account is not blank. Maximum length 50.", example = "0012345678", maxLength = 50)
    private String accountNumber;

    @Schema(description = "Display order from 1 to 2. Defaults to the account position in the list when omitted.", example = "1")
    private Integer displayOrder;
}
