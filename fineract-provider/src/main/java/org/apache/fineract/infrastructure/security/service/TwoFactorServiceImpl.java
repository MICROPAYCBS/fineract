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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.EmailDetail;
import org.apache.fineract.infrastructure.core.service.PlatformEmailService;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.OTPDeliveryMethod;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.domain.OTPRequestRepository;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.domain.TFAccessTokenRepository;
import org.apache.fineract.infrastructure.security.exception.AccessTokenInvalidIException;
import org.apache.fineract.infrastructure.security.exception.OTPDeliveryMethodInvalidException;
import org.apache.fineract.infrastructure.security.exception.OTPTokenInvalidException;
import org.apache.fineract.infrastructure.security.exception.TotpEnrollmentRequiredException;
import org.apache.fineract.infrastructure.security.exception.UserSessionNotFoundException;
import org.apache.fineract.infrastructure.sms.domain.SmsMessage;
import org.apache.fineract.infrastructure.sms.domain.SmsMessageRepository;
import org.apache.fineract.infrastructure.sms.scheduler.SmsMessageScheduledJobService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty("fineract.security.2fa.enabled")
public class TwoFactorServiceImpl implements TwoFactorService {

    private final AccessTokenGenerationService accessTokenGenerationService;
    private final PlatformEmailService emailService;
    private final SmsMessageScheduledJobService smsMessageScheduledJobService;

    private final OTPRequestRepository otpRequestRepository;
    private final TFAccessTokenRepository tfAccessTokenRepository;
    private final SmsMessageRepository smsMessageRepository;

    private final TwoFactorConfigurationService configurationService;
    private final TotpService totpService;
    private final AppUserRepository appUserRepository;
    private final ConfigurationDomainService configurationDomainService;
    private final CacheManager cacheManager;

    @Autowired
    public TwoFactorServiceImpl(AccessTokenGenerationService accessTokenGenerationService, PlatformEmailService emailService,
            SmsMessageScheduledJobService smsMessageScheduledJobService, OTPRequestRepository otpRequestRepository,
            TFAccessTokenRepository tfAccessTokenRepository, SmsMessageRepository smsMessageRepository,
            TwoFactorConfigurationService configurationService, TotpService totpService, AppUserRepository appUserRepository,
            ConfigurationDomainService configurationDomainService, CacheManager cacheManager) {
        this.accessTokenGenerationService = accessTokenGenerationService;
        this.emailService = emailService;
        this.smsMessageScheduledJobService = smsMessageScheduledJobService;
        this.otpRequestRepository = otpRequestRepository;
        this.tfAccessTokenRepository = tfAccessTokenRepository;
        this.smsMessageRepository = smsMessageRepository;
        this.configurationService = configurationService;
        this.totpService = totpService;
        this.appUserRepository = appUserRepository;
        this.configurationDomainService = configurationDomainService;
        this.cacheManager = cacheManager;
    }

    @Override
    public List<OTPDeliveryMethod> getDeliveryMethodsForUser(final AppUser user) {
        List<OTPDeliveryMethod> deliveryMethods = new ArrayList<>();
        final String method = configurationService.getDeliveryMethod();

        if (TwoFactorConstants.SMS_DELIVERY_METHOD_NAME.equals(method)) {
            OTPDeliveryMethod smsMethod = getSMSDeliveryMethodForUser(user);
            if (smsMethod != null) {
                deliveryMethods.add(smsMethod);
            }
        } else if (TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME.equals(method)) {
            OTPDeliveryMethod emailDelivery = getEmailDeliveryMethodForUser(user);
            if (emailDelivery != null) {
                deliveryMethods.add(emailDelivery);
            }
        } else if (TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME.equals(method)) {
            final AppUser managedUser = appUserRepository.findById(user.getId()).orElse(user);
            if (managedUser.isTotpEnabled()) {
                deliveryMethods
                        .add(new OTPDeliveryMethod().setName(TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME).setTarget("Authenticator app"));
            }
        }

        return deliveryMethods;
    }

    @Override
    public OTPRequest createNewOTPToken(final AppUser user, final String deliveryMethodName, final boolean extendedAccessToken) {
        final String configuredMethod = configurationService.getDeliveryMethod();
        if (!configuredMethod.equalsIgnoreCase(deliveryMethodName)) {
            throw new OTPDeliveryMethodInvalidException();
        }

        if (TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME.equalsIgnoreCase(deliveryMethodName)) {
            final AppUser managedUser = appUserRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException(user.getId()));
            if (!managedUser.isTotpEnabled()) {
                throw new TotpEnrollmentRequiredException();
            }
            final OTPDeliveryMethod totpDelivery = new OTPDeliveryMethod().setName(TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME)
                    .setTarget("Authenticator app");
            final OTPRequest request = OTPRequest.create("", 30, extendedAccessToken, totpDelivery);
            otpRequestRepository.addOTPRequest(user, request);
            return request;
        }

