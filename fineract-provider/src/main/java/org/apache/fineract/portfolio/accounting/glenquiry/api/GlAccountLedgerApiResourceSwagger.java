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
package org.apache.fineract.portfolio.accounting.glenquiry.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

final class GlAccountLedgerApiResourceSwagger {

    private GlAccountLedgerApiResourceSwagger() {}

    @Schema(description = "GetGlAccountLedgerResponse")
    public static final class GetGlAccountLedgerResponse {

        private GetGlAccountLedgerResponse() {}

        @Schema(example = "15")
        public Long glAccountId;
        @Schema(example = "100001")
        public String glCode;
        @Schema(example = "Cash on Hand")
        public String glAccountName;
        @Schema(example = "1")
        public Long officeId;
        @Schema(example = "Head Office")
        public String officeName;
        @Schema(example = "2")
        public Long departmentId;
        @Schema(example = "Information Technology")
        public String departmentName;
        @Schema(example = "UGX")
        public String currencyCode;
        @Schema(example = "[2026, 7, 1]")
        public LocalDate startDate;
        @Schema(example = "[2026, 7, 16]")
        public LocalDate endDate;
        public GetGlAccountLedgerSummary summary;
        public List<GetGlAccountLedgerEntry> entries;
    }

    @Schema(description = "GetGlAccountLedgerSummary")
    public static final class GetGlAccountLedgerSummary {

        private GetGlAccountLedgerSummary() {}

        @Schema(example = "1000.00")
        public BigDecimal openingBalance;
        @Schema(example = "100.00")
        public BigDecimal totalDebit;
        @Schema(example = "0.00")
        public BigDecimal totalCredit;
        @Schema(example = "1100.00")
        public BigDecimal closingBalance;
        @Schema(example = "2026-07-10T14:32:01.123Z")
        public OffsetDateTime lastUpdated;
    }

    @Schema(description = "GetGlAccountLedgerEntry")
    public static final class GetGlAccountLedgerEntry {

        private GetGlAccountLedgerEntry() {}

        @Schema(example = "[2026, 7, 10]")
        public LocalDate entryDate;
        @Schema(example = "TXN-123")
        public String transactionId;
        @Schema(example = "Cash deposit")
        public String description;
        @Schema(example = "Manual")
        public String source;
        @Schema(example = "100.00")
        public BigDecimal debit;
        @Schema(example = "0.00")
        public BigDecimal credit;
        @Schema(example = "1100.00")
        public BigDecimal runningBalance;
    }
}
