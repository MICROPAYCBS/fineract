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
package org.apache.fineract.treasury.data;

import java.math.BigDecimal;
import java.util.Set;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class TreasuryAccountRequest {

    private String code;
    private String name;
    private String accountType;
    private Long glAccountId;
    private Long officeId;
    private String currencyCode;
    private String counterpartyName;
    private String externalAccountNumber;
    private BigDecimal minBalance;
    private BigDecimal maxBalance;

    /** The parameters present in the request, so an update changes only those (an explicit null clears a field). */
    private transient Set<String> parametersPassed = Set.of();

    public boolean has(final String parameterName) {
        return this.parametersPassed.contains(parameterName);
    }
}
