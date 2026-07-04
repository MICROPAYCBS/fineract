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
package org.apache.fineract.organisation.teller.domain;

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

@Entity
@Table(name = "m_cashier_transaction_legal_tender")
@Getter
@Setter
@NoArgsConstructor
public class CashierTransactionLegalTender extends AbstractPersistableCustom<Long> {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "cashier_transaction_id", nullable = false)
    private CashierTransaction cashierTransaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "legal_tender_id", nullable = false)
    private CurrencyLegalTender legalTender;

    @Column(name = "quantity", nullable = false)
    private Integer quantity;

    @Column(name = "line_amount", nullable = false, scale = 6, precision = 19)
    private BigDecimal lineAmount;

    public static CashierTransactionLegalTender createNew(final CashierTransaction cashierTransaction,
            final CurrencyLegalTender legalTender, final Integer quantity, final BigDecimal lineAmount) {
        final CashierTransactionLegalTender line = new CashierTransactionLegalTender();
        line.setCashierTransaction(cashierTransaction);
        line.setLegalTender(legalTender);
        line.setQuantity(quantity);
        line.setLineAmount(lineAmount);
        return line;
    }
}
