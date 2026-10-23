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
 * An asset class of a rule set version: which treasury account types, deal instrument kinds and deal types it holds,
 * whether it is liquid, the longest remaining maturity that still counts (deals), and its haircut. Lists are comma
 * separated.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_asset_class_rule")
public class TreasuryAssetClassRule extends AbstractPersistableCustom<Long> {

    @Column(name = "rule_set_id", nullable = false)
    private Long ruleSetId;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "label", nullable = false, length = 150)
    private String label;

    @Column(name = "is_liquid", nullable = false)
    private boolean liquid;

    @Column(name = "account_types", length = 200)
    private String accountTypes;

    @Column(name = "instrument_kinds", length = 200)
    private String instrumentKinds;

    @Column(name = "deal_type_ids", length = 200)
    private String dealTypeIds;

    /** The deals' accounting classifications counted, comma-separated; all when empty. */
    @Column(name = "classifications", length = 100)
    private String classifications;

    @Column(name = "max_maturity_days")
    private Integer maxMaturityDays;

    @Column(name = "haircut", nullable = false, precision = 19, scale = 6)
    private BigDecimal haircut = BigDecimal.ZERO;
}
