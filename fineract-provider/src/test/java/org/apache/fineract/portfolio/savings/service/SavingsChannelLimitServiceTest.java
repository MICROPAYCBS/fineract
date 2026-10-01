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
package org.apache.fineract.portfolio.savings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentType;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimit;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitConsumption;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitConsumptionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitUsage;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitUsageRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsChannelLimitDirection;
import org.apache.fineract.portfolio.savings.domain.SavingsChannelLimitPeriodType;
import org.apache.fineract.portfolio.savings.domain.SavingsProduct;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.SelfServiceUserClientMappingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SavingsChannelLimitServiceTest {

    private static final LocalDate TXN_DATE = LocalDate.of(2026, 1, 15);

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private SavingsAccountRepositoryWrapper savingsAccountRepository;
    @Mock
    private org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository productChannelRepository;
    @Mock
    private SavingsAccountChannelLimitRepository limitRepository;
    @Mock
    private SavingsAccountChannelLimitUsageRepository usageRepository;
    @Mock
    private SavingsAccountChannelLimitConsumptionRepository consumptionRepository;
    @Mock
    private SelfServiceUserClientMappingRepository selfServiceUserClientMappingRepository;
    @Mock
    private AppUser user;

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private SavingsChannelLimitService service;
    private SavingsAccount account;
    private SavingsProductPaymentChannel channel;

    @BeforeEach
    void setUp() {
        service = new SavingsChannelLimitService(context, configurationDomainService, savingsAccountRepository, productChannelRepository,
                limitRepository, usageRepository, consumptionRepository, selfServiceUserClientMappingRepository);
        account = org.mockito.Mockito.mock(SavingsAccount.class);
        final PaymentType paymentType = new PaymentType();
        paymentType.setId(3L);
        channel = new SavingsProductPaymentChannel().setPaymentType(paymentType).setActive(true);
        channel.setId(7L);
    }

    @Test
    void customerCapIsLowerThanCeiling() {
        stubUser(false);
        stubAccount();
        channel.setMaxDebitPerTxn(new BigDecimal("100"));
        final SavingsAccountChannelLimit customer = customerLimit();
        customer.setMaxPerTxn(new BigDecimal("40"));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.of(customer));

        assertTrue(fails(new BigDecimal("50"), false).contains("exceeds.per.transaction"));
        assertTrue(authorize(new BigDecimal("40"), false).isEmpty());
    }

    @Test
    void dailyAndMonthlyBucketsUseTransactionDate() {
        stubUser(false);
        stubAccount();
        channel.setMaxDebitPerDay(new BigDecimal("100"));
        channel.setMaxDebitPerMonth(new BigDecimal("150"));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        when(usageRepository.findForUpdate(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(usageRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final SavingsChannelLimitHold hold = authorize(new BigDecimal("80"), false);
        assertEquals(2, hold.usages().size());
        assertEquals(0, new BigDecimal("80").compareTo(hold.usages().get(0).getAmountUsed()));
        assertEquals(TXN_DATE, hold.usages().get(0).getPeriodStart());
        assertEquals(SavingsChannelLimitPeriodType.DAY, hold.usages().get(0).getPeriodType());
        assertEquals(LocalDate.of(2026, 1, 1), hold.usages().get(1).getPeriodStart());
        assertEquals(SavingsChannelLimitPeriodType.MONTH, hold.usages().get(1).getPeriodType());

        final SavingsAccountChannelLimitUsage day = hold.usages().get(0);
        final SavingsAccountChannelLimitUsage month = hold.usages().get(1);
        when(usageRepository.findForUpdate(10L, 7L, SavingsChannelLimitDirection.DEBIT, SavingsChannelLimitPeriodType.DAY, TXN_DATE))
                .thenReturn(Optional.of(day));
        when(usageRepository.findForUpdate(10L, 7L, SavingsChannelLimitDirection.DEBIT, SavingsChannelLimitPeriodType.MONTH,
                LocalDate.of(2026, 1, 1))).thenReturn(Optional.of(month));
        assertTrue(fails(new BigDecimal("30"), false).contains("exceeds.daily"));
        day.release(new BigDecimal("80"), 1);
        assertTrue(fails(new BigDecimal("80"), false).contains("exceeds.monthly"));
    }

    @Test
    void countCapRejectsWhenTheBucketIsFull() {
        stubUser(false);
        stubAccount();
        channel.setMaxDebitCountPerDay(1);
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        final SavingsAccountChannelLimitUsage day = SavingsAccountChannelLimitUsage.start(account, channel, SavingsChannelLimitDirection.DEBIT,
                SavingsChannelLimitPeriodType.DAY, TXN_DATE);
        day.add(new BigDecimal("10"), 1);
        when(usageRepository.findForUpdate(10L, 7L, SavingsChannelLimitDirection.DEBIT, SavingsChannelLimitPeriodType.DAY, TXN_DATE))
                .thenReturn(Optional.of(day));

        assertTrue(fails(new BigDecimal("1"), false).contains("exceeds.daily.count"));
    }

    @Test
    void creditLimitDoesNotApplyToDebitAndZeroBlocks() {
        stubUser(false);
        stubAccount();
        channel.setMaxCreditPerTxn(new BigDecimal("10"));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        assertTrue(authorize(new BigDecimal("1000"), false).isEmpty());

        channel.setMaxDebitPerTxn(BigDecimal.ZERO);
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        assertTrue(fails(new BigDecimal("1"), false).contains("exceeds.per.transaction"));
    }

    @Test
    void nullCeilingAndCustomerLimitAreUnlimited() {
        stubUser(false);
        stubAccount();
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        assertTrue(authorize(new BigDecimal("100000"), false).isEmpty());
        verify(usageRepository, never()).saveAndFlush(any());
    }

    @Test
    void transferChannelIsUsedWhenPaymentDetailIsMissing() {
        stubUser(false);
        stubAccount();
        channel.setAccountTransferChannel(true);
        channel.setMaxDebitPerTxn(new BigDecimal("25"));
        when(productChannelRepository.findByProductIdAndAccountTransferChannelTrueAndActiveTrue(1L)).thenReturn(List.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());

        final SavingsChannelLimitCommand command = new SavingsChannelLimitCommand(account, TXN_DATE, new BigDecimal("26"), null,
                SavingsChannelLimitDirection.DEBIT, true, true, false);
        assertTrue(fails(command).contains("exceeds.per.transaction"));
    }

    @Test
    void systemPostingsAreExempt() {
        final SavingsChannelLimitCommand interest = new SavingsChannelLimitCommand(account, TXN_DATE, new BigDecimal("10"), null,
                SavingsChannelLimitDirection.DEBIT, false, true, true);
        assertTrue(service.authorize(interest).isEmpty());
        final SavingsChannelLimitCommand system = new SavingsChannelLimitCommand(account, TXN_DATE, new BigDecimal("10"), null,
                SavingsChannelLimitDirection.CREDIT, false, false, false);
        assertTrue(service.authorize(system).isEmpty());
        verify(productChannelRepository, never()).findByProductIdAndPaymentTypeId(any(), any());
    }

    @Test
    void bypassPermissionSkipsTheCheck() {
        stubUser(true);
        final SavingsChannelLimitHold hold = authorize(new BigDecimal("10"), false);
        assertTrue(hold.isEmpty());
        verify(productChannelRepository, never()).findByProductIdAndPaymentTypeId(any(), any());
    }

    @Test
    void usageIsRecordedAndReleasedOnUndo() {
        stubUser(false);
        stubAccount();
        channel.setMaxDebitPerDay(new BigDecimal("100"));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        when(usageRepository.findForUpdate(any(), any(), any(), any(), any())).thenReturn(Optional.empty());
        when(usageRepository.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        final SavingsChannelLimitHold hold = authorize(new BigDecimal("40"), false);
        hold.usages().get(0).setId(9L);
        service.record(hold, 55L);

        final ArgumentCaptor<SavingsAccountChannelLimitConsumption> consumption = ArgumentCaptor
                .forClass(SavingsAccountChannelLimitConsumption.class);
        verify(consumptionRepository).save(consumption.capture());
        assertEquals(55L, consumption.getValue().getSavingsAccountTransactionId());
        assertEquals(0, new BigDecimal("40").compareTo(consumption.getValue().getAmount()));

        when(consumptionRepository.findBySavingsAccountTransactionId(55L)).thenReturn(List.of(consumption.getValue()));
        when(usageRepository.findOneForUpdate(9L)).thenReturn(Optional.of(hold.usages().get(0)));
        service.release(55L);
        assertEquals(0, BigDecimal.ZERO.compareTo(hold.usages().get(0).getAmountUsed()));
        assertEquals(0, hold.usages().get(0).getCountUsed());
        verify(consumptionRepository).deleteAll(List.of(consumption.getValue()));
    }

    @Test
    void selfServiceIncreaseWaitsAndStaffIncreaseIsImmediate() {
        stubAccount();
        when(user.getId()).thenReturn(4L);
        when(context.authenticatedUser()).thenReturn(user);
        when(configurationDomainService.retrieveChannelLimitIncreaseCoolingHours()).thenReturn(24);
        when(savingsAccountRepository.findOneWithNotFoundDetection(10L)).thenReturn(account);
        channel.setMaxDebitPerDay(new BigDecimal("1000"));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        final SavingsAccountChannelLimit row = customerLimit();
        row.setMaxPerDay(new BigDecimal("100"));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.of(row));
        when(selfServiceUserClientMappingRepository.existsByAppUser_Id(4L)).thenReturn(true);
        when(selfServiceUserClientMappingRepository.existsByAppUser_IdAndClient_Id(4L, 8L)).thenReturn(true);

        service.update(10L, command("""
                { "paymentTypeId": 3, "direction": "DEBIT", "maxPerDay": 500, "locale": "en" }
                """));
        assertEquals(0, new BigDecimal("100").compareTo(row.getMaxPerDay()));
        assertEquals(0, new BigDecimal("500").compareTo(row.getPendingMaxPerDay()));
        assertTrue(row.getPendingEffectiveOn().isAfter(OffsetDateTime.now(ZoneOffset.UTC).plusHours(23)));

        when(selfServiceUserClientMappingRepository.existsByAppUser_Id(4L)).thenReturn(false);
        service.update(10L, command("""
                { "paymentTypeId": 3, "direction": "DEBIT", "maxPerDay": 500, "locale": "en" }
                """));
        assertEquals(0, new BigDecimal("500").compareTo(row.getMaxPerDay()));
        assertNull(row.getPendingEffectiveOn());
        verify(limitRepository, times(2)).save(row);
    }

    @Test
    void selfServiceDecreaseAppliesImmediatelyAndCannotExceedCeiling() {
        stubAccount();
        when(user.getId()).thenReturn(4L);
        when(context.authenticatedUser()).thenReturn(user);
        when(configurationDomainService.retrieveChannelLimitIncreaseCoolingHours()).thenReturn(24);
        when(savingsAccountRepository.findOneWithNotFoundDetection(10L)).thenReturn(account);
        channel.setMaxDebitPerDay(new BigDecimal("1000"));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        final SavingsAccountChannelLimit row = customerLimit();
        row.setMaxPerDay(new BigDecimal("500"));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.of(row));
        when(selfServiceUserClientMappingRepository.existsByAppUser_Id(4L)).thenReturn(true);
        when(selfServiceUserClientMappingRepository.existsByAppUser_IdAndClient_Id(4L, 8L)).thenReturn(true);

        service.update(10L, command("""
                { "paymentTypeId": 3, "direction": "DEBIT", "maxPerDay": 100, "locale": "en" }
                """));
        assertEquals(0, new BigDecimal("100").compareTo(row.getMaxPerDay()));
        assertNull(row.getPendingEffectiveOn());

        channel.setMaxDebitPerDay(new BigDecimal("100"));
        assertTrue(failsUpdate("""
                { "paymentTypeId": 3, "direction": "DEBIT", "maxPerDay": 250, "locale": "en" }
                """).contains("exceeds.ceiling"));
    }

    @Test
    void customerDailyAboveOwnMonthlyIsRejected() {
        stubLimitUpdate();
        assertTrue(failsUpdate("""
                { "paymentTypeId": 3, "direction": "DEBIT", "maxPerDay": 500, "maxPerMonth": 200, "locale": "en" }
                """).contains("must.not.exceed.monthly"));
    }

    @Test
    void customerDailyUnderDailyCeilingButAboveMonthlyCeilingIsRejected() {
        stubLimitUpdate();
        channel.setMaxDebitPerDay(new BigDecimal("1000"));
        channel.setMaxDebitPerMonth(new BigDecimal("200"));
        assertTrue(failsUpdate("""
                { "paymentTypeId": 3, "direction": "DEBIT", "maxPerDay": 500, "locale": "en" }
                """).contains("must.not.exceed.monthly"));
    }

    @Test
    void duePendingIncreaseIsAppliedBeforeEnforcement() {
        stubUser(false);
        stubAccount();
        final SavingsAccountChannelLimit customer = customerLimit();
        customer.setMaxPerTxn(new BigDecimal("100"));
        customer.setPendingMaxPerTxn(new BigDecimal("500"));
        customer.setPendingEffectiveOn(OffsetDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.of(customer));

        assertTrue(authorize(new BigDecimal("400"), false).isEmpty());
        assertEquals(0, new BigDecimal("500").compareTo(customer.getMaxPerTxn()));
        assertNull(customer.getPendingEffectiveOn());
    }

    private SavingsChannelLimitHold authorize(final BigDecimal amount, final boolean transfer) {
        final PaymentDetail paymentDetail = transfer ? null
                : PaymentDetail.instance(channel.getPaymentType(), null, null, null, null, null);
        return service.authorize(new SavingsChannelLimitCommand(account, TXN_DATE, amount, paymentDetail, SavingsChannelLimitDirection.DEBIT,
                transfer, true, false));
    }

    private String fails(final BigDecimal amount, final boolean transfer) {
        return fails(new SavingsChannelLimitCommand(account, TXN_DATE, amount,
                transfer ? null : PaymentDetail.instance(channel.getPaymentType(), null, null, null, null, null),
                SavingsChannelLimitDirection.DEBIT, transfer, true, false));
    }

    private String fails(final SavingsChannelLimitCommand command) {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.authorize(command));
        return ex.getErrors().get(0).getUserMessageGlobalisationCode();
    }

    private String failsUpdate(final String json) {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> service.update(10L, command(json)));
        return ex.getErrors().get(0).getUserMessageGlobalisationCode();
    }

    private void stubLimitUpdate() {
        stubAccount();
        when(user.getId()).thenReturn(4L);
        when(context.authenticatedUser()).thenReturn(user);
        when(savingsAccountRepository.findOneWithNotFoundDetection(10L)).thenReturn(account);
        when(productChannelRepository.findByProductIdAndPaymentTypeId(1L, 3L)).thenReturn(Optional.of(channel));
        when(limitRepository.findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(10L, 7L, SavingsChannelLimitDirection.DEBIT))
                .thenReturn(Optional.empty());
        when(selfServiceUserClientMappingRepository.existsByAppUser_Id(4L)).thenReturn(false);
    }

    private void stubUser(final boolean bypass) {
        when(context.authenticatedUser()).thenReturn(user);
        when(user.hasSpecificPermissionTo(SavingsChannelLimitService.BYPASS_PERMISSION)).thenReturn(bypass);
    }

    private void stubAccount() {
        lenient().when(account.getId()).thenReturn(10L);
        lenient().when(account.productId()).thenReturn(1L);
        lenient().when(account.clientId()).thenReturn(8L);
    }

    private SavingsAccountChannelLimit customerLimit() {
        final SavingsProduct product = new SavingsProduct() {};
        product.setId(1L);
        channel.setProduct(product);
        return SavingsAccountChannelLimit.create(account, channel, SavingsChannelLimitDirection.DEBIT, 4L,
                OffsetDateTime.now(ZoneOffset.UTC));
    }

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromJsonHelper, "SAVINGSACCOUNT", null, null, null, null, null, null,
                null, null, null, null, null, null, null);
    }
}
