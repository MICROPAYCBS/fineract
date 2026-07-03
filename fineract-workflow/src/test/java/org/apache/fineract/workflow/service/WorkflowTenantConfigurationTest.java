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
package org.apache.fineract.workflow.service;

import static org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants.ENABLE_APPROVAL_WORKFLOWS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.exception.GlobalConfigurationPropertyNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WorkflowTenantConfigurationTest {

    @Mock
    private GlobalConfigurationRepositoryWrapper globalConfigurationRepository;

    @Mock
    private GlobalConfigurationProperty configurationProperty;

    @InjectMocks
    private WorkflowTenantConfiguration tenantConfiguration;

    @Test
    void enabledConfigurationEnablesWorkflows() {
        when(globalConfigurationRepository.findOneByNameWithNotFoundDetection(ENABLE_APPROVAL_WORKFLOWS))
                .thenReturn(configurationProperty);
        when(configurationProperty.isEnabled()).thenReturn(true);

        assertThat(tenantConfiguration.isApprovalWorkflowsEnabled()).isTrue();
    }

    @Test
    void disabledConfigurationDisablesWorkflows() {
        when(globalConfigurationRepository.findOneByNameWithNotFoundDetection(ENABLE_APPROVAL_WORKFLOWS))
                .thenReturn(configurationProperty);
        when(configurationProperty.isEnabled()).thenReturn(false);

        assertThat(tenantConfiguration.isApprovalWorkflowsEnabled()).isFalse();
    }

    @Test
    void missingConfigurationDefaultsToDisabled() {
        when(globalConfigurationRepository.findOneByNameWithNotFoundDetection(ENABLE_APPROVAL_WORKFLOWS))
                .thenThrow(new GlobalConfigurationPropertyNotFoundException(ENABLE_APPROVAL_WORKFLOWS));

        assertThat(tenantConfiguration.isApprovalWorkflowsEnabled()).isFalse();
    }
}
