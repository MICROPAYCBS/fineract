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
import java.time.OffsetDateTime;

public record TreasuryAccountData(Long id, String code, String name, String accountType, Long glAccountId, String glAccountCode,
        String glAccountName, Long officeId, String officeName, String currencyCode, String counterpartyName, String externalAccountNumber,
        BigDecimal minBalance, BigDecimal maxBalance, String status, OffsetDateTime createdOnUtc, OffsetDateTime lastModifiedOnUtc) {
}
