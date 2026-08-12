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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.OTPDeliveryMethod;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.domain.OTPRequestRepository;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.domain.TFAccessTokenRepository;
import org.apache.fineract.infrastructure.security.exception.UserSessionNotFoundException;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TwoFactorServiceImplSessionEnforcementTest {

    private static final String NEW_TOKEN = "NEWTOKEN";
    private static final String OTP = "123456";

    @Mock
    private AccessTokenGenerationService accessTokenGenerationService;
    @Mock
    private PlatformEmailService emailService;
    @Mock
    private SmsMessageScheduledJobService smsMessageScheduledJobService;
    @Mock
    private OTPRequestRepository otpRequestRepository;
    @Mock
    private TFAccessTokenRepository tfAccessTokenRepository;
    @Mock
    private SmsMessageRepository smsMessageRepository;
    @Mock
    private TwoFactorConfigurationService configurationService;
    @Mock
    private TotpService totpService;
    @Mock
    private AppUserRepository appUserRepository;
    @Mock
    private ConfigurationDomainService configurationDomainService;
    @Mock
    private CacheManager cacheManager;
    @Mock
    private Cache tokenCache;
    @Mock
    private AppUser user;

    private TwoFactorServiceImpl twoFactorService;
    private final AtomicLong nextTokenId = new AtomicLong(100L);

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(FineractPlatformTenant.builder().id(1L).tenantIdentifier("default").name("default").timezoneId("UTC").build());
        twoFactorService = new TwoFactorServiceImpl(accessTokenGenerationService, emailService, smsMessageScheduledJobService,
                otpRequestRepository, tfAccessTokenRepository, smsMessageRepository, configurationService, totpService, appUserRepository,
                configurationDomainService, cacheManager);

        when(user.getUsername()).thenReturn("admin");
        when(configurationService.isTotpDeliveryEnabled()).thenReturn(false);
        when(configurationService.getAccessTokenLiveTime()).thenReturn(3600);
        when(accessTokenGenerationService.generateRandomToken()).thenReturn(NEW_TOKEN);
        when(otpRequestRepository.getOTPRequestForUser(user)).thenReturn(
                OTPRequest.create(OTP, 300, false, new OTPDeliveryMethod().setName(TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME)));
        when(cacheManager.getCache("userTFAccessToken")).thenReturn(tokenCache);
        when(tfAccessTokenRepository.save(any(TFAccessToken.class))).thenAnswer(invocation -> {
            TFAccessToken saved = invocation.getArgument(0);
            saved.setId(nextTokenId.getAndIncrement());
            return saved;
        });
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    private TFAccessToken activeToken(long id, String token) {
        TFAccessToken accessToken = TFAccessToken.create(token, user, 3600);
        accessToken.setId(id);
        return accessToken;
    }

    @Test
    void newLoginRevokesOlderSessionsWhenSingleSessionEnforced() {
        when(configurationDomainService.retrieveMaxActiveSessions()).thenReturn(1);
        TFAccessToken oldToken1 = activeToken(1L, "OLD1");
        TFAccessToken oldToken2 = activeToken(2L, "OLD2");
        when(tfAccessTokenRepository.findByUserAndEnabledTrueOrderByIdDesc(user)).thenAnswer(invocation -> {
            TFAccessToken newToken = activeToken(nextTokenId.get() - 1, NEW_TOKEN);
            return List.of(newToken, oldToken2, oldToken1);
        });

        TFAccessToken newToken = twoFactorService.createAccessTokenFromOTP(user, OTP, "10.0.0.5", "Mozilla/5.0");

        assertTrue(newToken.isEnabled());
        assertEquals("10.0.0.5", newToken.getIpAddress());
        assertEquals("Mozilla/5.0", newToken.getUserAgent());

        assertFalse(oldToken1.isEnabled());
        assertFalse(oldToken2.isEnabled());
        assertEquals(TwoFactorConstants.REVOCATION_REASON_SUPERSEDED, oldToken1.getRevocationReason());
        assertEquals(TwoFactorConstants.REVOCATION_REASON_SUPERSEDED, oldToken2.getRevocationReason());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TFAccessToken>> revokedCaptor = ArgumentCaptor.forClass(List.class);
        verify(tfAccessTokenRepository).saveAll(revokedCaptor.capture());
        assertEquals(2, revokedCaptor.getValue().size());

        verify(tokenCache).evict("defaultadminOLD1tok");
        verify(tokenCache).evict("defaultadminOLD2tok");
    }

    @Test
    void newLoginKeepsNewestSessionsUpToConfiguredMaximum() {
        when(configurationDomainService.retrieveMaxActiveSessions()).thenReturn(2);
        TFAccessToken oldToken1 = activeToken(1L, "OLD1");
        TFAccessToken oldToken2 = activeToken(2L, "OLD2");
        when(tfAccessTokenRepository.findByUserAndEnabledTrueOrderByIdDesc(user)).thenAnswer(invocation -> {
            TFAccessToken newToken = activeToken(nextTokenId.get() - 1, NEW_TOKEN);
            return List.of(newToken, oldToken2, oldToken1);
        });

        twoFactorService.createAccessTokenFromOTP(user, OTP, null, null);

        assertTrue(oldToken2.isEnabled());
        assertFalse(oldToken1.isEnabled());
        verify(tokenCache).evict("defaultadminOLD1tok");
        verify(tokenCache, never()).evict("defaultadminOLD2tok");
    }

    @Test
    void noSessionsRevokedWhenEnforcementDisabled() {
        when(configurationDomainService.retrieveMaxActiveSessions()).thenReturn(null);

        TFAccessToken newToken = twoFactorService.createAccessTokenFromOTP(user, OTP, "10.0.0.5", "Mozilla/5.0");

        assertTrue(newToken.isEnabled());
        assertNull(newToken.getRevocationReason());
        verify(tfAccessTokenRepository, never()).findByUserAndEnabledTrueOrderByIdDesc(any());
        verify(tfAccessTokenRepository, never()).saveAll(any());
        verify(tokenCache, never()).evict(anyString());
    }

    @Test
    void adminRevokeDisablesSessionAndEvictsCache() {
        when(user.getId()).thenReturn(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        TFAccessToken token = activeToken(5L, "OLD1");
        when(tfAccessTokenRepository.findById(5L)).thenReturn(Optional.of(token));

        TFAccessToken revoked = twoFactorService.revokeSessionForUser(7L, 5L);

        assertFalse(revoked.isEnabled());
        assertEquals(TwoFactorConstants.REVOCATION_REASON_ADMIN, revoked.getRevocationReason());
        verify(tfAccessTokenRepository).save(token);
        verify(tokenCache).evict("defaultadminOLD1tok");
    }

    @Test
    void revokeSessionBelongingToAnotherUserThrows() {
        when(user.getId()).thenReturn(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        AppUser otherUser = mock(AppUser.class);
        when(otherUser.getId()).thenReturn(99L);
        TFAccessToken token = TFAccessToken.create("OTHER", otherUser, 3600);
        token.setId(5L);
        when(tfAccessTokenRepository.findById(5L)).thenReturn(Optional.of(token));

        assertThrows(UserSessionNotFoundException.class, () -> twoFactorService.revokeSessionForUser(7L, 5L));
        assertTrue(token.isEnabled());
        verify(tokenCache, never()).evict(anyString());
    }

    @Test
    void revokeAlreadyDisabledSessionThrows() {
        when(user.getId()).thenReturn(7L);
        when(appUserRepository.findById(7L)).thenReturn(Optional.of(user));
        TFAccessToken token = activeToken(5L, "OLD1");
        token.revoke(TwoFactorConstants.REVOCATION_REASON_SUPERSEDED);
        when(tfAccessTokenRepository.findById(5L)).thenReturn(Optional.of(token));

        assertThrows(UserSessionNotFoundException.class, () -> twoFactorService.revokeSessionForUser(7L, 5L));
        verify(tokenCache, never()).evict(anyString());
    }
}
