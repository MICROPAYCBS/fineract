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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * One side of a match: a statement line or a journal entry, with its signed amount.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_recon_match_item")
public class TreasuryReconMatchItem extends AbstractPersistableCustom<Long> {

    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "statement_line_id")
    private Long statementLineId;

    @Column(name = "journal_entry_id")
    private Long journalEntryId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    public static TreasuryReconMatchItem line(final Long matchId, final Long lineId, final BigDecimal amount) {
        final TreasuryReconMatchItem item = new TreasuryReconMatchItem();
        item.setMatchId(matchId);
        item.setStatementLineId(lineId);
        item.setAmount(amount);
        return item;
    }

    public static TreasuryReconMatchItem entry(final Long matchId, final Long journalEntryId, final BigDecimal amount) {
        final TreasuryReconMatchItem item = new TreasuryReconMatchItem();
        item.setMatchId(matchId);
        item.setJournalEntryId(journalEntryId);
        item.setAmount(amount);
        return item;
    }
}
