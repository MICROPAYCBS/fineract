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
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Points awarded when a characteristic falls in this bin. A bin matches either a numeric range [lower, upper), an
 * exact value ({@code matchValue}, compared as text) or a missing value ({@code missingBin}).
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_scorecard_bin")
public class ScorecardBin extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scorecard_id", nullable = false)
    private Scorecard scorecard;

    @Column(name = "characteristic", nullable = false, length = 150)
    private String characteristic;

    @Column(name = "lower_bound", precision = 19, scale = 6)
    private BigDecimal lower;

    @Column(name = "upper_bound", precision = 19, scale = 6)
    private BigDecimal upper;

    @Column(name = "match_value", length = 100)
    private String matchValue;

    @Column(name = "is_missing_bin", nullable = false)
    private boolean missingBin;

    @Column(name = "points", nullable = false, precision = 19, scale = 6)
    private BigDecimal points;

    @Column(name = "reason_code", length = 50)
    private String reasonCode;

    public static ScorecardBin create(final String characteristic, final BigDecimal lower, final BigDecimal upper, final String matchValue,
            final boolean missingBin, final BigDecimal points, final String reasonCode) {
        final ScorecardBin bin = new ScorecardBin();
        bin.setCharacteristic(characteristic);
        bin.setLower(lower);
        bin.setUpper(upper);
        bin.setMatchValue(matchValue);
        bin.setMissingBin(missingBin);
        bin.setPoints(points);
        bin.setReasonCode(reasonCode);
        return bin;
    }
}
