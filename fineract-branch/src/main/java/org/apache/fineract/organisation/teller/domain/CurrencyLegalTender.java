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
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

@Entity
@Table(name = "m_currency_legal_tender")
@Getter
@Setter
@NoArgsConstructor
public class CurrencyLegalTender extends AbstractPersistableCustom<Long> {

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Column(name = "value", nullable = false, scale = 6, precision = 19)
    private BigDecimal value;

    @Column(name = "tender_type", nullable = false)
    private Integer tenderType;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;

    public LegalTenderType getLegalTenderTypeEnum() {
        return LegalTenderType.fromInt(this.tenderType);
    }

    public void setLegalTenderTypeEnum(final LegalTenderType legalTenderType) {
        this.tenderType = legalTenderType.getId();
    }
}
