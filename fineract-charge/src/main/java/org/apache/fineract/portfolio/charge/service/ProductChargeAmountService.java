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

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.portfolio.charge.data.ProductChargeLink;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Persists/reads optional amount overrides on product–charge join rows (ManyToMany tables).
 */
@Service
@RequiredArgsConstructor
public class ProductChargeAmountService {

    private final JdbcTemplate jdbcTemplate;

    public void syncLoanProductChargeAmounts(final Long productId, final Collection<ProductChargeLink> links) {
        syncAmounts("m_product_loan_charge", "product_loan_id", productId, links);
    }

    public void syncSavingsProductChargeAmounts(final Long productId, final Collection<ProductChargeLink> links) {
        syncAmounts("m_savings_product_charge", "savings_product_id", productId, links);
    }

    public Map<Long, BigDecimal> getLoanProductChargeAmounts(final Long productId) {
        return loadAmounts("m_product_loan_charge", "product_loan_id", productId);
    }

    public Map<Long, BigDecimal> getSavingsProductChargeAmounts(final Long productId) {
        return loadAmounts("m_savings_product_charge", "savings_product_id", productId);
    }

    private void syncAmounts(final String table, final String productColumn, final Long productId,
            final Collection<ProductChargeLink> links) {
        if (productId == null || links == null) {
            return;
        }
        // Clear then set — join rows were just rewritten by JPA ManyToMany with NULL amount
        this.jdbcTemplate.update("update " + table + " set amount = null where " + productColumn + " = ?", productId);
        for (final ProductChargeLink link : links) {
            if (link.amount() != null) {
                this.jdbcTemplate.update("update " + table + " set amount = ? where " + productColumn + " = ? and charge_id = ?",
                        link.amount(), productId, link.chargeId());
            }
        }
    }

    private Map<Long, BigDecimal> loadAmounts(final String table, final String productColumn, final Long productId) {
        final String sql = "select charge_id, amount from " + table + " where " + productColumn + " = ? and amount is not null";
        final List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(sql, productId);
        final Map<Long, BigDecimal> result = new HashMap<>();
        for (final Map<String, Object> row : rows) {
            final Number chargeId = (Number) row.get("charge_id");
            final BigDecimal amount = (BigDecimal) row.get("amount");
            if (chargeId != null && amount != null) {
                result.put(chargeId.longValue(), amount);
            }
        }
        return result;
    }
}
