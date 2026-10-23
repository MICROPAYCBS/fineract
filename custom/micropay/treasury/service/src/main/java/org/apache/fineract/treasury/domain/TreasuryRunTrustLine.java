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
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.treasury.trust.EMoneyCategory;
import org.apache.fineract.treasury.trust.TrustAssetType;

/** A line of a TRUST_RECON run: an e-money category, or an asset held in trust. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_trust_line")
public class TreasuryRunTrustLine extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    @Column(name = "line_type", nullable = false, length = 20)
    private String lineType;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", length = 20)
    private EMoneyCategory category;

    @Enumerated(EnumType.STRING)
    @Column(name = "asset_type", length = 20)
    private TrustAssetType assetType;

    @Column(name = "institution_id")
    private Long institutionId;

    @Column(name = "institution_name", length = 150)
    private String institutionName;

    @Column(name = "trust_account_id")
    private Long trustAccountId;

    @Column(name = "deal_id")
    private Long dealId;

    @Column(name = "label", length = 200)
    private String label;

    @Column(name = "amount", nullable = false, precision = 19, scale = 6)
    private BigDecimal amount;

    @Column(name = "eligible_amount", precision = 19, scale = 6)
    private BigDecimal eligible;

    @Column(name = "encumbered_amount", precision = 19, scale = 6)
    private BigDecimal encumbered;
}
