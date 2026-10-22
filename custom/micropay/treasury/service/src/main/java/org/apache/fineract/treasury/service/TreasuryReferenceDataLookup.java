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

import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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

    public record GlAccountInfo(Long id, int classification, int usage, boolean disabled) {

        public boolean isAssetDetail() {
            return this.classification == GL_CLASSIFICATION_ASSET && this.usage == GL_USAGE_DETAIL;
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
}
