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
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/** A ladder bucket of a rule set: the flows due from {@code fromDay} to {@code toDay} days after the as-of date. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_bucket")
public class TreasuryBucket extends AbstractPersistableCustom<Long> {

    @Column(name = "rule_set_id", nullable = false)
    private Long ruleSetId;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "from_day", nullable = false)
    private int fromDay;

    /** Null for the open-ended last bucket. */
    @Column(name = "to_day")
    private Integer toDay;
}
