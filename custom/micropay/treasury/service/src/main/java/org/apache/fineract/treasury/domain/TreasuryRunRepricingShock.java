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

/** The change in net interest income over twelve months of a REPRICING run for a parallel rate shock. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_run_repricing_shock")
public class TreasuryRunRepricingShock extends AbstractPersistableCustom<Long> {

    @Column(name = "run_id", nullable = false)
    private Long runId;

    /** Basis points, up or down. */
    @Column(name = "shock_bp", nullable = false)
    private int shockBp;

    @Column(name = "delta_nii", nullable = false, precision = 19, scale = 6)
    private BigDecimal deltaNii;

    /** The loss as a percentage of core capital. */
    @Column(name = "ear_percent", precision = 19, scale = 6)
    private BigDecimal earPercent;
}
