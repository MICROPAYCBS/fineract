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
package org.apache.fineract.portfolio.loanaccount.serialization;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.exception.GeneralPlatformDomainRuleException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanTransactionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LoanTransactionValidatorDirectRepaymentTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;

    private LoanTransactionValidatorImpl validator;

    @BeforeEach
    void setUp() {
        validator = new LoanTransactionValidatorImpl(null, null, null, null, null, null, null, null, null, null, null,
                configurationDomainService);
    }

    @Test
    void validateDirectLoanRepaymentPermitted_whenEnabled_allowsRepayment() {
        when(configurationDomainService.isAllowDirectLoanRepaymentsEnabled()).thenReturn(true);

        assertDoesNotThrow(() -> validator.validateDirectLoanRepaymentPermitted(LoanTransactionType.REPAYMENT, false));
    }

    @Test
    void validateDirectLoanRepaymentPermitted_whenDisabled_blocksRepayment() {
        when(configurationDomainService.isAllowDirectLoanRepaymentsEnabled()).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validator.validateDirectLoanRepaymentPermitted(LoanTransactionType.REPAYMENT, false));
    }

    @Test
    void validateDirectLoanRepaymentPermitted_whenDisabled_blocksRecoveryRepayment() {
        when(configurationDomainService.isAllowDirectLoanRepaymentsEnabled()).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validator.validateDirectLoanRepaymentPermitted(LoanTransactionType.REPAYMENT, true));
    }

    @Test
    void validateDirectLoanRepaymentPermitted_whenDisabled_blocksDownPayment() {
        when(configurationDomainService.isAllowDirectLoanRepaymentsEnabled()).thenReturn(false);

        assertThrows(GeneralPlatformDomainRuleException.class,
                () -> validator.validateDirectLoanRepaymentPermitted(LoanTransactionType.DOWN_PAYMENT, false));
    }

    @Test
    void validateDirectLoanRepaymentPermitted_whenDisabled_allowsChargeRefund() {
        when(configurationDomainService.isAllowDirectLoanRepaymentsEnabled()).thenReturn(false);

        assertDoesNotThrow(() -> validator.validateDirectLoanRepaymentPermitted(LoanTransactionType.CHARGE_REFUND, false));
    }
}
