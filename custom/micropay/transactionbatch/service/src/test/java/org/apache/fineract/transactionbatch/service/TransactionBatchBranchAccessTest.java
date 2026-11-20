/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. Licensed under the Apache License, Version 2.0.
 */
package org.apache.fineract.transactionbatch.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.micropay.fineract.infrastructure.interbranch.MicropayCrossBranchTransactionAccessService;
import java.util.Map;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.apache.fineract.transactionbatch.domain.TransactionBatchConfigRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchItemRepository;
import org.apache.fineract.transactionbatch.domain.TransactionBatchRepository;
import org.apache.fineract.transactionbatch.exception.TransactionBatchRuleException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.Test;

class TransactionBatchBranchAccessTest {

    // Head office .1. with Kampala .1.2. (and its sub-branch Nakawa .1.2.5.) and Jinja .1.3.
    private static final Map<Long, String> HIERARCHY = Map.of(1L, ".1.", 2L, ".1.2.", 5L, ".1.2.5.", 3L, ".1.3.");

    @Test
    void userMayUseTheirBranchAndSubBranchesWithoutPermission() {
        final TransactionBatchAccess access = access(2L, false, false);
        assertDoesNotThrow(() -> access.assertLineBranchAllowed(2L));
        assertDoesNotThrow(() -> access.assertLineBranchAllowed(5L));
    }

    @Test
    void otherBranchNeedsCrossBranchPermission() {
        assertThrows(TransactionBatchRuleException.class, () -> access(2L, false, false).assertLineBranchAllowed(3L));
        assertDoesNotThrow(() -> access(2L, true, true).assertLineBranchAllowed(3L));
    }

    @Test
    void postingASubBranchLineDoesNotNeedCrossBranchServicing() {
        assertDoesNotThrow(() -> access(1L, false, false).requireCrossBranchServicingForPosting(2L, 5L));
        assertDoesNotThrow(() -> access(1L, false, false).requireCrossBranchServicingForPosting(2L, 2L));
    }

    @Test
    void postingAnOtherBranchLineNeedsCrossBranchServicingOn() {
        assertThrows(TransactionBatchRuleException.class, () -> access(1L, false, false).requireCrossBranchServicingForPosting(2L, 3L));
        // The job runs as the system user: its own permission is not required.
        assertDoesNotThrow(() -> access(1L, false, true).requireCrossBranchServicingForPosting(2L, 3L));
    }

    private static TransactionBatchAccess access(final Long userOfficeId, final boolean userMayCrossBranch,
            final boolean crossBranchServicingOn) {
        final OfficeRepositoryWrapper offices = mock(OfficeRepositoryWrapper.class);
        when(offices.findOneWithNotFoundDetection(anyLong())).thenAnswer(call -> office(call.getArgument(0)));
        final AppUser user = mock(AppUser.class);
        final Office userOffice = office(userOfficeId);
        when(user.getOffice()).thenReturn(userOffice);
        final PlatformSecurityContext context = mock(PlatformSecurityContext.class);
        when(context.authenticatedUser()).thenReturn(user);
        final MicropayCrossBranchTransactionAccessService crossBranch = mock(MicropayCrossBranchTransactionAccessService.class);
        when(crossBranch.isCrossBranchTransactionEnabledForCurrentUser()).thenReturn(userMayCrossBranch);
        when(crossBranch.isCrossBranchServicingEnabled()).thenReturn(crossBranchServicingOn);
        return new TransactionBatchAccess(context, mock(TransactionBatchRepository.class), mock(TransactionBatchItemRepository.class),
                mock(TransactionBatchConfigRepository.class), offices, crossBranch);
    }

    private static Office office(final Long id) {
        final Office office = mock(Office.class);
        when(office.getId()).thenReturn(id);
        when(office.getHierarchy()).thenReturn(HIERARCHY.get(id));
        when(office.getName()).thenReturn("Office " + id);
        return office;
    }
}
