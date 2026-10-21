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

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

/**
 * How pre-approved limits are given for one loan product. Every night the clients in the segment (active clients,
 * agents only by default) are scored with the behavioural scorecard and get a limit from the band of their grade.
 *
 * <p>
 * In ENFORCED mode every loan on the product, whoever the client, must fit within the client's current limit at
 * approval and at disbursal; a client without a current limit cannot borrow on it. ADVISORY only records limits and
 * logs breaches.
 */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_scoring_limit_policy")
public class ScoringLimitPolicy extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "code", nullable = false, length = 100)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "scorecard_code", nullable = false, length = 100)
    private String scorecardCode;

    @Column(name = "loan_product_id", nullable = false)
    private Long loanProductId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scoring_mode", nullable = false, length = 20)
    private ScoringMode mode;

    /** Only clients flagged as staff, which is how agents are recorded. */
    @Column(name = "staff_only", nullable = false)
    private boolean staffOnly;

    @Column(name = "office_id")
    private Long officeId;

    @Column(name = "customer_class_id")
    private Long customerClassId;

    @Column(name = "valid_days", nullable = false)
    private Integer validDays;

    /** The characteristic each band's multiplier applies to, for example SAVINGS_MONTHLY_DEPOSITS_6M. */
    @Column(name = "base_characteristic", length = 200)
    private String baseCharacteristic;

    /** Limits are rounded down to a multiple of this amount. */
    @Column(name = "rounding_unit", precision = 19, scale = 6)
    private BigDecimal roundingUnit;

    @OneToMany(mappedBy = "policy", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("grade")
    private List<ScoringLimitBand> bands = new ArrayList<>();

    public void addBand(final ScoringLimitBand band) {
        band.setPolicy(this);
        this.bands.add(band);
    }

    public Optional<ScoringLimitBand> findBand(final String grade) {
        return grade == null ? Optional.empty() : this.bands.stream().filter(band -> band.getGrade().equalsIgnoreCase(grade)).findFirst();
    }
}
