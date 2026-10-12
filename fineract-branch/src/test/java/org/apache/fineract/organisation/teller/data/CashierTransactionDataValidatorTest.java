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
package org.apache.fineract.organisation.teller.data;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.SearchParameters;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.organisation.staff.domain.Staff;
import org.apache.fineract.organisation.teller.exception.CashierInsufficientAmountException;
import org.apache.fineract.organisation.teller.service.TellerManagementReadPlatformService;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

@ExtendWith(MockitoExtension.class)
class CashierTransactionDataValidatorTest {

    @Mock
    private TellerManagementReadPlatformService tellerManagementReadPlatformService;

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @Mock
    private ConfigurationDomainService configurationDomainService;

    private CashierTransactionDataValidator validator;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        this.validator = new CashierTransactionDataValidator(this.tellerManagementReadPlatformService, this.jdbcTemplate,
                this.configurationDomainService);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void settleUsesNetCashAndRefusesAnAmountAboveTheTill() {
        when(this.configurationDomainService.isPreventCashierOverdrawEnabled()).thenReturn(true);
        when(this.tellerManagementReadPlatformService.retrieveCashierNetCash(1L, "USD")).thenReturn(new BigDecimal("50"));

        assertThrows(CashierInsufficientAmountException.class,
                () -> this.validator.validateSettleCashAndCashOutTransactions(1L, "USD", new BigDecimal("100")));
        assertDoesNotThrow(() -> this.validator.validateSettleCashAndCashOutTransactions(1L, "USD", new BigDecimal("50")));
        verify(this.tellerManagementReadPlatformService, never()).retrieveCashierTransactionsWithSummary(nullable(Long.class), anyBoolean(),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(String.class), nullable(SearchParameters.class));
    }

    @Test
    void cashOutRefusesAmountAboveTill() {
        when(this.configurationDomainService.isRequireCashierForCashTransactionsEnabled()).thenReturn(false);
        when(this.configurationDomainService.isPreventCashierOverdrawEnabled()).thenReturn(true);
        when(this.jdbcTemplate.queryForObject(anyString(), anyMap(), eq(Long.class))).thenReturn(9L);
        when(this.tellerManagementReadPlatformService.retrieveCashierNetCash(9L, "USD")).thenReturn(new BigDecimal("50"));

        assertThrows(CashierInsufficientAmountException.class,
                () -> this.validator.validateCashOut(userWithStaff(), cashPayment(), "USD", new BigDecimal("100")));
        assertDoesNotThrow(() -> this.validator.validateCashOut(userWithStaff(), cashPayment(), "USD", new BigDecimal("50")));
        verify(this.tellerManagementReadPlatformService, never()).retrieveCashierTransactionsWithSummary(nullable(Long.class), anyBoolean(),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(String.class), nullable(SearchParameters.class));
    }

    @Test
    void cashOutIgnoresNonCashPayments() {
        this.validator.validateCashOut(mock(AppUser.class), nonCashPayment(), "USD", new BigDecimal("100"));

        verify(this.configurationDomainService, never()).isPreventCashierOverdrawEnabled();
        verify(this.configurationDomainService, never()).isRequireCashierForCashTransactionsEnabled();
        verify(this.tellerManagementReadPlatformService, never()).retrieveCashierNetCash(any(), any());
        verify(this.jdbcTemplate, never()).queryForObject(anyString(), anyMap(), eq(Long.class));
    }

    @Test
    void cashOutSkipsNetCashWhenOverdrawIsOff() {
        when(this.configurationDomainService.isRequireCashierForCashTransactionsEnabled()).thenReturn(false);
        when(this.configurationDomainService.isPreventCashierOverdrawEnabled()).thenReturn(false);

        this.validator.validateCashOut(mock(AppUser.class), cashPayment(), "USD", new BigDecimal("100"));

        verify(this.tellerManagementReadPlatformService, never()).retrieveCashierNetCash(any(), any());
    }

    @Test
    void cashOutPassesWhenUserHasNoStaffAndCashierIsNotRequired() {
        when(this.configurationDomainService.isRequireCashierForCashTransactionsEnabled()).thenReturn(false);
        when(this.configurationDomainService.isPreventCashierOverdrawEnabled()).thenReturn(true);

        this.validator.validateCashOut(mock(AppUser.class), cashPayment(), "USD", new BigDecimal("100"));

        verify(this.tellerManagementReadPlatformService, never()).retrieveCashierNetCash(any(), any());
        verify(this.jdbcTemplate, never()).queryForObject(anyString(), anyMap(), eq(Long.class));
    }

    private static AppUser userWithStaff() {
        final Staff staff = mock(Staff.class);
        when(staff.getId()).thenReturn(4L);
        final AppUser user = mock(AppUser.class);
        when(user.getStaff()).thenReturn(staff);
        return user;
    }

    private static PaymentDetail cashPayment() {
        final PaymentType paymentType = new PaymentType();
        paymentType.setIsCashPayment(true);
        return PaymentDetail.instance(paymentType, null, null, null, null, null);
    }

    private static PaymentDetail nonCashPayment() {
        final PaymentType paymentType = new PaymentType();
        paymentType.setIsCashPayment(false);
        return PaymentDetail.instance(paymentType, null, null, null, null, null);
    }
}
