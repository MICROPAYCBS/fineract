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
package org.apache.fineract.treasury.liquidity;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.deal.DealDirection;
import org.apache.fineract.treasury.deal.InstrumentKind;
import org.apache.fineract.treasury.deal.InterestMethod;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Treasury deals: the open cash flows (expected, or what is left of a partial one) of settled deals, and of approved
 * and confirmed deals with their settlement on the value date. Asset deals bring cash in, liability deals take it out.
 * {@code ROLLOVER} (per deal type or general) on placements, deposits and borrowings: that share of the principal due
 * at maturity is expected to roll, and is added back.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class DealLadderSource implements LadderSource {

    static final String PRINCIPAL = "DEAL_PRINCIPAL";
    static final String INTEREST = "DEAL_INTEREST";
    static final String SETTLEMENT = "DEAL_SETTLEMENT";

    private static final String FLOWS_SQL = """
            SELECT d.id AS deal_id, d.deal_type_id, t.code AS type_code, t.instrument_kind, d.maturity_date, f.flow_type, f.due_date,
                   f.amount - f.settled_amount AS amount
            FROM m_treasury_deal_cashflow f
            JOIN m_treasury_deal d ON d.id = f.deal_id
            JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            WHERE d.status IN ('APPROVED', 'CONFIRMED', 'SETTLED') AND f.status IN ('EXPECTED', 'PARTIAL') AND d.currency_code = :currency
              AND (:officeId IS NULL OR d.office_id = :officeId)
            ORDER BY d.id, f.sequence_number
            """;

    private static final String SETTLEMENT_SQL = """
            SELECT d.deal_type_id, t.code AS type_code, t.instrument_kind, d.value_date, d.consideration, d.amount
            FROM m_treasury_deal d
            JOIN m_treasury_deal_type t ON t.id = d.deal_type_id
            WHERE d.status IN ('APPROVED', 'CONFIRMED') AND d.currency_code = :currency AND (:officeId IS NULL OR d.office_id = :officeId)
            ORDER BY d.id
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public record DealFlow(Long dealTypeId, String typeCode, InstrumentKind kind, LocalDate maturityDate, boolean principal,
            LocalDate dueDate, BigDecimal amount) {
    }

    public record Settlement(Long dealTypeId, String typeCode, InstrumentKind kind, LocalDate valueDate, BigDecimal amount) {
    }

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.DEALS;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final MapSqlParameterSource params = new MapSqlParameterSource().addValue("currency", context.currencyCode()).addValue("officeId",
                context.officeId(), java.sql.Types.BIGINT);
        final List<DealFlow> flows = this.jdbcTemplate.query(FLOWS_SQL, params,
                (rs, i) -> new DealFlow(rs.getLong("deal_type_id"), rs.getString("type_code"),
                        InstrumentKind.valueOf(rs.getString("instrument_kind")), rs.getObject("maturity_date", LocalDate.class),
                        "PRINCIPAL".equals(rs.getString("flow_type")), rs.getObject("due_date", LocalDate.class),
                        rs.getBigDecimal("amount")));
        final List<Settlement> settlements = this.jdbcTemplate.query(SETTLEMENT_SQL, params, (rs, i) -> {
            final InstrumentKind kind = InstrumentKind.valueOf(rs.getString("instrument_kind"));
            return new Settlement(rs.getLong("deal_type_id"), rs.getString("type_code"), kind, rs.getObject("value_date", LocalDate.class),
                    kind.isAsset() ? rs.getBigDecimal("consideration") : rs.getBigDecimal("amount"));
        });
        return flows(context, flows, settlements);
    }

    public static List<LadderFlow> flows(final LadderContext context, final List<DealFlow> dealFlows, final List<Settlement> settlements) {
        final List<LadderFlow> flows = new ArrayList<>();
        for (final Settlement settlement : settlements) {
            if (settlement.amount() != null && settlement.amount().signum() != 0) {
                flows.add(LadderFlow.contractual(LadderSourceCode.DEALS, SETTLEMENT, settlement.dealTypeId(), settlement.typeCode(),
                        settlement.valueDate(), settlement.kind().isAsset() ? settlement.amount().negate() : settlement.amount()));
            }
        }
        for (final DealFlow flow : dealFlows) {
            if (flow.amount() == null || flow.amount().signum() <= 0) {
                continue;
            }
            final boolean asset = flow.kind().direction() == DealDirection.ASSET;
            final BigDecimal signed = asset ? flow.amount() : flow.amount().negate();
            final String line = flow.principal() ? PRINCIPAL : INTEREST;
            flows.add(LadderFlow.contractual(LadderSourceCode.DEALS, line, flow.dealTypeId(), flow.typeCode(), flow.dueDate(), signed));
            if (flow.principal() && flow.kind().interestMethod() == InterestMethod.SIMPLE && flow.dueDate().equals(flow.maturityDate())) {
                final Optional<BigDecimal> rollover = context.assumptions().rate(AssumptionType.ROLLOVER, LadderSourceCode.DEALS,
                        flow.dealTypeId(), null);
                if (rollover.isPresent() && rollover.get().signum() > 0) {
                    flows.add(LadderFlow.behavioural(LadderSourceCode.DEALS, line, flow.dealTypeId(), flow.typeCode(), flow.dueDate(),
                            context.percent(signed, rollover.get()).negate()));
                }
            }
        }
        return flows;
    }
}
