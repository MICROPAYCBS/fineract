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
import java.time.LocalDate;
import java.util.List;

/** A stored regulatory liquidity return; {@code provisional} until MICROPAY confirms the layout (Q7). */
public record TreasuryReturnData(Long runId, String layout, boolean provisional, LocalDate asOfDate, String currencyCode, Long officeId,
        String ruleSetCode, int ruleSetVersion, Integer assumptionSetVersion, Long ratiosRunId, Long ladderRunId, String status,
        Long submittedBy, LocalDate submittedOnDate, List<Line> lines) {

    public record Line(int seq, String section, String lineCode, String label, BigDecimal amount, String text) {
    }
}
