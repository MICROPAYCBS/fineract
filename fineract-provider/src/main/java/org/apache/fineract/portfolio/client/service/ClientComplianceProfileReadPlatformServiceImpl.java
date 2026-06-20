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

package org.apache.fineract.portfolio.client.service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientComplianceProfileData;
import org.apache.fineract.portfolio.client.data.ClientOtherBankAccountData;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ClientComplianceProfileReadPlatformServiceImpl implements ClientComplianceProfileReadPlatformService {

    private final PlatformSecurityContext context;
    private final JdbcTemplate jdbcTemplate;

    private static final class ClientComplianceProfileMapper implements RowMapper<ClientComplianceProfileData> {

        public String schema() {
            return " ccp.id AS id, ccp.client_id AS clientId,"
                    + " ccp.has_other_bank_accounts AS hasOtherBankAccountsFlag,"
                    + " ccp.is_pep AS isPepFlag, ccp.pep_position AS pepPosition,"
                    + " ccp.pep_relative_name AS pepRelativeName,"
                    + " ccp.us_citizen_or_resident AS usCitizenOrResidentFlag,"
                    + " ccp.fatca_registered AS fatcaRegisteredFlag,"
                    + " ccp.fatca_registration_no AS fatcaRegistrationNo,"
                    + " ccp.dpf_alternative_bank_name AS dpfAlternativeBankName,"
                    + " ccp.dpf_alternative_account_number AS dpfAlternativeAccountNumber"
                    + " FROM m_client_compliance_profile ccp";
        }

        @Override
        public ClientComplianceProfileData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return ClientComplianceProfileData.builder().id(rs.getLong("id")).clientId(rs.getLong("clientId"))
                    .hasOtherBankAccounts("Y".equalsIgnoreCase(rs.getString("hasOtherBankAccountsFlag")))
                    .isPep("Y".equalsIgnoreCase(rs.getString("isPepFlag"))).pepPosition(rs.getString("pepPosition"))
                    .pepRelativeName(rs.getString("pepRelativeName"))
                    .usCitizenOrResident("Y".equalsIgnoreCase(rs.getString("usCitizenOrResidentFlag")))
                    .fatcaRegistered("Y".equalsIgnoreCase(rs.getString("fatcaRegisteredFlag")))
                    .fatcaRegistrationNo(rs.getString("fatcaRegistrationNo"))
                    .dpfAlternativeBankName(rs.getString("dpfAlternativeBankName"))
                    .dpfAlternativeAccountNumber(rs.getString("dpfAlternativeAccountNumber")).build();
        }
    }

    private static final class ClientOtherBankAccountMapper implements RowMapper<ClientOtherBankAccountData> {

        public String schema() {
            return " oba.id AS id, oba.client_id AS clientId, oba.bank_name AS bankName,"
                    + " oba.branch_name AS branchName, oba.account_number AS accountNumber, oba.display_order AS displayOrder"
                    + " FROM m_client_other_bank_account oba";
        }

        @Override
        public ClientOtherBankAccountData mapRow(final ResultSet rs, @SuppressWarnings("unused") final int rowNum) throws SQLException {
            return ClientOtherBankAccountData.builder().id(rs.getLong("id")).clientId(rs.getLong("clientId"))
                    .bankName(rs.getString("bankName")).branchName(rs.getString("branchName"))
                    .accountNumber(rs.getString("accountNumber")).displayOrder(JdbcSupport.getInteger(rs, "displayOrder")).build();
        }
    }

    @Override
    public ClientComplianceProfileData getClientComplianceProfile(final long clientId) {
        this.context.authenticatedUser();
        final ClientComplianceProfileMapper profileMapper = new ClientComplianceProfileMapper();
        final ClientOtherBankAccountMapper bankAccountMapper = new ClientOtherBankAccountMapper();

        ClientComplianceProfileData profile;
        try {
            final String profileSql = "SELECT " + profileMapper.schema() + " WHERE ccp.client_id = ?";
            profile = this.jdbcTemplate.queryForObject(profileSql, profileMapper, clientId);
        } catch (EmptyResultDataAccessException e) {
            return ClientComplianceProfileData.builder().clientId(clientId).otherBankAccounts(Collections.emptyList()).build();
        }

        final String bankAccountsSql = "SELECT " + bankAccountMapper.schema() + " WHERE oba.client_id = ? ORDER BY oba.display_order";
        final List<ClientOtherBankAccountData> otherBankAccounts = this.jdbcTemplate.query(bankAccountsSql, bankAccountMapper, clientId);

        return ClientComplianceProfileData.builder().id(profile.getId()).clientId(profile.getClientId())
                .hasOtherBankAccounts(profile.getHasOtherBankAccounts()).isPep(profile.getIsPep())
                .pepPosition(profile.getPepPosition()).pepRelativeName(profile.getPepRelativeName())
                .usCitizenOrResident(profile.getUsCitizenOrResident()).fatcaRegistered(profile.getFatcaRegistered())
                .fatcaRegistrationNo(profile.getFatcaRegistrationNo()).dpfAlternativeBankName(profile.getDpfAlternativeBankName())
                .dpfAlternativeAccountNumber(profile.getDpfAlternativeAccountNumber()).otherBankAccounts(otherBankAccounts).build();
    }
}
