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

/**
 * An agent float with the live balance of its savings account and the level status against its minimum and maximum.
 * {@code pendingTopUps} and {@code pendingSweeps} are approved requests not yet executed.
 */
public record TreasuryAgentFloatData(Long id, Long clientId, String clientName, Long officeId, String officeName, Long savingsAccountId,
        String savingsAccountNo, String currencyCode, Long treasuryAccountId, String treasuryAccountCode, BigDecimal balance,
        BigDecimal minFloat, BigDecimal maxFloat, String levelStatus, BigDecimal pendingTopUps, BigDecimal pendingSweeps, String status) {
}
