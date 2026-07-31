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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.eatthepath.otp.TimeBasedOneTimePasswordGenerator;
import com.google.common.io.BaseEncoding;
import java.security.Key;
import java.time.Duration;
import java.time.Instant;
import javax.crypto.KeyGenerator;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TotpServiceTest {

    private TotpService totpService;

    @BeforeEach
    void setUp() {
        totpService = new TotpService(mock(TwoFactorConfigurationService.class), mock(PasswordEncryptor.class),
                mock(AppUserRepository.class));
    }

    @Test
    void verifyCodeAcceptsCurrentWindow() throws Exception {
        final TimeBasedOneTimePasswordGenerator generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
        final KeyGenerator keyGenerator = KeyGenerator.getInstance(generator.getAlgorithm());
        keyGenerator.init(Mac.getInstance(generator.getAlgorithm()).getMacLength() * 8);
        final Key key = keyGenerator.generateKey();
        final String secret = BaseEncoding.base32().omitPadding().encode(key.getEncoded());
        final String code = generator.generateOneTimePasswordString(key, Instant.now());

        assertTrue(totpService.verifyCode(secret, code));
    }

    @Test
    void verifyCodeRejectsInvalidToken() throws Exception {
        final KeyGenerator keyGenerator = KeyGenerator.getInstance("HmacSHA1");
        keyGenerator.init(160);
        final String secret = BaseEncoding.base32().omitPadding().encode(keyGenerator.generateKey().getEncoded());

        assertFalse(totpService.verifyCode(secret, "000000"));
        assertFalse(totpService.verifyCode(secret, null));
        assertFalse(totpService.verifyCode(null, "123456"));
    }

    @Test
    void verifyCodeAcceptsAdjacentWindow() throws Exception {
        final TimeBasedOneTimePasswordGenerator generator = new TimeBasedOneTimePasswordGenerator(Duration.ofSeconds(30), 6);
        final KeyGenerator keyGenerator = KeyGenerator.getInstance(generator.getAlgorithm());
        keyGenerator.init(Mac.getInstance(generator.getAlgorithm()).getMacLength() * 8);
        final byte[] encoded = keyGenerator.generateKey().getEncoded();
        final Key key = new SecretKeySpec(encoded, generator.getAlgorithm());
        final String secret = BaseEncoding.base32().omitPadding().encode(encoded);
        final String previous = generator.generateOneTimePasswordString(key, Instant.now().minus(Duration.ofSeconds(30)));

        assertTrue(totpService.verifyCode(secret, previous));
    }
}
