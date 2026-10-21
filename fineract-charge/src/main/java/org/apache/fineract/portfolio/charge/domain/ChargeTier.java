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
package org.apache.fineract.portfolio.charge.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.charge.data.ChargeTierData;

@Entity
@Table(name = "m_charge_tier")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class ChargeTier extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "charge_id", nullable = false)
    private Charge charge;

    @Column(name = "amount_range_from", scale = 6, precision = 19, nullable = false)
    private BigDecimal amountRangeFrom;

    @Column(name = "amount_range_to", scale = 6, precision = 19)
    private BigDecimal amountRangeTo;

    @Column(name = "amount", scale = 6, precision = 19, nullable = false)
    private BigDecimal amount;

    public static ChargeTier create(final Charge charge, final BigDecimal amountRangeFrom, final BigDecimal amountRangeTo,
            final BigDecimal amount) {
        return new ChargeTier().setCharge(charge).setAmountRangeFrom(amountRangeFrom).setAmountRangeTo(amountRangeTo).setAmount(amount);
    }

    public boolean matches(final BigDecimal baseAmount) {
        if (baseAmount == null || amountRangeFrom == null) {
            return false;
        }
        final boolean fromInclusive = baseAmount.compareTo(amountRangeFrom) >= 0;
        final boolean toExclusive = amountRangeTo == null || baseAmount.compareTo(amountRangeTo) < 0;
        return fromInclusive && toExclusive;
    }

    public ChargeTierData toData() {
        return ChargeTierData.builder().id(getId()).amountRangeFrom(amountRangeFrom).amountRangeTo(amountRangeTo).amount(amount).build();
    }
}
