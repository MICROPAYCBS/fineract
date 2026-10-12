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
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "Other bank account on a client compliance profile")
public final class ClientOtherBankAccountData implements Serializable {

    @Schema(description = "Other bank account identifier.", example = "1")
    private final Long id;

    @Schema(description = "Client identifier.", example = "1")
    private final Long clientId;

    @Schema(description = "Bank name. Maximum length 200.", example = "Example Bank")
    private final String bankName;

    @Schema(description = "Branch name. Maximum length 200.", example = "Main Branch")
    private final String branchName;

    @Schema(description = "Account number. Maximum length 50.", example = "0012345678")
    private final String accountNumber;

    @Schema(description = "Display order from 1 to 2.", example = "1")
    private final Integer displayOrder;
}
