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
package org.apache.fineract.infrastructure.security.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Request and response models for the two-factor resources. Dates are epoch milliseconds because
 * {@code ZonedDateTime} is serialised that way.
 */
final class TwoFactorApiResourceSwagger {

    private TwoFactorApiResourceSwagger() {}

    @Schema(description = "One way this user can receive a one-time password")
    public static final class GetTwoFactorDeliveryMethodResponse {

        private GetTwoFactorDeliveryMethodResponse() {}

        @Schema(description = "Delivery method name", example = "email", allowableValues = { "email", "sms", "totp" })
        public String name;
        @Schema(description = "Masked destination, such as an email address or mobile number. Empty for totp.", example = "a***@example.com")
        public String target;
    }

    @Schema(description = "Metadata for a newly requested one-time password. The password itself is not returned.")
    public static final class PostTwoFactorResponse {

        private PostTwoFactorResponse() {}

        @Schema(description = "When the one-time password was created, as epoch milliseconds", example = "1710000000000")
        public Long requestTime;
        @Schema(description = "How long the one-time password stays valid, in seconds", example = "300")
        public Integer tokenLiveTimeInSec;
        @Schema(description = "True when the caller asked for an extended access-token lifetime", example = "false")
        public Boolean extendedAccessToken;
        public GetTwoFactorDeliveryMethodResponse deliveryMethod;
    }

    @Schema(description = "Access token returned by POST /v1/twofactor/validate")
    public static final class PostTwoFactorValidateResponse {

        private PostTwoFactorValidateResponse() {}

        @Schema(description = "Value to send as the Fineract-Platform-TFA-Token header", example = "a1b2c3d4e5")
        public String token;
        @Schema(description = "Token valid-from instant, as epoch milliseconds", example = "1710000000000")
        public Long validFrom;
        @Schema(description = "Token valid-to instant, as epoch milliseconds", example = "1710003600000")
        public Long validTo;
    }

    @Schema(description = "Invalidate the caller's current two-factor access token")
    public static final class PostTwoFactorInvalidateRequest {

        private PostTwoFactorInvalidateRequest() {}

        @Schema(description = "Access token to invalidate", example = "a1b2c3d4e5", requiredMode = Schema.RequiredMode.REQUIRED)
        public String token;
    }

    @Schema(description = "Command result after the access token is invalidated")
    public static final class PostTwoFactorInvalidateResponse {

        private PostTwoFactorInvalidateResponse() {}

        @Schema(description = "Maker-checker command id. Present when the command was logged.", example = "12")
        public Long commandId;
        @Schema(description = "The invalidated token", example = "a1b2c3d4e5")
        public String resourceIdentifier;
        @Schema(description = "True when maker-checker held the command and rolled the transaction back", example = "true")
        public Boolean rollbackTransaction;
    }

    @Schema(description = "TOTP enrollment material. The secret is shown once.")
    public static final class PostTwoFactorTotpEnrollResponse {

        private PostTwoFactorTotpEnrollResponse() {}

        @Schema(description = "Base32 TOTP secret to store in an authenticator app", example = "JBSWY3DPEHPK3PXP")
        public String secret;
        @Schema(description = "otpauth URI for a QR code", example = "otpauth://totp/Micropay:mifos?secret=JBSWY3DPEHPK3PXP&issuer=Micropay")
        public String otpauthUri;
    }

    @Schema(description = "Result of confirming a staged TOTP secret")
    public static final class PostTwoFactorTotpConfirmResponse {

        private PostTwoFactorTotpConfirmResponse() {}

        @Schema(description = "True after the authenticator code is accepted and TOTP is enabled", example = "true")
        public Boolean totpEnabled;
    }

    @Schema(description = "Two-factor configuration. Property names match the stored configuration keys.")
    public static final class GetTwoFactorConfigurationResponse {

        private GetTwoFactorConfigurationResponse() {}

