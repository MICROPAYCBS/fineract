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
package org.apache.fineract.infrastructure.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.UserSessionData;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.domain.TFAccessTokenRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;

@ExtendWith(MockitoExtension.class)
class UserSessionHistoryReadServiceTest {

    @Mock
    private TFAccessTokenRepository tfAccessTokenRepository;

    private UserSessionHistoryReadService readService;

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(FineractPlatformTenant.builder().id(1L).tenantIdentifier("default").name("default").timezoneId("UTC").build());
        readService = new UserSessionHistoryReadService(tfAccessTokenRepository);
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @SuppressWarnings("unchecked")
    @Test
    void mapsTokensToSessionDataWithUserAndRevocationDetails() {
        AppUser user = mock(AppUser.class);
        when(user.getId()).thenReturn(7L);
        when(user.getUsername()).thenReturn("admin");

        TFAccessToken activeToken = TFAccessToken.create("TOK1", user, 3600);
        activeToken.setId(2L);
        TFAccessToken revokedToken = TFAccessToken.create("TOK2", user, 3600);
        revokedToken.setId(1L);
        revokedToken.revoke(TwoFactorConstants.REVOCATION_REASON_SUPERSEDED);

        when(tfAccessTokenRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(activeToken, revokedToken), Pageable.ofSize(50), 2));

        Page<UserSessionData> result = readService.retrieveSessionHistory(null, null, null, null, null);

        assertEquals(2, result.getTotalFilteredRecords());
        UserSessionData active = result.getPageItems().get(0);
        assertEquals(7L, active.getUserId());
        assertEquals("admin", active.getUsername());
        assertNull(active.getRevocationReason());

        UserSessionData revoked = result.getPageItems().get(1);
        assertFalse(revoked.isActive());
        assertEquals(TwoFactorConstants.REVOCATION_REASON_SUPERSEDED, revoked.getRevocationReason());
    }

    @SuppressWarnings("unchecked")
    @Test
    void appliesPaginationDefaultsAndCaps() {
        when(tfAccessTokenRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), Pageable.ofSize(50), 0));

        readService.retrieveSessionHistory(null, null, null, null, null);
        readService.retrieveSessionHistory(null, null, null, 100, 25);
        readService.retrieveSessionHistory(null, null, null, null, 9999);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(tfAccessTokenRepository, org.mockito.Mockito.times(3)).findAll(any(Specification.class), pageableCaptor.capture());

        Pageable defaults = pageableCaptor.getAllValues().get(0);
        assertEquals(0, defaults.getPageNumber());
        assertEquals(UserSessionHistoryReadService.DEFAULT_PAGE_SIZE, defaults.getPageSize());
        assertEquals(Sort.by(Sort.Direction.DESC, "id"), defaults.getSort());

        Pageable offsetBased = pageableCaptor.getAllValues().get(1);
        assertEquals(4, offsetBased.getPageNumber());
        assertEquals(25, offsetBased.getPageSize());

        Pageable capped = pageableCaptor.getAllValues().get(2);
        assertEquals(UserSessionHistoryReadService.MAX_PAGE_SIZE, capped.getPageSize());
    }
}
