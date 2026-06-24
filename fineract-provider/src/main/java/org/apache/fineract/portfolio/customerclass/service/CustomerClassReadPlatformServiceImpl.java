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
package org.apache.fineract.portfolio.customerclass.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassData;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassTemplateData;
import org.apache.fineract.portfolio.customerclass.exception.CustomerClassNotFoundException;
import org.apache.fineract.portfolio.client.domain.LegalForm;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerClassReadPlatformServiceImpl implements CustomerClassReadPlatformService {

    private static final String RESOURCE_NAME = "CUSTOMERCLASS";

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class CustomerClassMapper implements RowMapper<CustomerClassData> {

        public String schema() {
            return " cc.id AS id, cc.class_code AS classCode, cc.class_name AS className, cc.description AS description,"
                    + " cc.legal_form_enum AS legalFormId, cc.customer_type AS customerType, cc.risk_level AS riskLevel,"
                    + " cc.kyc_level AS kycLevel, cc.loan_eligible AS loanEligibleFlag, cc.restriction_id AS restrictionId,"
                    + " r.restriction_code AS restrictionCode, r.restriction_name AS restrictionName,"
                    + " cc.overdraft_allowed AS overdraftAllowedFlag, cc.enhanced_due_diligence AS enhancedDueDiligenceFlag,"
                    + " cc.reclassification_allowed AS reclassificationAllowedFlag, cc.min_age AS minAge, cc.max_age AS maxAge,"
                    + " cc.enforce_cust_photo AS enforceCustPhotoFlag, cc.enforce_cust_signature AS enforceCustSignatureFlag,"
                    + " cc.enforce_cust_document AS enforceCustDocumentFlag, cc.auto_create_account AS autoCreateAccountFlag,"
                    + " cc.status AS status"
                    + " FROM m_customer_class cc"
                    + " LEFT JOIN m_restriction r ON cc.restriction_id = r.id";
        }

        @Override
        public CustomerClassData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return CustomerClassData.builder().id(rs.getLong("id")).classCode(rs.getString("classCode"))
                    .className(rs.getString("className")).description(rs.getString("description"))
                    .legalFormId(JdbcSupport.getInteger(rs, "legalFormId")).customerType(rs.getString("customerType"))
                    .riskLevel(rs.getString("riskLevel")).kycLevel(rs.getString("kycLevel"))
                    .loanEligible(fromYn(rs.getString("loanEligibleFlag"))).restrictionId(JdbcSupport.getLong(rs, "restrictionId"))
                    .restrictionCode(rs.getString("restrictionCode")).restrictionName(rs.getString("restrictionName"))
                    .overdraftAllowed(fromYn(rs.getString("overdraftAllowedFlag")))
                    .enhancedDueDiligence(fromYn(rs.getString("enhancedDueDiligenceFlag")))
                    .reclassificationAllowed(fromYn(rs.getString("reclassificationAllowedFlag")))
                    .minAge(JdbcSupport.getInteger(rs, "minAge")).maxAge(JdbcSupport.getInteger(rs, "maxAge"))
                    .enforceCustPhoto(fromYn(rs.getString("enforceCustPhotoFlag")))
                    .enforceCustSignature(fromYn(rs.getString("enforceCustSignatureFlag")))
                    .enforceCustDocument(fromYn(rs.getString("enforceCustDocumentFlag")))
                    .autoCreateAccount(fromYn(rs.getString("autoCreateAccountFlag"))).status(rs.getString("status")).build();
        }

        private static Boolean fromYn(final String value) {
            return "Y".equalsIgnoreCase(value);
        }
    }

    @Override
    public List<CustomerClassData> retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final CustomerClassMapper mapper = new CustomerClassMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " ORDER BY cc.class_name", mapper);
    }

    @Override
    public CustomerClassData retrieveOne(final Long customerClassId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final CustomerClassMapper mapper = new CustomerClassMapper();
        final List<CustomerClassData> results = this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE cc.id = ?",
                mapper, customerClassId);
        if (results.isEmpty()) {
            throw new CustomerClassNotFoundException(customerClassId);
        }
        return results.get(0);
    }

    @Override
    public CustomerClassTemplateData retrieveTemplate() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final List<CustomerClassTemplateData.RestrictionOption> restrictionOptions = this.jdbcTemplate.query(
                "SELECT id, restriction_code, restriction_name FROM m_restriction WHERE status = 'ACTIVE' ORDER BY restriction_name",
                (rs, rowNum) -> new CustomerClassTemplateData.RestrictionOption(rs.getLong("id"), rs.getString("restriction_code"),
                        rs.getString("restriction_name")));
        return CustomerClassTemplateData.builder()
                .legalFormOptions(List.of(new CustomerClassTemplateData.LegalFormOption(LegalForm.PERSON.getValue(), LegalForm.PERSON.getLabel()),
                        new CustomerClassTemplateData.LegalFormOption(LegalForm.ENTITY.getValue(), LegalForm.ENTITY.getLabel())))
                .customerTypeOptions(List.of("GROUP", "JOINT"))
                .riskLevelOptions(List.of("LOW", "MEDIUM", "HIGH")).kycLevelOptions(List.of("BASIC", "STANDARD", "ENHANCED"))
                .statusOptions(List.of("ACTIVE", "INACTIVE")).restrictionOptions(restrictionOptions).build();
    }

    @Override
    public List<CustomerClassData> retrieveActiveForClientDropdown() {
        this.context.authenticatedUser();
        final CustomerClassMapper mapper = new CustomerClassMapper();
        return this.jdbcTemplate.query("SELECT " + mapper.schema() + " WHERE cc.status = 'ACTIVE' ORDER BY cc.class_name", mapper);
    }
}
