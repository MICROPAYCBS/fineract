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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryTransferData;
import org.apache.fineract.treasury.domain.TreasuryTransferStatus;
import org.apache.fineract.treasury.exception.TreasuryTransferNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryTransferReadPlatformService {

    private static final String TRANSFER_SQL = """
            SELECT tr.*, fa.code AS from_code, fa.office_id AS from_office_id, ta.code AS to_code, ta.office_id AS to_office_id
            FROM m_treasury_transfer tr
            JOIN m_treasury_account fa ON fa.id = tr.from_account_id
            JOIN m_treasury_account ta ON ta.id = tr.to_account_id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record TransferFilter(String status, Long accountId, Long officeId, LocalDate fromDate, LocalDate toDate) {
    }

    public List<TreasuryTransferData> retrieveTransfers(final TransferFilter filter) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.isNotBlank(filter.status())) {
            conditions.add("tr.status = :status");
            params.addValue("status", filter.status().trim().toUpperCase());
        }
        if (filter.accountId() != null) {
            conditions.add("(tr.from_account_id = :accountId OR tr.to_account_id = :accountId)");
            params.addValue("accountId", filter.accountId());
        }
        if (filter.officeId() != null) {
            conditions.add("(fa.office_id = :officeId OR ta.office_id = :officeId)");
            params.addValue("officeId", filter.officeId());
        }
        if (filter.fromDate() != null) {
            conditions.add("tr.transfer_date >= :fromDate");
            params.addValue("fromDate", filter.fromDate());
        }
        if (filter.toDate() != null) {
            conditions.add("tr.transfer_date <= :toDate");
            params.addValue("toDate", filter.toDate());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        return this.jdbcTemplate.query(TRANSFER_SQL + where + " ORDER BY tr.transfer_date DESC, tr.id DESC", params,
                (rs, rowNum) -> map(rs, businessDate));
    }

    public TreasuryTransferData retrieveTransfer(final Long transferId) {
        final LocalDate businessDate = DateUtils.getBusinessLocalDate();
        final List<TreasuryTransferData> rows = this.jdbcTemplate.query(TRANSFER_SQL + " WHERE tr.id = :id",
                new MapSqlParameterSource("id", transferId), (rs, rowNum) -> map(rs, businessDate));
        if (rows.isEmpty()) {
            throw new TreasuryTransferNotFoundException(transferId);
        }
        return rows.getFirst();
    }

    /** Sent and not yet received, oldest first. */
    public List<TreasuryTransferData> retrieveInTransit(final Long officeId) {
        final List<TreasuryTransferData> transfers = retrieveTransfers(
                new TransferFilter(TreasuryTransferStatus.SENT.name(), null, officeId, null, null));
        return transfers.stream().sorted((a, b) -> Long.compare(b.daysInTransit(), a.daysInTransit())).toList();
    }

    private static TreasuryTransferData map(final ResultSet rs, final LocalDate businessDate) throws SQLException {
        final String status = rs.getString("status");
        final LocalDate sentOn = JdbcSupport.getLocalDate(rs, "sent_on_date");
        final Long daysInTransit = TreasuryTransferStatus.SENT.name().equals(status) && sentOn != null
                ? ChronoUnit.DAYS.between(sentOn, businessDate)
                : null;
        return new TreasuryTransferData(rs.getLong("id"), rs.getLong("from_account_id"), rs.getString("from_code"),
                rs.getLong("from_office_id"), rs.getLong("to_account_id"), rs.getString("to_code"), rs.getLong("to_office_id"),
                rs.getBigDecimal("amount"), rs.getString("currency_code"), JdbcSupport.getLocalDate(rs, "transfer_date"),
                rs.getString("reference"), rs.getString("description"), status, JdbcSupport.getLong(rs, "requested_by"),
                JdbcSupport.getLong(rs, "approved_by"), JdbcSupport.getLocalDate(rs, "approved_on_date"),
                JdbcSupport.getLong(rs, "sent_by"), sentOn, rs.getString("send_transaction_id"), JdbcSupport.getLong(rs, "received_by"),
                JdbcSupport.getLocalDate(rs, "received_on_date"), rs.getString("receive_transaction_id"),
                JdbcSupport.getLong(rs, "cancelled_by"), JdbcSupport.getLocalDate(rs, "cancelled_on_date"), rs.getString("note"),
                daysInTransit);
    }
}
