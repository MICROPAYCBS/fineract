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
package org.apache.fineract.portfolio.paymenttype.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/**
 * Interval during which a payment type was inactive system-wide. {@code endedOnDate} null means it is still inactive.
 */
@Entity
@Table(name = "m_payment_type_hold")
@Getter
@Setter
@NoArgsConstructor
@Accessors(chain = true)
public class PaymentTypeHold extends AbstractPersistableCustom<Long> {

    @ManyToOne(optional = false)
    @JoinColumn(name = "payment_type_id", nullable = false)
    private PaymentType paymentType;

    @Column(name = "started_on_date", nullable = false)
    private LocalDate startedOnDate;

    @Column(name = "ended_on_date")
    private LocalDate endedOnDate;

    public static PaymentTypeHold open(final PaymentType paymentType, final LocalDate startedOnDate) {
        return new PaymentTypeHold().setPaymentType(paymentType).setStartedOnDate(startedOnDate);
    }
}
