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
import static org.apache.fineract.workflow.api.WorkflowApiConstants.MODULE_ENABLED_PROPERTY;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationProperty;
import org.apache.fineract.infrastructure.configuration.domain.GlobalConfigurationRepositoryWrapper;
import org.apache.fineract.infrastructure.configuration.exception.GlobalConfigurationPropertyNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Tenant-level switch for the approval workflow engine, backed by the {@code enable-approval-workflows} global
 * configuration entry. Smaller institutions that do not need approval chains leave it disabled (the default) and
 * business transactions proceed without workflow approval; larger institutions opt in per tenant at runtime, without a
 * server restart. This complements the deployment-wide {@code fineract.module.workflow.enabled} property.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class WorkflowTenantConfiguration {

    private final GlobalConfigurationRepositoryWrapper globalConfigurationRepository;

    public boolean isApprovalWorkflowsEnabled() {
        try {
            final GlobalConfigurationProperty config = globalConfigurationRepository
                    .findOneByNameWithNotFoundDetection(ENABLE_APPROVAL_WORKFLOWS);
            return config.isEnabled();
        } catch (final GlobalConfigurationPropertyNotFoundException e) {
            log.warn("Global configuration '{}' not found, defaulting to disabled", ENABLE_APPROVAL_WORKFLOWS);
            return false;
        }
    }
}
