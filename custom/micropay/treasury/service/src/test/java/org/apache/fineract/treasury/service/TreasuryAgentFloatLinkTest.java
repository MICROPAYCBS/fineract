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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryAgentFloat;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatRepository;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatStatus;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.SavingsAccountInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreasuryAgentFloatLinkTest {

    private static final long TREASURY_GL = 10L;
    private static final long PAYMENT_TYPE = 5L;
    private static final long PRODUCT = 3L;

    private final TreasuryAccountRepository accountRepository = mock(TreasuryAccountRepository.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasurySettingService settingService = mock(TreasurySettingService.class);
    private final TreasuryAgentFloatWritePlatformServiceImpl service = new TreasuryAgentFloatWritePlatformServiceImpl(
            mock(TreasuryActor.class), new TreasuryWorkflowDataValidator(new FromJsonHelper()), mock(TreasuryAgentFloatRepository.class),
            mock(TreasuryFloatRequestRepository.class), this.accountRepository, this.lookup, this.settingService,
            mock(SavingsAccountWritePlatformService.class), new FromJsonHelper());
    private final TreasuryAccount treasuryAccount = new TreasuryAccount();

    @BeforeEach
    void setUp() {
        this.treasuryAccount.setId(1L);
        this.treasuryAccount.setCode("FLOAT-POOL");
        this.treasuryAccount.setGlAccountId(TREASURY_GL);
        this.treasuryAccount.setOfficeId(2L);
        this.treasuryAccount.setCurrencyCode("UGX");
        this.treasuryAccount.setStatus(TreasuryAccountStatus.ACTIVE);
        when(this.accountRepository.findById(1L)).thenReturn(Optional.of(this.treasuryAccount));
        lenient().when(this.settingService.requireFloatPaymentType("UGX")).thenReturn(PAYMENT_TYPE);
        lenient().when(this.lookup.savingsFundSourceGlAccount(PRODUCT, PAYMENT_TYPE)).thenReturn(Optional.of(TREASURY_GL));
    }

    @Test
    void linkIsValidWhenThePaymentTypeLandsOnTheTreasuryGlAccount() {
        assertEquals(PAYMENT_TYPE, this.service.validateLink(agentFloat(), savings(2L, "UGX", 300)));
    }

    @Test
    void fundSourceOnAnotherGlAccountIsRefused() {
        when(this.lookup.savingsFundSourceGlAccount(PRODUCT, PAYMENT_TYPE)).thenReturn(Optional.of(99L));

        assertRejected("fund.source.mismatch", savings(2L, "UGX", 300));
    }

    @Test
    void productWithoutAFundSourceIsRefused() {
        when(this.lookup.savingsFundSourceGlAccount(PRODUCT, PAYMENT_TYPE)).thenReturn(Optional.empty());

        assertRejected("fund.source.mismatch", savings(2L, "UGX", 300));
    }

    @Test
    void savingsInAnotherOfficeIsRefused() {
        assertRejected("office.mismatch", savings(1L, "UGX", 300));
    }

    @Test
    void savingsInAnotherCurrencyIsRefused() {
        assertRejected("currency.mismatch", savings(2L, "USD", 300));
    }

    @Test
    void inactiveSavingsIsRefused() {
        assertRejected("savings.account.not.active", savings(2L, "UGX", 100));
    }

    @Test
    void closedTreasuryAccountIsRefused() {
        this.treasuryAccount.setStatus(TreasuryAccountStatus.CLOSED);

        assertRejected("treasury.account.closed", savings(2L, "UGX", 300));
    }

    private void assertRejected(final String codeSuffix, final SavingsAccountInfo savings) {
        final TreasuryWorkflowException exception = assertThrows(TreasuryWorkflowException.class,
                () -> this.service.validateLink(agentFloat(), savings));
        assertEquals("error.msg.treasury.agent.float." + codeSuffix, exception.getGlobalisationMessageCode());
    }

    private static TreasuryAgentFloat agentFloat() {
        final TreasuryAgentFloat agentFloat = new TreasuryAgentFloat();
        agentFloat.setClientId(8L);
        agentFloat.setSavingsAccountId(9L);
        agentFloat.setTreasuryAccountId(1L);
        agentFloat.setStatus(TreasuryAgentFloatStatus.ACTIVE);
        return agentFloat;
    }

    private static SavingsAccountInfo savings(final Long officeId, final String currency, final int status) {
        return new SavingsAccountInfo(9L, "000000009", 8L, PRODUCT, status, currency, officeId, new BigDecimal("1000"));
    }
}
