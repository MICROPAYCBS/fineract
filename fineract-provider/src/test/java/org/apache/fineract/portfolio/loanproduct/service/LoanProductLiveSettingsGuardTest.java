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
package org.apache.fineract.portfolio.loanproduct.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.loanaccount.domain.LoanRepositoryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoanProductLiveSettingsGuardTest {

    private static final Long PRODUCT_ID = 5L;

    private LoanRepositoryWrapper loanRepositoryWrapper;
    private LoanProductWritePlatformServiceJpaRepositoryImpl service;
    private Method rejectLiveSettingChanges;

    @BeforeEach
    void setUp() throws Exception {
        loanRepositoryWrapper = mock(LoanRepositoryWrapper.class);
        service = new LoanProductWritePlatformServiceJpaRepositoryImpl(null, null, null, null, null, null, null, null, null,
                loanRepositoryWrapper, null, null, null, null, null, null, null, null, null);
        rejectLiveSettingChanges = LoanProductWritePlatformServiceJpaRepositoryImpl.class
                .getDeclaredMethod("rejectLiveSettingChangesWhenProductHasLoans", Long.class, Map.class);
        rejectLiveSettingChanges.setAccessible(true);
    }

    @Test
    void blocksALockedFieldWhenTheProductHasLoans() throws Exception {
        when(loanRepositoryWrapper.doLoansExistForProduct(PRODUCT_ID)).thenReturn(true);
        final Map<String, Object> changes = new HashMap<>();
        changes.put("overdueDaysForNPA", 45);

        final InvocationTargetException thrown = assertThrows(InvocationTargetException.class,
                () -> rejectLiveSettingChanges.invoke(service, PRODUCT_ID, changes));

        assertThat(thrown.getCause()).isInstanceOf(PlatformApiDataValidationException.class);
        final PlatformApiDataValidationException validation = (PlatformApiDataValidationException) thrown.getCause();
        assertThat(validation.getGlobalisationMessageCode()).isEqualTo("validation.msg.loanproduct.has.loans.live.settings.locked");
        assertThat(validation.getDefaultUserMessage()).contains("overdueDaysForNPA");
        assertThat(validation.getErrors()).extracting(ApiParameterError::getParameterName).containsExactly("overdueDaysForNPA");
    }

    @Test
    void ignoresFieldsTheRequestDidNotChange() {
        final Map<String, Object> changes = new HashMap<>();
        changes.put("name", "Renamed product");
        changes.put("description", "New description");

        assertThatNoException().isThrownBy(() -> rejectLiveSettingChanges.invoke(service, PRODUCT_ID, changes));
        verify(loanRepositoryWrapper, never()).doLoansExistForProduct(PRODUCT_ID);
    }

    @Test
    void allowsALockedFieldWhenTheProductHasNoLoans() {
        when(loanRepositoryWrapper.doLoansExistForProduct(PRODUCT_ID)).thenReturn(false);
        final Map<String, Object> changes = new HashMap<>();
        changes.put("accountingRule", 2);

        assertThatNoException().isThrownBy(() -> rejectLiveSettingChanges.invoke(service, PRODUCT_ID, changes));
    }
}
