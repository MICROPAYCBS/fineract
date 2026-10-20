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
package org.apache.fineract.scoring.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * A knock-out or refer rule. When the rule fires, the decision outcome becomes at least {@link #outcome} and the rule's
 * reason code is recorded. For {@link ScoringRuleType#CHARACTERISTIC} rules the characteristic is compared with
 * {@link #value} using {@link #operator}.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_rule")
public class ScorecardRule extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", nullable = false, length = 40)
    private ScoringRuleType ruleType;

    @Column(name = "characteristic", length = 150)
    private String characteristic;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator", length = 10)
    private RuleOperator operator;

    @Column(name = "rule_value", length = 100)
    private String value;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false, length = 20)
    private ScoringOutcome outcome;

    @Column(name = "reason_code", nullable = false, length = 50)
    private String reasonCode;

    public static ScorecardRule create(final ScoringRuleType ruleType, final String characteristic, final RuleOperator operator,
            final String value, final ScoringOutcome outcome, final String reasonCode) {
        final ScorecardRule rule = new ScorecardRule();
        rule.setRuleType(ruleType);
        rule.setCharacteristic(characteristic);
        rule.setOperator(operator);
        rule.setValue(value);
        rule.setOutcome(outcome);
        rule.setReasonCode(reasonCode);
        return rule;
    }
}
