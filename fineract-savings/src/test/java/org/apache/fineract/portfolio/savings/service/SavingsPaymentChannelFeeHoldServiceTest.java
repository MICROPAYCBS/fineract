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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import jakarta.persistence.PersistenceException;
import org.apache.fineract.portfolio.paymenttype.domain.PaymentTypeHoldRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelBlockRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelChargeRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsPaymentChannelStatus;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelHoldRepository;
import org.apache.fineract.portfolio.savings.exception.SavingsPaymentChannelSchemaMissingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.orm.jpa.JpaSystemException;

@ExtendWith(MockitoExtension.class)
class SavingsPaymentChannelFeeHoldServiceTest {

    @Mock
    private SavingsProductPaymentChannelHoldRepository productHoldRepository;
    @Mock
    private PaymentTypeHoldRepository paymentTypeHoldRepository;
    @Mock
    private SavingsAccountPaymentChannelBlockRepository accountBlockRepository;
    @Mock
    private SavingsAccountPaymentChannelRepository accountChannelRepository;
    @Mock
    private SavingsAccountPaymentChannelChargeRepository accountChannelChargeRepository;
    @Mock
    private SavingsAccountChargeRepository savingsAccountChargeRepository;

    @InjectMocks
    private SavingsPaymentChannelFeeHoldService service;

    @Test
    void missingAccountChannelTableBecomesUsefulError() {
        final JpaSystemException missingTable = new JpaSystemException(new PersistenceException(
                "ERROR: relation \"m_savings_account_payment_channel\" does not exist"));
        when(this.accountChannelRepository.findByPaymentTypeIdAndStatus(7L, SavingsPaymentChannelStatus.ACTIVE.getValue()))
                .thenThrow(missingTable);

        final SavingsPaymentChannelSchemaMissingException exception = assertThrows(SavingsPaymentChannelSchemaMissingException.class,
                () -> this.service.skipMissedCyclesForPaymentType(7L));

        assertEquals("error.msg.savings.payment.channel.schema.missing", exception.getGlobalisationMessageCode());
        assertEquals(SavingsPaymentChannelSchemaMissingException.MESSAGE, exception.getDefaultUserMessage());
        assertSame(missingTable, exception.getCause());
    }

    @Test
    void otherPersistenceFailuresStayUnchanged() {
        final JpaSystemException failure = new JpaSystemException(new PersistenceException("connection reset"));
        when(this.accountChannelRepository.findByPaymentTypeIdAndStatus(7L, SavingsPaymentChannelStatus.ACTIVE.getValue()))
                .thenThrow(failure);

        final JpaSystemException thrown = assertThrows(JpaSystemException.class, () -> this.service.skipMissedCyclesForPaymentType(7L));

        assertSame(failure, thrown);
    }
}
