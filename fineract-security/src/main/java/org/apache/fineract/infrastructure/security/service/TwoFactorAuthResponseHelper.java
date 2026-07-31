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

import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.AuthenticatedOauthUserData;
import org.apache.fineract.infrastructure.security.data.AuthenticatedUserData;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

@Component
public class TwoFactorAuthResponseHelper {

    private final ObjectProvider<TwoFactorConfigurationService> twoFactorConfigurationService;

    public TwoFactorAuthResponseHelper(final ObjectProvider<TwoFactorConfigurationService> twoFactorConfigurationService) {
        this.twoFactorConfigurationService = twoFactorConfigurationService;
    }

    public AuthenticatedUserData apply(final AuthenticatedUserData data, final AppUser user, final boolean twoFactorRequired) {
        data.setTwoFactorAuthenticationRequired(twoFactorRequired);
        data.setTotpEnabled(user.isTotpEnabled());
        if (!twoFactorRequired) {
            data.setDeliveryMethod(null);
            data.setTotpEnrollmentRequired(false);
            return data;
        }
        final TwoFactorConfigurationService config = twoFactorConfigurationService.getIfAvailable();
        if (config == null) {
            data.setDeliveryMethod(null);
            data.setTotpEnrollmentRequired(false);
            return data;
        }
        final String deliveryMethod = config.getDeliveryMethod();
        data.setDeliveryMethod(deliveryMethod);
        data.setTotpEnrollmentRequired(
                TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME.equals(deliveryMethod) && !user.isTotpEnabled());
        return data;
    }

    public AuthenticatedOauthUserData apply(final AuthenticatedOauthUserData data, final AppUser user, final boolean twoFactorRequired) {
        data.setTwoFactorAuthenticationRequired(twoFactorRequired);
        data.setTotpEnabled(user.isTotpEnabled());
        if (!twoFactorRequired) {
            data.setDeliveryMethod(null);
            data.setTotpEnrollmentRequired(false);
            return data;
        }
        final TwoFactorConfigurationService config = twoFactorConfigurationService.getIfAvailable();
        if (config == null) {
            data.setDeliveryMethod(null);
            data.setTotpEnrollmentRequired(false);
            return data;
        }
        final String deliveryMethod = config.getDeliveryMethod();
        data.setDeliveryMethod(deliveryMethod);
        data.setTotpEnrollmentRequired(
                TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME.equals(deliveryMethod) && !user.isTotpEnabled());
        return data;
    }
}
