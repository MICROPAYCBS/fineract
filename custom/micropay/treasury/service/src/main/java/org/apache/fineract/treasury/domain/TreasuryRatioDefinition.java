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
import org.apache.fineract.treasury.liquidity.RatioDirection;

/** A ratio of a rule set version: numerator over denominator in percent, against a limit and a warning level. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_ratio_definition")
public class TreasuryRatioDefinition extends AbstractPersistableCustom<Long> {

    @Column(name = "rule_set_id", nullable = false)
    private Long ruleSetId;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Column(name = "code", nullable = false, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "direction", nullable = false, length = 10)
    private RatioDirection direction;

    /** The regulatory minimum (MIN) or maximum (MAX), percent. */
    @Column(name = "limit_value", nullable = false, precision = 19, scale = 6)
    private BigDecimal limitValue;

    @Column(name = "warning_value", precision = 19, scale = 6)
    private BigDecimal warningValue;

    /** Whether the ratio is reported in the regulatory return. */
    @Column(name = "in_return", nullable = false)
    private boolean inReturn;

    @Column(name = "numerator_label", length = 150)
    private String numeratorLabel;

    @Column(name = "denominator_label", length = 150)
    private String denominatorLabel;

    @Column(name = "description", length = 1000)
    private String description;
}
