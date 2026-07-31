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

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.common.io.BaseEncoding;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.TotpEnrollmentData;
import org.apache.fineract.infrastructure.security.exception.OTPTokenInvalidException;
import org.apache.fineract.infrastructure.security.exception.TotpAlreadyEnrolledException;
import org.apache.fineract.infrastructure.security.exception.TotpEnrollmentRequiredException;
import org.apache.fineract.infrastructure.security.exception.TotpNotConfiguredException;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty("fineract.security.2fa.enabled")
@RequiredArgsConstructor
public class TotpService {

    private static final Duration TIME_STEP = Duration.ofSeconds(30);
    private static final int DIGITS = 6;
    private static final int WINDOW_STEPS = 1;

    private final TwoFactorConfigurationService configurationService;
    private final PasswordEncryptor passwordEncryptor;
    private final AppUserRepository appUserRepository;
    private final TimeBasedOneTimePasswordGenerator totpGenerator = new TimeBasedOneTimePasswordGenerator(TIME_STEP, DIGITS);

    @Transactional
    public TotpEnrollmentData enroll(final AppUser user) {
        requireTotpDeliveryMethod();
        final AppUser managedUser = loadUser(user.getId());
        if (managedUser.isTotpEnabled()) {
            throw new TotpAlreadyEnrolledException();
        }

        final String secret = generateBase32Secret();
        managedUser.stageTotpSecret(passwordEncryptor.encrypt(secret));
        appUserRepository.save(managedUser);

        return new TotpEnrollmentData().setSecret(secret).setOtpauthUri(buildOtpauthUri(managedUser.getUsername(), secret));
    }

    @Transactional
    public void confirm(final AppUser user, final String token) {
        requireTotpDeliveryMethod();
        final AppUser managedUser = loadUser(user.getId());
        if (StringUtils.isBlank(managedUser.getTotpSecret())) {
            throw new TotpEnrollmentRequiredException();
        }
        if (managedUser.isTotpEnabled()) {
            throw new TotpAlreadyEnrolledException();
        }
        if (!verifyCode(passwordEncryptor.decrypt(managedUser.getTotpSecret()), token)) {
            throw new OTPTokenInvalidException();
        }
        managedUser.confirmTotpEnrollment();
        appUserRepository.save(managedUser);
    }

    private AppUser loadUser(final Long userId) {
        return appUserRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));
    }

    public boolean verifyForUser(final AppUser user, final String token) {
        if (!user.isTotpEnabled() || StringUtils.isBlank(user.getTotpSecret())) {
            return false;
        }
        return verifyCode(passwordEncryptor.decrypt(user.getTotpSecret()), token);
    }

    public boolean verifyCode(final String base32Secret, final String token) {
        if (StringUtils.isBlank(base32Secret) || StringUtils.isBlank(token)) {
            return false;
        }
        try {
            final Key key = toKey(base32Secret);
            final Instant now = Instant.now();
            for (int offset = -WINDOW_STEPS; offset <= WINDOW_STEPS; offset++) {
                final Instant instant = now.plus(TIME_STEP.multipliedBy(offset));
                if (totpGenerator.generateOneTimePasswordString(key, instant).equals(token.trim())) {
                    return true;
                }
            }
            return false;
        } catch (final InvalidKeyException | IllegalArgumentException ex) {
            return false;
        }
    }

    private void requireTotpDeliveryMethod() {
        if (!configurationService.isTotpDeliveryEnabled()) {
            throw new TotpNotConfiguredException();
        }
    }

    private String generateBase32Secret() {
        try {
            final KeyGenerator keyGenerator = KeyGenerator.getInstance(totpGenerator.getAlgorithm());
            final int macLengthInBytes = Mac.getInstance(totpGenerator.getAlgorithm()).getMacLength();
            keyGenerator.init(macLengthInBytes * 8);
            return BaseEncoding.base32().omitPadding().encode(keyGenerator.generateKey().getEncoded());
        } catch (final NoSuchAlgorithmException ex) {
            throw new IllegalStateException("Unable to generate TOTP secret", ex);
        }
    }

    private Key toKey(final String base32Secret) {
        final byte[] decoded = BaseEncoding.base32().omitPadding().decode(base32Secret.replace(" ", "").toUpperCase());
        return new SecretKeySpec(decoded, totpGenerator.getAlgorithm());
    }

    private String buildOtpauthUri(final String username, final String secret) {
        final String issuer = TwoFactorConstants.TOTP_ISSUER;
        final String label = URLEncoder.encode(issuer + ":" + username, StandardCharsets.UTF_8);
        final String issuerParam = URLEncoder.encode(issuer, StandardCharsets.UTF_8);
        return "otpauth://totp/" + label + "?secret=" + secret + "&issuer=" + issuerParam + "&digits=" + DIGITS + "&period="
                + TIME_STEP.getSeconds();
    }
}
