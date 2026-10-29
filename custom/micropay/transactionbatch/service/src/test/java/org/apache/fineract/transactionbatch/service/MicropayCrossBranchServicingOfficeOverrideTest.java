/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.micropay.fineract.infrastructure.interbranch.MicropayCrossBranchTransactionAccessService;
import com.micropay.fineract.platform.configuration.MicropayConfigurationService;
import java.util.Optional;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class MicropayCrossBranchServicingOfficeOverrideTest {

    @Test
    void overrideNamesTheOriginBranchWhenCrossBranchServicingIsOn() {
        final MicropayCrossBranchTransactionAccessService service = service(true);
        final Office origin = office(10L, ".2.");
        service.setServicingOfficeOverride(origin);
        final Optional<Office> resolved = service.resolveTransactionOffice(office(20L, ".3."));
        assertTrue(resolved.isPresent());
        assertEquals(10L, resolved.get().getId());
        service.clearServicingOfficeOverride();
    }

    @Test
    void overrideIsIgnoredWhenCrossBranchServicingIsOff() {
        final MicropayCrossBranchTransactionAccessService service = service(false);
        service.setServicingOfficeOverride(office(10L, ".2."));
        assertTrue(service.resolveTransactionOffice(office(20L, ".3.")).isEmpty());
    }

    @Test
    void overrideIsClearedWhenTheCallThrows() {
        final MicropayCrossBranchTransactionAccessService service = service(true);
        assertThrows(IllegalStateException.class, () -> service.withServicingOffice(office(10L, ".2."), () -> {
            throw new IllegalStateException("line failed");
        }));
        assertTrue(service.resolveTransactionOffice(office(20L, ".3.")).isEmpty());
    }

    private static MicropayCrossBranchTransactionAccessService service(final boolean crossBranch) {
        final MicropayConfigurationService configuration = mock(MicropayConfigurationService.class);
        when(configuration.isCrossBranchServicingEnabled()).thenReturn(crossBranch);
        final Office headOffice = office(1L, ".");
        final AppUser user = mock(AppUser.class);
        when(user.getOffice()).thenReturn(headOffice);
        when(user.hasSpecificPermissionTo(anyString())).thenReturn(true);
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        when(context.getAuthenticatedUserIfPresent()).thenReturn(user);
        return new MicropayCrossBranchTransactionAccessService(configuration, context);
    }

    private static Office office(final Long id, final String hierarchy) {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(id);
        when(office.getHierarchy()).thenReturn(hierarchy);
        return office;
    }
}
