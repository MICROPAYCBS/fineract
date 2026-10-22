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
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.treasury.data.TreasuryDealData;
import org.apache.fineract.treasury.data.TreasuryMaturityData;
import org.apache.fineract.treasury.domain.TreasuryDealAccrualRepository;
import org.apache.fineract.treasury.domain.TreasuryDealCashflowRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryDealReadService {

    private static final String DEAL_SQL = """
            SELECT d.*, t.code AS type_code, t.instrument_kind, t.direction, c.short_code AS counterparty_code, a.code AS account_code
            FROM m_treasury_deal d
            JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            JOIN m_treasury_counterparty c ON c.id = d.counterparty_id
            JOIN m_treasury_account a ON a.id = d.settlement_account_id
            """;

    /** Cash flows of deals that are neither rejected nor cancelled. */
    private static final String MATURITY_SQL = """
            SELECT f.id AS cashflow_id, f.status AS cashflow_status, f.settled_amount, f.due_date, f.flow_type, f.amount, d.id AS deal_id, d.currency_code, d.status, t.code AS type_code, t.instrument_kind,
                   t.direction, c.short_code AS counterparty_code
            FROM m_treasury_deal_cashflow f
            JOIN m_treasury_deal d ON d.id = f.deal_id
            JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            JOIN m_treasury_counterparty c ON c.id = d.counterparty_id
            WHERE f.due_date >= :fromDate AND f.due_date <= :toDate AND d.status NOT IN ('REJECTED', 'CANCELLED')
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TreasuryDealCashflowRepository cashflowRepository;
    private final TreasuryDealEventRepository eventRepository;
    private final TreasuryDealAccrualRepository accrualRepository;

    public record DealFilter(String status, Long dealTypeId, Long counterpartyId, LocalDate maturityFrom, LocalDate maturityTo) {
    }

    public List<TreasuryDealData> retrieveDeals(final DealFilter filter) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource();
        if (StringUtils.isNotBlank(filter.status())) {
            conditions.add("d.status = :status");
            params.addValue("status", filter.status().trim().toUpperCase());
        }
        if (filter.dealTypeId() != null) {
            conditions.add("d.deal_type_id = :dealTypeId");
            params.addValue("dealTypeId", filter.dealTypeId());
        }
        if (filter.counterpartyId() != null) {
            conditions.add("d.counterparty_id = :counterpartyId");
            params.addValue("counterpartyId", filter.counterpartyId());
        }
        if (filter.maturityFrom() != null) {
            conditions.add("d.maturity_date >= :maturityFrom");
            params.addValue("maturityFrom", filter.maturityFrom());
        }
        if (filter.maturityTo() != null) {
            conditions.add("d.maturity_date <= :maturityTo");
            params.addValue("maturityTo", filter.maturityTo());
        }
        final String where = conditions.isEmpty() ? "" : " WHERE " + String.join(" AND ", conditions);
        return this.jdbcTemplate.query(DEAL_SQL + where + " ORDER BY d.maturity_date, d.id", params,
                (rs, rowNum) -> map(rs, List.of(), List.of(), List.of()));
    }

    public TreasuryDealData retrieveDeal(final Long dealId) {
        final List<TreasuryDealData.Cashflow> cashflows = this.cashflowRepository.findByDealIdOrderBySequenceNumberAsc(dealId).stream()
                .map(f -> new TreasuryDealData.Cashflow(f.getId(), f.getSequenceNumber(), f.getFlowType().name(), f.getPeriodStart(),
                        f.getPeriodEnd(), f.getDueDate(), f.getAmount(), f.getPrincipalOutstanding(), f.getStatus().name(),
                        f.getSettledAmount(), f.getSettledOnDate(), f.getTransactionId()))
                .toList();
        final List<TreasuryDealData.Event> events = this.eventRepository.findByDealIdOrderByIdAsc(dealId).stream()
                .map(e -> new TreasuryDealData.Event(e.getEventType().name(), e.getBusinessDate(), e.getUserId(), e.getNote(),
                        e.getTransactionId()))
                .toList();
        final List<TreasuryDealData.Accrual> accruals = this.accrualRepository
                .findByDealIdOrderBySequenceNumberAsc(dealId).stream().map(a -> new TreasuryDealData.Accrual(a.getSequenceNumber(),
                        a.getFromDate(), a.getToDate(), a.getInterest(), a.getAmortisation(), a.getFeeAmortisation(), a.getTransactionId()))
                .toList();
        final List<TreasuryDealData> rows = this.jdbcTemplate.query(DEAL_SQL + " WHERE d.id = :id", new MapSqlParameterSource("id", dealId),
                (rs, rowNum) -> map(rs, cashflows, events, accruals));
        if (rows.isEmpty()) {
            throw new TreasuryWorkflowException("deal", "not.found", "Treasury deal " + dealId + " does not exist", dealId);
        }
        return rows.getFirst();
    }

    /** Cash flows due between the dates, earliest first. */
    public List<TreasuryMaturityData> retrieveMaturityCalendar(final LocalDate from, final LocalDate to) {
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("fromDate", from).addValue("toDate", to);
        return this.jdbcTemplate.query(MATURITY_SQL + " ORDER BY f.due_date, d.id, f.sequence_number", params,
                (rs, rowNum) -> new TreasuryMaturityData(JdbcSupport.getLocalDate(rs, "due_date"), rs.getLong("deal_id"),
                        rs.getString("type_code"), rs.getString("instrument_kind"), rs.getString("direction"),
                        rs.getString("counterparty_code"), rs.getString("currency_code"), rs.getString("flow_type"),
                        rs.getBigDecimal("amount"), rs.getString("status"), rs.getLong("cashflow_id"), rs.getString("cashflow_status"),
                        rs.getBigDecimal("settled_amount")));
    }

    private static TreasuryDealData map(final ResultSet rs, final List<TreasuryDealData.Cashflow> cashflows,
            final List<TreasuryDealData.Event> events, final List<TreasuryDealData.Accrual> accruals) throws SQLException {
        final TreasuryDealData.Lifecycle lifecycle = new TreasuryDealData.Lifecycle(JdbcSupport.getLocalDate(rs, "accrued_to_date"),
                rs.getBigDecimal("interest_accrued"), rs.getBigDecimal("amortisation_accrued"), rs.getBigDecimal("fee_deferred"),
                rs.getBigDecimal("fee_amortised"), rs.getBigDecimal("interest_settled"), rs.getBigDecimal("principal_settled"),
                JdbcSupport.getLocalDate(rs, "matured_on_date"), JdbcSupport.getLocalDate(rs, "terminated_on_date"),
                rs.getBigDecimal("termination_proceeds"), rs.getBigDecimal("break_cost"), rs.getBigDecimal("realised_result"),
                rs.getBoolean("rollover_pre_approved"), JdbcSupport.getLong(rs, "rolled_from_deal_id"),
                JdbcSupport.getLong(rs, "rolled_to_deal_id"));
        return new TreasuryDealData(rs.getLong("id"), rs.getLong("deal_type_id"), rs.getString("type_code"),
                rs.getString("instrument_kind"), rs.getString("direction"), rs.getLong("counterparty_id"),
                rs.getString("counterparty_code"), rs.getLong("settlement_account_id"), rs.getString("account_code"),
                rs.getLong("office_id"), rs.getString("currency_code"), rs.getString("external_reference"), rs.getString("isin"),
                JdbcSupport.getLocalDate(rs, "trade_date"), JdbcSupport.getLocalDate(rs, "value_date"),
                JdbcSupport.getLocalDate(rs, "maturity_date"), JdbcSupport.getLocalDate(rs, "issue_date"), rs.getBigDecimal("amount"),
                rs.getBigDecimal("rate"), rs.getBigDecimal("clean_price"), rs.getString("day_count"), rs.getInt("frequency"),
                rs.getBoolean("amortising"), rs.getBigDecimal("fees"), rs.getString("classification"), rs.getBigDecimal("consideration"),
                rs.getBigDecimal("accrued_interest_purchased"), rs.getBigDecimal("premium"), rs.getBigDecimal("discount"),
                rs.getBigDecimal("exposure_amount"), rs.getBoolean("limit_exceeded"), rs.getString("status"), rs.getString("notes"),
                JdbcSupport.getLong(rs, "captured_by"), JdbcSupport.getLong(rs, "approved_by"),
                JdbcSupport.getLocalDate(rs, "approved_on_date"), JdbcSupport.getLong(rs, "confirmed_by"),
                JdbcSupport.getLocalDate(rs, "confirmed_on_date"), rs.getString("confirmation_reference"),
                JdbcSupport.getLong(rs, "settled_by"), JdbcSupport.getLocalDate(rs, "settled_on_date"),
                rs.getString("settlement_transaction_id"), JdbcSupport.getLong(rs, "closed_by"),
                JdbcSupport.getLocalDate(rs, "closed_on_date"), rs.getString("close_reason"), lifecycle, cashflows, events, accruals);
    }
}
