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
package com.micropay.fineract.platform.configuration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.data.GlobalConfigurationPropertyData;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.junit.jupiter.api.Test;

class MicropayConfigurationServiceSessionIdleTest {

    @Test
    void disabledIdleTimeoutReturnsZero() {
        assertEquals(0, service(false, 15L).retrieveSessionIdleTimeoutMinutes());
    }

    @Test
    void enabledIdleTimeoutReturnsTheStoredValue() {
        assertEquals(20, service(true, 20L).retrieveSessionIdleTimeoutMinutes());
    }

    @Test
    void enabledIdleTimeoutWithNoValueReturnsFifteen() {
        assertEquals(15, service(true, null).retrieveSessionIdleTimeoutMinutes());
    }

    @Test
    void disabledWarningReturnsZero() {
        assertEquals(0, warning(false, 60L).retrieveSessionIdleWarningSeconds());
    }

    @Test
    void enabledWarningReturnsTheStoredValue() {
        assertEquals(30, warning(true, 30L).retrieveSessionIdleWarningSeconds());
    }

    @Test
    void enabledWarningWithNoValueReturnsSixty() {
        assertEquals(60, warning(true, null).retrieveSessionIdleWarningSeconds());
    }

    private static MicropayConfigurationServiceImpl service(final boolean enabled, final Long value) {
        return domain(MicropayGlobalConfigurationConstants.SESSION_IDLE_TIMEOUT_MINUTES, enabled, value);
    }

    private static MicropayConfigurationServiceImpl warning(final boolean enabled, final Long value) {
        return domain(MicropayGlobalConfigurationConstants.SESSION_IDLE_WARNING_SECONDS, enabled, value);
    }

    private static MicropayConfigurationServiceImpl domain(final String name, final boolean enabled, final Long value) {
        final GlobalConfigurationRepositoryWrapper repository = mock(GlobalConfigurationRepositoryWrapper.class);
        final GlobalConfigurationProperty property = mock(GlobalConfigurationProperty.class);
        when(repository.findOneByNameWithNotFoundDetection(name)).thenReturn(property);
        when(property.toData()).thenReturn(new GlobalConfigurationPropertyData().setEnabled(enabled).setValue(value));
        return new MicropayConfigurationServiceImpl(repository);
    }
}
