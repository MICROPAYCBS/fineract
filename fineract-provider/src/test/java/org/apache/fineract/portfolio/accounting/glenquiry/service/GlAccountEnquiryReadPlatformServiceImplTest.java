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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GlAccountEnquiryReadPlatformServiceImplTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private GlAccountEnquiryDataValidator dataValidator;

    @InjectMocks
    private GlAccountEnquiryReadPlatformServiceImpl service;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "Asia/Kampala", null));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 7, 19))));
        doNothing().when(this.dataValidator).validate(any());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void enquireValidatesAndAppliesFiltersToHybridQuery() {
        when(this.jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any(), any()))
                .thenReturn(LocalDate.of(2026, 7, 18));
        when(this.jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());

        final GlAccountEnquiryRequest request = GlAccountEnquiryRequest.builder() //
                .glPrefix("1") //
                .ledgerNumber("0001") //
                .officeId(1L) //
                .departmentId(2L) //
                .currencyCode("UGX") //
                .disabled(false) //
                .build();

        final List<GlAccountEnquiryData> results = this.service.enquire(request);

        verify(this.dataValidator).validate(request);
        assertThat(results).isEmpty();

        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(this.jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        final String sql = sqlCaptor.getValue();
        assertThat(sql).contains("m_gl_balance_snapshot");
        assertThat(sql).contains("acc_gl_journal_entry");
        assertThat(sql).contains("m_department");
        assertThat(sql).contains("COALESCE(department_id, 0)");
        assertThat(sql).contains("aga.gl_code LIKE ?");
        assertThat(sql).contains("balances.office_id = ?");
        assertThat(sql).contains("balances.department_id = ?");
        assertThat(sql).contains("balances.currency_code = ?");
        assertThat(sql).contains("aga.disabled = ?");

        final Object[] params = paramsCaptor.getValue();
        assertThat(params).contains(Date.valueOf(LocalDate.of(2026, 7, 18)));
        assertThat(params).contains(Date.valueOf(LocalDate.of(2026, 7, 19)));
        assertThat(params).contains("DAILY");
        assertThat(params).contains("1%");
        assertThat(params).contains("%0001%");
        assertThat(params).contains(1L);
        assertThat(params).contains(2L);
        assertThat(params).contains("UGX");
        assertThat(params).contains(false);
    }

    @Test
    void enquireWithoutSnapshotUsesFullJournalDeltaThroughBusinessDate() {
        when(this.jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(null);
        when(this.jdbcTemplate.queryForObject(anyString(), any(RowMapper.class), any(), any())).thenReturn(null);
        when(this.jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class))).thenReturn(Collections.emptyList());

        this.service.enquire(GlAccountEnquiryRequest.builder().glPrefix("2").build());

        final ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        final ArgumentCaptor<Object[]> paramsCaptor = ArgumentCaptor.forClass(Object[].class);
        verify(this.jdbcTemplate).query(sqlCaptor.capture(), any(RowMapper.class), paramsCaptor.capture());

        assertThat(sqlCaptor.getValue()).contains("AND 1 = 0");
        assertThat(paramsCaptor.getValue()).contains(Date.valueOf(LocalDate.of(2026, 7, 19)));
        assertThat(paramsCaptor.getValue()).doesNotContain("DAILY", "MONTHLY");
    }
}