        @Schema(name = "otp-delivery-email-enable", description = "Enable email delivery of one-time passwords", example = "true")
        public Boolean otpDeliveryEmailEnable;
        @Schema(name = "otp-delivery-email-subject", description = "Subject template for the email one-time password", example = "Your Micropay code")
        public String otpDeliveryEmailSubject;
        @Schema(name = "otp-delivery-email-body", description = "Body template for the email one-time password", example = "Your code is {{token}}")
        public String otpDeliveryEmailBody;
        @Schema(name = "otp-delivery-sms-enable", description = "Enable SMS delivery of one-time passwords", example = "false")
        public Boolean otpDeliverySmsEnable;
        @Schema(name = "otp-delivery-sms-provider", description = "SMS provider id", example = "1")
        public Long otpDeliverySmsProvider;
        @Schema(name = "otp-delivery-sms-text", description = "SMS message template", example = "Your code is {{token}}")
        public String otpDeliverySmsText;
        @Schema(name = "otp-delivery-method", description = "Default delivery method", example = "email", allowableValues = { "email", "sms",
                "totp" })
        public String otpDeliveryMethod;
        @Schema(name = "otp-token-live-time", description = "One-time password lifetime in seconds", example = "300")
        public Integer otpTokenLiveTime;
        @Schema(name = "otp-token-length", description = "Number of digits in the one-time password", example = "6")
        public Integer otpTokenLength;
        @Schema(name = "access-token-live-time", description = "Two-factor access token lifetime in seconds", example = "3600")
        public Integer accessTokenLiveTime;
        @Schema(name = "access-token-live-time-extended", description = "Extended access token lifetime in seconds", example = "86400")
        public Integer accessTokenLiveTimeExtended;
    }

    @Schema(description = "Partial update of two-factor configuration. Send only the keys to change.")
    public static final class PutTwoFactorConfigurationRequest {

        private PutTwoFactorConfigurationRequest() {}

        @Schema(name = "otp-delivery-email-enable", description = "Enable email delivery of one-time passwords", example = "true")
        public Boolean otpDeliveryEmailEnable;
        @Schema(name = "otp-delivery-email-subject", description = "Subject template for the email one-time password", example = "Your Micropay code")
        public String otpDeliveryEmailSubject;
        @Schema(name = "otp-delivery-email-body", description = "Body template for the email one-time password", example = "Your code is {{token}}")
        public String otpDeliveryEmailBody;
        @Schema(name = "otp-delivery-sms-enable", description = "Enable SMS delivery of one-time passwords", example = "false")
        public Boolean otpDeliverySmsEnable;
        @Schema(name = "otp-delivery-sms-provider", description = "SMS provider id", example = "1")
        public Long otpDeliverySmsProvider;
        @Schema(name = "otp-delivery-sms-text", description = "SMS message template", example = "Your code is {{token}}")
        public String otpDeliverySmsText;
        @Schema(name = "otp-delivery-method", description = "Default delivery method", example = "email", allowableValues = { "email", "sms",
                "totp" })
        public String otpDeliveryMethod;
        @Schema(name = "otp-token-live-time", description = "One-time password lifetime in seconds", example = "300")
        public Integer otpTokenLiveTime;
        @Schema(name = "otp-token-length", description = "Number of digits in the one-time password", example = "6")
        public Integer otpTokenLength;
        @Schema(name = "access-token-live-time", description = "Two-factor access token lifetime in seconds", example = "3600")
        public Integer accessTokenLiveTime;
        @Schema(name = "access-token-live-time-extended", description = "Extended access token lifetime in seconds", example = "86400")
        public Integer accessTokenLiveTimeExtended;
    }

    @Schema(description = "Command result after updating two-factor configuration")
    public static final class PutTwoFactorConfigurationResponse {

        private PutTwoFactorConfigurationResponse() {}

        @Schema(example = "12")
        public Long commandId;
        @Schema(description = "Keys that changed")
        public PutTwoFactorConfigurationRequest changes;
        @Schema(description = "True when maker-checker held the command and rolled the transaction back", example = "true")
        public Boolean rollbackTransaction;
    }
}