        if (TwoFactorConstants.SMS_DELIVERY_METHOD_NAME.equalsIgnoreCase(deliveryMethodName)) {
            OTPDeliveryMethod smsDelivery = getSMSDeliveryMethodForUser(user);
            if (smsDelivery == null) {
                throw new OTPDeliveryMethodInvalidException();
            }
            final OTPRequest request = generateNewToken(smsDelivery, extendedAccessToken);
            final String smsText = configurationService.getFormattedSmsTextFor(user, request);
            SmsMessage smsMessage = SmsMessage.pendingSms(null, null, null, user.getStaff(), smsText, user.getStaff().getMobileNo(), null,
                    false);
            this.smsMessageRepository.save(smsMessage);
            smsMessageScheduledJobService.sendTriggeredMessage(Collections.singleton(smsMessage), configurationService.getSMSProviderId());
            otpRequestRepository.addOTPRequest(user, request);
            return request;
        } else if (TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME.equalsIgnoreCase(deliveryMethodName)) {
            OTPDeliveryMethod emailDelivery = getEmailDeliveryMethodForUser(user);
            if (emailDelivery == null) {
                throw new OTPDeliveryMethodInvalidException();
            }
            final OTPRequest request = generateNewToken(emailDelivery, extendedAccessToken);
            final String emailSubject = configurationService.getFormattedEmailSubjectFor(user, request);
            final String emailBody = configurationService.getFormattedEmailBodyFor(user, request);
            final EmailDetail emailData = new EmailDetail(emailSubject, emailBody, user.getEmail(),
                    user.getFirstname() + " " + user.getLastname());
            emailService.sendDefinedEmail(emailData);
            otpRequestRepository.addOTPRequest(user, request);
            return request;
        }

