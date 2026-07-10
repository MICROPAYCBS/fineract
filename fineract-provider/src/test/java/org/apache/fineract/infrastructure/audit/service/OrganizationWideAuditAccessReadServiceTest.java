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
package org.apache.fineract.infrastructure.audit.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.audit.api.OrganizationWideAuditConstants;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrganizationWideAuditAccessReadServiceTest {

    @Mock
    private ConfigurationDomainService configurationDomainService;

    @Mock
    private PlatformSecurityContext context;

    @Mock
    private AppUser appUser;

    private OrganizationWideAuditAccessReadServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OrganizationWideAuditAccessReadServiceImpl(configurationDomainService, context);
    }

    @Test
    void returnsFalseWhenConfigDisabled() {
        when(configurationDomainService.isOrganizationWideAuditViewEnabled()).thenReturn(false);

        assertThat(service.isOrganizationWideAuditViewEnabledForCurrentUser()).isFalse();
    }

    @Test
    void returnsFalseWhenUserCannotReadAudits() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(configurationDomainService.isOrganizationWideAuditViewEnabled()).thenReturn(true);
        when(appUser.hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "READ_AUDIT")).thenReturn(true);

        assertThat(service.isOrganizationWideAuditViewEnabledForCurrentUser()).isFalse();
    }

    @Test
    void returnsFalseWhenOrgWidePermissionMissing() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(configurationDomainService.isOrganizationWideAuditViewEnabled()).thenReturn(true);
        when(appUser.hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "READ_AUDIT")).thenReturn(false);
        when(appUser.hasSpecificPermissionTo(OrganizationWideAuditConstants.VIEW_ORGANIZATION_AUDIT_PERMISSION)).thenReturn(false);

        assertThat(service.isOrganizationWideAuditViewEnabledForCurrentUser()).isFalse();
    }

    @Test
    void returnsTrueWhenConfigEnabledAndUserHasRequiredPermissions() {
        when(context.authenticatedUser()).thenReturn(appUser);
        when(configurationDomainService.isOrganizationWideAuditViewEnabled()).thenReturn(true);
        when(appUser.hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "READ_AUDIT")).thenReturn(false);
        when(appUser.hasSpecificPermissionTo(OrganizationWideAuditConstants.VIEW_ORGANIZATION_AUDIT_PERMISSION)).thenReturn(true);

        assertThat(service.isOrganizationWideAuditViewEnabledForCurrentUser()).isTrue();
    }
}
