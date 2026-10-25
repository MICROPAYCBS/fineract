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

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.core.domain.JdbcSupport;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryDealData;
import org.apache.fineract.treasury.data.TreasuryMaturityData;
import org.apache.fineract.treasury.deal.DealClassification;
import org.apache.fineract.treasury.deal.DealStatus;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;
import org.apache.fineract.treasury.domain.TreasuryDealAccrualRepository;
import org.apache.fineract.treasury.domain.TreasuryDealCashflowRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEclRepository;
import org.apache.fineract.treasury.domain.TreasuryDealEventRepository;
import org.apache.fineract.treasury.domain.TreasuryDealValuationRepository;
import org.apache.fineract.treasury.domain.TreasuryPledgeRepository;
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
            SELECT d.*, t.code AS type_code, t.instrument_kind, t.direction, c.short_code AS counterparty_code, a.code AS account_code,
                   (SELECT COALESCE(SUM(p.amount), 0) FROM m_treasury_pledge p WHERE p.deal_id = d.id AND p.start_date <= :asOf
                    AND (p.released_on_date IS NULL OR p.released_on_date > :asOf)) AS pledged
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
    private final TreasuryPledgeRepository pledgeRepository;
    private final TreasuryDealValuationRepository valuationRepository;
    private final TreasuryDealEclRepository eclRepository;
    private final TreasurySettingService settingService;

    public record DealFilter(String status, Long dealTypeId, Long counterpartyId, LocalDate maturityFrom, LocalDate maturityTo) {
    }

    public List<TreasuryDealData> retrieveDeals(final DealFilter filter) {
        final List<String> conditions = new ArrayList<>();
        final MapSqlParameterSource params = new MapSqlParameterSource("asOf", DateUtils.getBusinessLocalDate());
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
        final ToIntFunction<String> windows = rolloverWindowDays();
        return this.jdbcTemplate.query(DEAL_SQL + where + " ORDER BY d.maturity_date, d.id", params,
                (rs, rowNum) -> map(rs, new Details(List.of(), List.of(), List.of(), List.of(), List.of(), List.of()), windows));
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
        final List<TreasuryDealData.Pledge> pledges = this.pledgeRepository.findByDealIdOrderByIdAsc(dealId).stream()
                .map(p -> new TreasuryDealData.Pledge(p.getId(), p.getPledgeeCounterpartyId(), p.getPurpose().name(), p.getLinkedDealId(),
                        p.getAmount(), p.getStartDate(), p.getExpectedEndDate(), p.getStatus().name(), p.getReason(), p.getPledgedBy(),
                        p.getReleasedBy(), p.getReleasedOnDate(), p.getReleaseReason()))
                .toList();
        final List<TreasuryDealData.Valuation> valuations = this.valuationRepository.findByDealIdOrderBySequenceNumberAsc(dealId).stream()
                .map(v -> new TreasuryDealData.Valuation(v.getSequenceNumber(), v.getValuationDate(), v.getPriceDate(), v.getCleanPrice(),
                        v.getYieldRate(), v.getFairValue(), v.getAmortisedCost(), v.getAdjustment(), v.getAdjustmentChange(),
                        v.getTransactionId()))
                .toList();
        final List<TreasuryDealData.Ecl> ecl = this.eclRepository.findByDealIdOrderBySequenceNumberAsc(dealId).stream()
                .map(e -> new TreasuryDealData.Ecl(e.getSequenceNumber(), e.getCalculationDate(), e.getStage(), e.getTriggerType(),
                        e.getExposure(), e.getPd(), e.getLgd(), e.getBasis(), e.getEcl(), e.getEclChange(), e.getTransactionId()))
                .toList();
        final Details details = new Details(cashflows, events, accruals, pledges, valuations, ecl);
        final List<TreasuryDealData> rows = this.jdbcTemplate.query(DEAL_SQL + " WHERE d.id = :id",
                new MapSqlParameterSource("id", dealId).addValue("asOf", DateUtils.getBusinessLocalDate()),
                (rs, rowNum) -> map(rs, details, rolloverWindowDays()));
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

    private record Details(List<TreasuryDealData.Cashflow> cashflows, List<TreasuryDealData.Event> events,
            List<TreasuryDealData.Accrual> accruals, List<TreasuryDealData.Pledge> pledges, List<TreasuryDealData.Valuation> valuations,
            List<TreasuryDealData.Ecl> ecl) {
    }

    private static TreasuryDealData.Holding holding(final ResultSet rs) throws SQLException {
        final InstrumentKind kind = InstrumentKind.valueOf(rs.getString("instrument_kind"));
        final DealClassification classification = DealClassification.valueOf(rs.getString("classification"));
        final BigDecimal adjustment = rs.getBigDecimal("fv_adjustment");
        final BigDecimal allowance = rs.getBigDecimal("ecl_allowance");
        final BigDecimal pledged = rs.getBigDecimal("pledged");
        final LocalDate asOf = DateUtils.getBusinessLocalDate();
        final boolean held = kind.isAsset() && DealStatus.SETTLED.name().equals(rs.getString("status"))
                && !asOf.isBefore(JdbcSupport.getLocalDate(rs, "value_date"))
                && asOf.isBefore(JdbcSupport.getLocalDate(rs, "maturity_date"));
        final BigDecimal unencumbered = held
                ? rs.getBigDecimal("amount").subtract(rs.getBigDecimal("principal_settled")).subtract(pledged).max(BigDecimal.ZERO)
                : BigDecimal.ZERO;
        final int stage = rs.getInt("ecl_stage");
        return new TreasuryDealData.Holding(TreasuryDealTypeService.revalued(kind, classification), adjustment,
                rs.getBigDecimal("fv_last_value"), JdbcSupport.getLocalDate(rs, "fv_last_valuation_date"),
                JdbcSupport.getLocalDate(rs, "fv_last_price_date"),
                classification == DealClassification.FVOCI ? adjustment.add(allowance) : BigDecimal.ZERO, stage,
                rs.getString("ecl_stage_reason"), JdbcSupport.getLocalDate(rs, "ecl_stage_date"), rs.getBigDecimal("ecl_manual_pd"),
                rs.getBigDecimal("ecl_manual_lgd"), rs.getBigDecimal("ecl_manual_amount"), rs.getString("ecl_manual_reason"), allowance,
                JdbcSupport.getLocalDate(rs, "ecl_calculated_on_date"), stage == 3, pledged, unencumbered);
    }

    /** The roll-over window of each currency's settings, looked up once per currency. */
    private ToIntFunction<String> rolloverWindowDays() {
        final Map<String, Integer> days = new HashMap<>();
        return currency -> days.computeIfAbsent(currency, c -> this.settingService.settingsFor(c).getRolloverWindowDays());
    }

    /**
     * The dates a settled deal can be rolled over: from the currency's roll-over window before maturity to maturity.
     * Null when it cannot be (not settled, already rolled, or a bill or bond, which is bought again as a new deal).
     */
    private static LocalDate[] rolloverWindow(final ResultSet rs, final ToIntFunction<String> windowDays) throws SQLException {
        final InstrumentKind kind = InstrumentKind.valueOf(rs.getString("instrument_kind"));
        final LocalDate maturity = JdbcSupport.getLocalDate(rs, "maturity_date");
        if (kind.interestMethod() != InterestMethod.SIMPLE || !DealStatus.SETTLED.name().equals(rs.getString("status"))
                || JdbcSupport.getLong(rs, "rolled_to_deal_id") != null || maturity == null) {
            return new LocalDate[] { null, null };
        }
        return new LocalDate[] { maturity.minusDays(windowDays.applyAsInt(rs.getString("currency_code"))), maturity };
    }

    private static TreasuryDealData map(final ResultSet rs, final Details details, final ToIntFunction<String> windowDays)
            throws SQLException {
        final LocalDate[] window = rolloverWindow(rs, windowDays);
        final TreasuryDealData.Lifecycle lifecycle = new TreasuryDealData.Lifecycle(JdbcSupport.getLocalDate(rs, "accrued_to_date"),
                rs.getBigDecimal("interest_accrued"), rs.getBigDecimal("amortisation_accrued"), rs.getBigDecimal("fee_deferred"),
                rs.getBigDecimal("fee_amortised"), rs.getBigDecimal("interest_settled"), rs.getBigDecimal("principal_settled"),
                JdbcSupport.getLocalDate(rs, "matured_on_date"), JdbcSupport.getLocalDate(rs, "terminated_on_date"),
                rs.getBigDecimal("termination_proceeds"), rs.getBigDecimal("break_cost"), rs.getBigDecimal("realised_result"),
                rs.getBoolean("rollover_pre_approved"), JdbcSupport.getLong(rs, "rolled_from_deal_id"),
                JdbcSupport.getLong(rs, "rolled_to_deal_id"), window[0], window[1]);
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
                JdbcSupport.getLocalDate(rs, "closed_on_date"), rs.getString("close_reason"), lifecycle, holding(rs), details.cashflows(),
                details.events(), details.accruals(), details.pledges(), details.valuations(), details.ecl());
    }
}
