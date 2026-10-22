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
package org.apache.fineract.treasury.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.accounting.common.AccountingConstants.CashAccountsForSavings;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMapping;
import org.apache.fineract.accounting.producttoaccountmapping.domain.ProductToGLAccountMappingRepository;
import org.apache.fineract.portfolio.PortfolioProductType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Read-only checks against upstream reference data. Treasury links to upstream tables by id only, so these are plain
 * JDBC reads rather than entity lookups.
 */
@Component
@RequiredArgsConstructor
public class TreasuryReferenceDataLookup {

    /** {@code GLAccountType.ASSET} */
    static final int GL_CLASSIFICATION_ASSET = 1;
    /** {@code GLAccountUsage.DETAIL} */
    static final int GL_USAGE_DETAIL = 1;

    private final JdbcTemplate jdbcTemplate;
    private final ProductToGLAccountMappingRepository productMappingRepository;

    public record GlAccountInfo(Long id, int classification, int usage, boolean disabled) {

        public boolean isAssetDetail() {
            return this.classification == GL_CLASSIFICATION_ASSET && this.usage == GL_USAGE_DETAIL;
        }

        public boolean isDetail() {
            return this.usage == GL_USAGE_DETAIL;
        }
    }

    public Optional<GlAccountInfo> findGlAccount(final Long glAccountId) {
        final List<GlAccountInfo> rows = this.jdbcTemplate.query(
                "SELECT id, classification_enum, account_usage, disabled FROM acc_gl_account WHERE id = ?",
                (rs, rowNum) -> new GlAccountInfo(rs.getLong("id"), rs.getInt("classification_enum"), rs.getInt("account_usage"),
                        rs.getBoolean("disabled")),
                glAccountId);
        return rows.stream().findFirst();
    }

    public boolean officeExists(final Long officeId) {
        final Long count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_office WHERE id = ?", Long.class, officeId);
        return count != null && count > 0;
    }

    public boolean isOrganisationCurrency(final String currencyCode) {
        final Long count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_organisation_currency WHERE code = ?", Long.class,
                currencyCode);
        return count != null && count > 0;
    }

    public boolean isActivePaymentType(final Long paymentTypeId) {
        final Long count = this.jdbcTemplate.queryForObject("SELECT COUNT(*) FROM m_payment_type WHERE id = ? AND is_active = ?",
                Long.class, paymentTypeId, Boolean.TRUE);
        return count != null && count > 0;
    }

    public boolean isCashPaymentType(final Long paymentTypeId) {
        final List<Boolean> cash = this.jdbcTemplate.queryForList("SELECT is_cash_payment FROM m_payment_type WHERE id = ?", Boolean.class,
                paymentTypeId);
        return !cash.isEmpty() && Boolean.TRUE.equals(cash.getFirst());
    }

    /** {@code SavingsAccountStatusType.ACTIVE} */
    static final int SAVINGS_STATUS_ACTIVE = 300;

    public record SavingsAccountInfo(Long id, String accountNo, Long clientId, Long productId, int status, String currencyCode,
            Long officeId, BigDecimal balance) {

        public boolean isActive() {
            return this.status == SAVINGS_STATUS_ACTIVE;
        }
    }

    /** A client savings account with the client's office, which is where its journal entries are posted. */
    public Optional<SavingsAccountInfo> findClientSavingsAccount(final Long savingsAccountId) {
        final List<SavingsAccountInfo> rows = this.jdbcTemplate.query("""
                SELECT sa.id, sa.account_no, sa.client_id, sa.product_id, sa.status_enum, sa.currency_code, c.office_id,
                       sa.account_balance_derived
                FROM m_savings_account sa
                JOIN m_client c ON c.id = sa.client_id
                WHERE sa.id = ?
                """,
                (rs, rowNum) -> new SavingsAccountInfo(rs.getLong("id"), rs.getString("account_no"), rs.getLong("client_id"),
                        rs.getLong("product_id"), rs.getInt("status_enum"), rs.getString("currency_code"), rs.getLong("office_id"),
                        rs.getBigDecimal("account_balance_derived")),
                savingsAccountId);
        return rows.stream().findFirst();
    }

    /**
     * The GL account a savings deposit or withdrawal with this payment type posts its cash leg to: the product's
     * payment-channel-to-fund-source mapping for the payment type, or else the product's default fund source. This is
     * the lookup the upstream savings accounting makes ({@code AccountingProcessorHelper}).
     */
    public Optional<Long> savingsFundSourceGlAccount(final Long savingsProductId, final Long paymentTypeId) {
        final int savings = PortfolioProductType.SAVING.getValue();
        final int fundSource = CashAccountsForSavings.SAVINGS_REFERENCE.getValue();
        ProductToGLAccountMapping mapping = this.productMappingRepository
                .findByProductIdAndProductTypeAndFinancialAccountTypeAndPaymentTypeId(savingsProductId, savings, fundSource, paymentTypeId);
        if (mapping == null) {
            mapping = this.productMappingRepository.findCoreProductToFinAccountMapping(savingsProductId, savings, fundSource);
        }
        return Optional.ofNullable(mapping).map(ProductToGLAccountMapping::getGlAccount).map(GLAccount::getId);
    }
}
