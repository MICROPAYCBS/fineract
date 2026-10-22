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
package org.apache.fineract.treasury.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * A reconciliation match: statement lines and GL journal entries of one treasury account whose amounts are equal, or
 * statement lines explained with a reason (and an adjusting entry when one was posted).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_recon_match")
public class TreasuryReconMatch extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "treasury_account_id", nullable = false)
    private Long treasuryAccountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_rule", nullable = false, length = 20)
    private TreasuryReconMatchRule rule;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TreasuryReconMatchStatus status;

    @Column(name = "matched_by", nullable = false)
    private Long matchedBy;

    @Column(name = "matched_on_date", nullable = false)
    private LocalDate matchedOnDate;

    /** Code value of {@code TreasuryReconExplanation} for an explanation. */
    @Column(name = "reason_code_value_id")
    private Long reasonCodeValueId;

    @Column(name = "note", length = 500)
    private String note;

    /** Journal transaction id of the adjusting entry an explanation posted. */
    @Column(name = "adjustment_transaction_id", length = 50)
    private String adjustmentTransactionId;

    @Column(name = "unmatched_by")
    private Long unmatchedBy;

    @Column(name = "unmatched_on_date")
    private LocalDate unmatchedOnDate;

    @Column(name = "unmatch_reason", length = 500)
    private String unmatchReason;

    public boolean isActive() {
        return this.status == TreasuryReconMatchStatus.ACTIVE;
    }
}
