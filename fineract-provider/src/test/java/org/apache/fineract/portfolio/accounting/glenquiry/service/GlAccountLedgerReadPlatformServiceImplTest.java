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
package org.apache.fineract.portfolio.accounting.glenquiry.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.sql.Date;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.mockito.stubbing.Answer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlAccountLedgerReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private GlAccountLedgerDataValidator dataValidator;

    @InjectMocks
    private GlAccountLedgerReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        doNothing().when(this.dataValidator).validate(any());
    }

    @Test
    void emptyPeriodWithNonZeroOpeningReturnsClosingEqualsOpeningAndNullLastUpdated() throws Exception {
        stubAccountOfficeAndDepartment();
        stubNoSnapshotWatermark();
        // Asset (classification 1): opening = -rawNet; rawNet from full JE delta only
        when(this.jdbcTemplate.queryForObject(ArgumentMatchers.contains("SUM(CASE WHEN je.type_enum"), eq(BigDecimal.class),
                any(Object[].class))).thenReturn(new BigDecimal("-1000.00"));
        when(this.jdbcTemplate.query(ArgumentMatchers.contains("ORDER BY je.entry_date ASC"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        final GlAccountLedgerData result = this.service.retrieveLedger(request());

        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getSummary().getOpeningBalance()).isEqualByComparingTo("1000.00");
        assertThat(result.getSummary().getClosingBalance()).isEqualByComparingTo("1000.00");
        assertThat(result.getSummary().getTotalDebit()).isEqualByComparingTo("0");
        assertThat(result.getSummary().getTotalCredit()).isEqualByComparingTo("0");
        assertThat(result.getSummary().getLastUpdated()).isNull();
    }

    @Test
    void emptyPeriodWithZeroOpeningStillReturnsSummary() throws Exception {
        stubAccountOfficeAndDepartment();
        stubNoSnapshotWatermark();
        when(this.jdbcTemplate.queryForObject(ArgumentMatchers.contains("SUM(CASE WHEN je.type_enum"), eq(BigDecimal.class),
                any(Object[].class))).thenReturn(BigDecimal.ZERO);
        when(this.jdbcTemplate.query(ArgumentMatchers.contains("ORDER BY je.entry_date ASC"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(Collections.emptyList());

        final GlAccountLedgerData result = this.service.retrieveLedger(request());

        assertThat(result.getEntries()).isEmpty();
        assertThat(result.getSummary().getOpeningBalance()).isEqualByComparingTo("0");
        assertThat(result.getSummary().getClosingBalance()).isEqualByComparingTo("0");
        assertThat(result.getSummary().getLastUpdated()).isNull();
    }

    @Test
    void periodWithActivityBuildsRunningBalancesNewestFirstAndLastUpdated() throws Exception {
        stubAccountOfficeAndDepartment();
        stubNoSnapshotWatermark();
        when(this.jdbcTemplate.queryForObject(ArgumentMatchers.contains("SUM(CASE WHEN je.type_enum"), eq(BigDecimal.class),
                any(Object[].class))).thenReturn(new BigDecimal("-1000.00"));

        final OffsetDateTime older = OffsetDateTime.of(2026, 7, 5, 10, 0, 0, 0, ZoneOffset.UTC);
        final OffsetDateTime newer = OffsetDateTime.of(2026, 7, 10, 14, 32, 1, 0, ZoneOffset.UTC);

        when(this.jdbcTemplate.query(ArgumentMatchers.contains("ORDER BY je.entry_date ASC"), any(RowMapper.class), any(Object[].class)))
                .thenAnswer((Answer<List<?>>) invocation -> {
                    @SuppressWarnings("unchecked")
                    final RowMapper<Object> mapper = invocation.getArgument(1);
                    return List.of( //
                            mapper.mapRow(periodLineRs(1L, LocalDate.of(2026, 7, 5), "T1", "Older", true, "100.00", "0", "100.00", older), 0),
                            mapper.mapRow(periodLineRs(2L, LocalDate.of(2026, 7, 10), "T2", "Newer", false, "0", "25.00", "-25.00", newer),
                                    1));
                });

        final GlAccountLedgerData result = this.service.retrieveLedger(request());

        assertThat(result.getSummary().getOpeningBalance()).isEqualByComparingTo("1000.00");
        assertThat(result.getEntries()).hasSize(2);
        assertThat(result.getEntries().get(0).getTransactionId()).isEqualTo("T2");
        assertThat(result.getEntries().get(0).getSource()).isEqualTo("System");
        assertThat(result.getEntries().get(0).getRunningBalance()).isEqualByComparingTo("1075.00");
        assertThat(result.getEntries().get(1).getTransactionId()).isEqualTo("T1");
        assertThat(result.getEntries().get(1).getSource()).isEqualTo("Manual");
        assertThat(result.getEntries().get(1).getRunningBalance()).isEqualByComparingTo("1100.00");
        assertThat(result.getSummary().getClosingBalance()).isEqualByComparingTo("1075.00");
        assertThat(result.getSummary().getTotalDebit()).isEqualByComparingTo("100.00");
        assertThat(result.getSummary().getTotalCredit()).isEqualByComparingTo("25.00");
        assertThat(result.getSummary().getLastUpdated()).isEqualTo(newer);
    }

    private void stubAccountOfficeAndDepartment() throws Exception {
        when(this.jdbcTemplate.queryForObject(ArgumentMatchers.contains("FROM acc_gl_account"), any(RowMapper.class), any()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    final RowMapper<Object> mapper = invocation.getArgument(1);
                    final ResultSet rs = mock(ResultSet.class);
                    when(rs.getLong("id")).thenReturn(15L);
                    when(rs.getString("gl_code")).thenReturn("100001");
                    when(rs.getString("name")).thenReturn("Cash on Hand");
                    when(rs.getInt("classification_enum")).thenReturn(1);
                    when(rs.wasNull()).thenReturn(false);
                    return mapper.mapRow(rs, 0);
                });
        when(this.jdbcTemplate.queryForObject(eq("SELECT name FROM m_office WHERE id = ?"), eq(String.class), any()))
                .thenReturn("Head Office");
        when(this.jdbcTemplate.queryForObject(ArgumentMatchers.contains("FROM m_department"), eq(String.class), any()))
                .thenReturn("Information Technology");
    }

    private void stubNoSnapshotWatermark() {
        when(this.jdbcTemplate.queryForObject(ArgumentMatchers.contains("MAX(s.snapshot_date)"), any(RowMapper.class), any(Object[].class)))
                .thenReturn(null);
    }

    private static ResultSet periodLineRs(final long id, final LocalDate entryDate, final String transactionId, final String description,
            final boolean manualEntry, final String debit, final String credit, final String movement, final OffsetDateTime createdOnUtc)
            throws Exception {
        final ResultSet rs = mock(ResultSet.class);
        when(rs.getLong("id")).thenReturn(id);
        when(rs.getDate("entryDate")).thenReturn(Date.valueOf(entryDate));
        when(rs.getString("transactionId")).thenReturn(transactionId);
        when(rs.getString("description")).thenReturn(description);
        when(rs.getBoolean("manualEntry")).thenReturn(manualEntry);
        when(rs.getBigDecimal("debit")).thenReturn(new BigDecimal(debit));
        when(rs.getBigDecimal("credit")).thenReturn(new BigDecimal(credit));
        when(rs.getBigDecimal("movement")).thenReturn(new BigDecimal(movement));
        when(rs.getTimestamp("createdOnUtc")).thenReturn(Timestamp.from(createdOnUtc.toInstant()));
        when(rs.wasNull()).thenReturn(false);
        return rs;
    }

    private static GlAccountLedgerRequest request() {
        return GlAccountLedgerRequest.builder() //
                .glAccountId(15L) //
                .startDate(LocalDate.of(2026, 7, 1)) //
                .endDate(LocalDate.of(2026, 7, 16)) //
                .officeId(1L) //
                .currencyCode("UGX") //
                .departmentId(2L) //
                .build();
    }
}