        throw new OTPDeliveryMethodInvalidException();
    }

    @Override
    @CachePut(value = "userTFAccessToken", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil)"
            + ".getTenant().getTenantIdentifier().concat(#user.username).concat(#result.token + 'tok')")
    public TFAccessToken createAccessTokenFromOTP(final AppUser user, final String otpToken, final String ipAddress,
            final String userAgent) {

        if (configurationService.isTotpDeliveryEnabled()) {
            final AppUser managedUser = appUserRepository.findById(user.getId()).orElseThrow(() -> new UserNotFoundException(user.getId()));
            if (!managedUser.isTotpEnabled()) {
                throw new TotpEnrollmentRequiredException();
            }
            if (!totpService.verifyForUser(managedUser, otpToken)) {
                throw new OTPTokenInvalidException();
            }
            OTPRequest pending = otpRequestRepository.getOTPRequestForUser(user);
            final boolean extended = pending != null && pending.getMetadata().isExtendedAccessToken();
            otpRequestRepository.deleteOTPRequestForUser(user);
            return createAccessToken(user, extended, ipAddress, userAgent);
        }

        OTPRequest otpRequest = otpRequestRepository.getOTPRequestForUser(user);
        if (otpRequest == null || !otpRequest.isValid() || !otpRequest.getToken().equalsIgnoreCase(otpToken)) {
            throw new OTPTokenInvalidException();
        }

        otpRequestRepository.deleteOTPRequestForUser(user);
        return createAccessToken(user, otpRequest.getMetadata().isExtendedAccessToken(), ipAddress, userAgent);
    }

    private TFAccessToken createAccessToken(final AppUser user, final boolean extendedAccessToken, final String ipAddress,
            final String userAgent) {
        String token = accessTokenGenerationService.generateRandomToken();
        int liveTime;
        if (extendedAccessToken) {
            liveTime = configurationService.getAccessTokenExtendedLiveTime();
        } else {
            liveTime = configurationService.getAccessTokenLiveTime();
        }
        TFAccessToken accessToken = TFAccessToken.create(token, user, liveTime).setIpAddress(StringUtils.left(ipAddress, 45))
                .setUserAgent(StringUtils.left(userAgent, 500));
        tfAccessTokenRepository.save(accessToken);
        enforceMaxActiveSessions(user, accessToken);
        return accessToken;
    }

    private void enforceMaxActiveSessions(final AppUser user, final TFAccessToken newToken) {
        final Integer maxActiveSessions = configurationDomainService.retrieveMaxActiveSessions();
        if (maxActiveSessions == null) {
            return;
        }

        final List<TFAccessToken> activeTokens = tfAccessTokenRepository.findByUserAndEnabledTrueOrderByIdDesc(user).stream()
                .filter(activeToken -> !activeToken.getId().equals(newToken.getId())).toList();

        // The new token always survives; older tokens beyond the remaining allowance are revoked
        final int allowedOlderTokens = maxActiveSessions - 1;
        if (activeTokens.size() <= allowedOlderTokens) {
            return;
        }

        final List<TFAccessToken> tokensToRevoke = activeTokens.subList(allowedOlderTokens, activeTokens.size());
        for (final TFAccessToken tokenToRevoke : tokensToRevoke) {
            tokenToRevoke.revoke(TwoFactorConstants.REVOCATION_REASON_SUPERSEDED);
            evictAccessTokenFromCache(user, tokenToRevoke.getToken());
        }
        tfAccessTokenRepository.saveAll(tokensToRevoke);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TFAccessToken> fetchActiveSessionsForUser(final AppUser user) {
        return tfAccessTokenRepository.findByUserAndEnabledTrueOrderByIdDesc(user);
    }

    @Override
    @Transactional
    public TFAccessToken revokeSessionForUser(final Long userId, final Long sessionId) {
        final AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        final TFAccessToken accessToken = tfAccessTokenRepository.findById(sessionId)
                .filter(token -> token.getUser().getId().equals(userId) && token.isEnabled())
                .orElseThrow(() -> new UserSessionNotFoundException(userId, sessionId));

        accessToken.revoke(TwoFactorConstants.REVOCATION_REASON_ADMIN);
        tfAccessTokenRepository.save(accessToken);
        evictAccessTokenFromCache(user, accessToken.getToken());

        return accessToken;
    }

    // The filter validates tokens through the userTFAccessToken cache; a revoked token must be
    // evicted or the kicked device keeps its session until the cache entry expires
    private void evictAccessTokenFromCache(final AppUser user, final String token) {
        final Cache cache = cacheManager.getCache("userTFAccessToken");
        if (cache != null) {
            final String key = ThreadLocalContextUtil.getTenant().getTenantIdentifier().concat(user.getUsername()).concat(token + "tok");
            cache.evict(key);
        }
    }

    @Override
    public void validateTwoFactorAccessToken(AppUser user, String token) {
        TFAccessToken accessToken = fetchAccessTokenForUser(user, token);

        if (accessToken == null || !accessToken.isValid()) {
            throw new AccessTokenInvalidIException();
        }
    }

    @Override
    @CacheEvict(value = "userTFAccessToken", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil)"
            + ".getTenant().getTenantIdentifier().concat(#user.username).concat(#result.token + 'tok')")
    public TFAccessToken invalidateAccessToken(final AppUser user, final JsonCommand command) {

        final String token = command.stringValueOfParameterNamed("token");
        final TFAccessToken accessToken = fetchAccessTokenForUser(user, token);

        if (accessToken == null || !accessToken.isValid()) {
            throw new AccessTokenInvalidIException();
        }

        accessToken.setEnabled(false);
        tfAccessTokenRepository.save(accessToken);

        return accessToken;
    }

    @Override
    @Cacheable(value = "userTFAccessToken", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil)"
            + ".getTenant().getTenantIdentifier().concat(#user.username).concat(#token + 'tok')")
    @Transactional(readOnly = true)
    public TFAccessToken fetchAccessTokenForUser(final AppUser user, final String token) {
        return tfAccessTokenRepository.findByUserAndToken(user, token);
    }

    private OTPDeliveryMethod getSMSDeliveryMethodForUser(final AppUser user) {
        if (!configurationService.isSMSEnabled()) {
            return null;
        }

        if (configurationService.getSMSProviderId() == null) {
            return null;
        }

        if (user.getStaff() == null) {
            return null;
        }
        String mobileNo = user.getStaff().getMobileNo();
        if (StringUtils.isBlank(mobileNo)) {
            return null;
        }

        return new OTPDeliveryMethod().setName(TwoFactorConstants.SMS_DELIVERY_METHOD_NAME).setTarget(mobileNo);
    }

    private OTPDeliveryMethod getEmailDeliveryMethodForUser(final AppUser user) {
        if (!configurationService.isEmailEnabled()) {
            return null;
        }

        return new OTPDeliveryMethod().setName(TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME).setTarget(user.getEmail());
    }

    private OTPRequest generateNewToken(final OTPDeliveryMethod deliveryMethod, final boolean extendedAccessToken) {
        int tokenLiveTime = configurationService.getOTPTokenLiveTime();
        int otpLength = configurationService.getOTPTokenLength();
        String token = new RandomOTPGenerator(otpLength).generate();
        return OTPRequest.create(token, tokenLiveTime, extendedAccessToken, deliveryMethod);
    }
}
