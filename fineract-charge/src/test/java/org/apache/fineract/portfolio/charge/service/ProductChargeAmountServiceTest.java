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
package org.apache.fineract.portfolio.charge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.fineract.portfolio.charge.data.ProductChargeLink;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ProductChargeAmountServiceTest {

    private JdbcTemplate jdbcTemplate;
    private ProductChargeAmountService service;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        service = new ProductChargeAmountService(jdbcTemplate);
    }

    @Test
    void syncLoanWritesOverrideAmounts() {
        final Charge charge = mock(Charge.class);
        when(charge.getId()).thenReturn(5L);
        final List<ProductChargeLink> links = List.of(new ProductChargeLink(charge, new BigDecimal("3.25")));

        service.syncLoanProductChargeAmounts(99L, links);

        verify(jdbcTemplate).update(eq("update m_product_loan_charge set amount = null where product_loan_id = ?"), eq(99L));
        verify(jdbcTemplate).update(eq("update m_product_loan_charge set amount = ? where product_loan_id = ? and charge_id = ?"),
                eq(new BigDecimal("3.25")), eq(99L), eq(5L));
    }

    @Test
    void loadSavingsOverridesMapsChargeIdToAmount() {
        when(jdbcTemplate.queryForList(eq("select charge_id, amount from m_savings_product_charge where savings_product_id = ? and amount is not null"),
                eq(7L))).thenReturn(List.of(Map.of("charge_id", 3L, "amount", new BigDecimal("1.10"))));

        final Map<Long, BigDecimal> amounts = service.getSavingsProductChargeAmounts(7L);

        assertEquals(1, amounts.size());
        assertEquals(0, new BigDecimal("1.10").compareTo(amounts.get(3L)));
    }

    @Test
    void syncSavingsClearsWhenNoOverrides() {
        final Charge charge = mock(Charge.class);
        when(charge.getId()).thenReturn(8L);
        service.syncSavingsProductChargeAmounts(12L, List.of(new ProductChargeLink(charge, null)));

        verify(jdbcTemplate).update(eq("update m_savings_product_charge set amount = null where savings_product_id = ?"), eq(12L));
    }
}
