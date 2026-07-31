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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import java.io.StringReader;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConfigurationConstants;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.domain.TwoFactorConfiguration;
import org.apache.fineract.infrastructure.security.domain.TwoFactorConfigurationRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty("fineract.security.2fa.enabled")
public class TwoFactorConfigurationServiceImpl implements TwoFactorConfigurationService {

    private static final String DEFAULT_EMAIL_SUBJECT = "Fineract Two-Factor Authentication Token";
    private static final String DEFAULT_EMAIL_BODY = "Hello {username}.\n" + "Your OTP login token is {token}.";
    private static final String DEFAULT_SMS_TEXT = "Your authentication token for Fineract is " + "{token}.";

    private final TwoFactorConfigurationRepository configurationRepository;

    @Autowired
    public TwoFactorConfigurationServiceImpl(TwoFactorConfigurationRepository configurationRepository) {
        this.configurationRepository = configurationRepository;
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()")
    public Map<String, Object> retrieveAll() {
        List<TwoFactorConfiguration> configurationList = configurationRepository.findAll();
        Map<String, Object> configurationMap = new HashMap<>();
        for (final TwoFactorConfiguration configuration : configurationList) {
            configurationMap.put(configuration.getName(), configuration.getObjectValue());
        }
        return configurationMap;
    }

    @Override
    @CacheEvict(value = "tfConfig", allEntries = true)
    public Map<String, Object> update(JsonCommand command) {
        Map<String, Object> actualChanges = new HashMap<>();

        if (command.parameterExists(TwoFactorConfigurationConstants.DELIVERY_METHOD)) {
            final String newMethod = command.stringValueOfParameterNamed(TwoFactorConfigurationConstants.DELIVERY_METHOD).trim()
                    .toLowerCase();
            applyDeliveryMethod(newMethod, actualChanges);
        }

        for (final String parameterName : TwoFactorConfigurationConstants.BOOLEAN_PARAMETERS) {
            if (TwoFactorConfigurationConstants.ENABLE_EMAIL_DELIVERY.equals(parameterName)
                    || TwoFactorConfigurationConstants.ENABLE_SMS_DELIVERY.equals(parameterName)) {
                // Delivery enable flags are derived from otp-delivery-method; ignore direct updates.
                continue;
            }
            TwoFactorConfiguration configuration = configurationRepository.findByName(parameterName);
            if (configuration == null) {
                continue;
            }

            if (command.isChangeInBooleanParameterNamed(parameterName, BooleanUtils.toBooleanObject(configuration.getValue()))) {
                final boolean newValue = command.booleanPrimitiveValueOfParameterNamed(parameterName);
                actualChanges.put(parameterName, newValue);
                configuration.setValue(String.valueOf(newValue));
                configurationRepository.save(configuration);
            }
        }

        for (final String parameterName : TwoFactorConfigurationConstants.STRING_PARAMETERS) {
            if (TwoFactorConfigurationConstants.DELIVERY_METHOD.equals(parameterName)) {
                continue;
            }
            TwoFactorConfiguration configuration = configurationRepository.findByName(parameterName);
            if (configuration == null) {
                continue;
            }

            if (command.isChangeInStringParameterNamed(parameterName, configuration.getValue())) {
                final String newValue = command.stringValueOfParameterNamed(parameterName).trim();
                actualChanges.put(parameterName, newValue);
                configuration.setValue(newValue);
                configurationRepository.save(configuration);
            }
        }

        for (final String parameterName : TwoFactorConfigurationConstants.NUMBER_PARAMETERS) {
            TwoFactorConfiguration configuration = configurationRepository.findByName(parameterName);
            if (configuration == null) {
                continue;
            }

            if (command.isChangeInIntegerSansLocaleParameterNamed(parameterName, NumberUtils.createInteger(configuration.getValue()))) {
                final Long newValue = command.longValueOfParameterNamed(parameterName);
                actualChanges.put(parameterName, newValue);
                configuration.setValue(String.valueOf(newValue));
                configurationRepository.save(configuration);
            }
        }

        if (!actualChanges.isEmpty()) {
            configurationRepository.flush();
        }

        return actualChanges;
    }

    private void applyDeliveryMethod(final String deliveryMethod, final Map<String, Object> actualChanges) {
        TwoFactorConfiguration methodConfig = configurationRepository.findByName(TwoFactorConfigurationConstants.DELIVERY_METHOD);
        if (methodConfig == null) {
            methodConfig = new TwoFactorConfiguration();
            methodConfig.setName(TwoFactorConfigurationConstants.DELIVERY_METHOD);
        }
        if (!deliveryMethod.equalsIgnoreCase(methodConfig.getValue())) {
            actualChanges.put(TwoFactorConfigurationConstants.DELIVERY_METHOD, deliveryMethod);
            methodConfig.setValue(deliveryMethod);
            configurationRepository.save(methodConfig);
        }

        final boolean emailEnabled = TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME.equals(deliveryMethod);
        final boolean smsEnabled = TwoFactorConstants.SMS_DELIVERY_METHOD_NAME.equals(deliveryMethod);
        syncBooleanConfig(TwoFactorConfigurationConstants.ENABLE_EMAIL_DELIVERY, emailEnabled, actualChanges);
        syncBooleanConfig(TwoFactorConfigurationConstants.ENABLE_SMS_DELIVERY, smsEnabled, actualChanges);
    }

    private void syncBooleanConfig(final String name, final boolean value, final Map<String, Object> actualChanges) {
        TwoFactorConfiguration configuration = configurationRepository.findByName(name);
        if (configuration == null) {
            return;
        }
        final Boolean current = BooleanUtils.toBooleanObject(configuration.getValue());
        if (current == null || current.booleanValue() != value) {
            actualChanges.put(name, value);
            configuration.setValue(String.valueOf(value));
            configurationRepository.save(configuration);
        }
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|smsEnabled'")
    public boolean isSMSEnabled() {
        return TwoFactorConstants.SMS_DELIVERY_METHOD_NAME.equals(getDeliveryMethod());
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|smsProvider'")
    public Integer getSMSProviderId() {
        Integer value = getIntegerConfig(TwoFactorConfigurationConstants.SMS_PROVIDER_ID, null);
        if (value == null || value < 1) {
            return null;
        }
        return value;
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|smsText'")
    public String getSmsText() {
        return getStringConfig(TwoFactorConfigurationConstants.SMS_MESSAGE_TEXT, DEFAULT_SMS_TEXT);
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|emailEnabled'")
    public boolean isEmailEnabled() {
        return TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME.equals(getDeliveryMethod());
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|totpEnabled'")
    public boolean isTotpDeliveryEnabled() {
        return TwoFactorConstants.TOTP_DELIVERY_METHOD_NAME.equals(getDeliveryMethod());
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|deliveryMethod'")
    public String getDeliveryMethod() {
        return getStringConfig(TwoFactorConfigurationConstants.DELIVERY_METHOD, TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME)
                .trim().toLowerCase();
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|emailSubject'")
    public String getEmailSubject() {
        return getStringConfig(TwoFactorConfigurationConstants.EMAIL_SUBJECT, DEFAULT_EMAIL_SUBJECT);
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|emailBody'")
    public String getEmailBody() {
        return getStringConfig(TwoFactorConfigurationConstants.EMAIL_BODY, DEFAULT_EMAIL_BODY);
    }

    @Override
    public String getFormattedEmailSubjectFor(AppUser user, OTPRequest request) {
        final Map<String, Object> templateData = processTemplateDataFor(user, request);
        return compileTextTemplate(getEmailSubject(), TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME, templateData);
    }

    @Override
    public String getFormattedEmailBodyFor(AppUser user, OTPRequest request) {
        final Map<String, Object> templateData = processTemplateDataFor(user, request);
        return compileTextTemplate(getEmailBody(), TwoFactorConstants.EMAIL_DELIVERY_METHOD_NAME, templateData);
    }

    @Override
    public String getFormattedSmsTextFor(AppUser user, OTPRequest request) {
        final Map<String, Object> templateData = processTemplateDataFor(user, request);
        return compileTextTemplate(getSmsText(), TwoFactorConstants.SMS_DELIVERY_METHOD_NAME, templateData);
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|otpLength'")
    public Integer getOTPTokenLength() {
        Integer defaultValue = 1;
        return getIntegerConfig(TwoFactorConfigurationConstants.OTP_TOKEN_LENGTH, defaultValue);
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|otpTime'")
    public Integer getOTPTokenLiveTime() {
        Integer defaultValue = 300;
        Integer value = getIntegerConfig(TwoFactorConfigurationConstants.OTP_TOKEN_LIVE_TIME, defaultValue);
        if (value < 1) {
            return defaultValue;
        }
        return value;
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|tokenTime'")
    public Integer getAccessTokenLiveTime() {
        Integer defaultValue = 86400;
        Integer value = getIntegerConfig(TwoFactorConfigurationConstants.ACCESS_TOKEN_LIVE_TIME, defaultValue);
        if (value < 1) {
            return defaultValue;
        }
        return value;
    }

    @Override
    @Cacheable(value = "tfConfig", key = "T(org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil).getTenant().getTenantIdentifier()+'|tokenExtendedTime'")
    public Integer getAccessTokenExtendedLiveTime() {
        Integer defaultValue = 604800;
        Integer value = getIntegerConfig(TwoFactorConfigurationConstants.ACCESS_TOKEN_LIVE_TIME_EXTENDED, defaultValue);
        if (value < 1) {
            return defaultValue;
        }
        return value;
    }

    private boolean getBooleanConfig(final String name, final boolean defaultValue) {
        final TwoFactorConfiguration configuration = configurationRepository.findByName(name);
        Boolean value = BooleanUtils.toBooleanObject(configuration.getValue());
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private String getStringConfig(final String name, final String defaultValue) {
        final TwoFactorConfiguration configuration = configurationRepository.findByName(name);
        if (configuration == null || configuration.getValue() == null) {
            return defaultValue;
        }
        return configuration.getValue();
    }

    private Integer getIntegerConfig(final String name, final Integer defaultValue) {
        final TwoFactorConfiguration configuration = configurationRepository.findByName(name);
        Integer value = NumberUtils.createInteger(configuration.getValue());
        if (value == null) {
            return defaultValue;
        }
        return value;
    }

    private Map<String, Object> processTemplateDataFor(AppUser user, OTPRequest request) {
        Map<String, Object> templateData = new HashMap<>();

        templateData.put("username", user.getUsername());
        templateData.put("email", user.getEmail());
        templateData.put("firstname", user.getFirstname());
        templateData.put("lastname", user.getLastname());
        if (user.getStaff() != null && user.getStaff().getMobileNo() != null) {
            templateData.put("mobileno", user.getStaff().getMobileNo());
        }

        templateData.put("token", request.getToken());
        templateData.put("tokenlivetime", request.getMetadata().getTokenLiveTimeInSec());

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy");

        final LocalDateTime requestTime = request.getMetadata().getRequestTime().toLocalDateTime();
        final LocalDateTime expireTime = requestTime.plusSeconds(request.getMetadata().getTokenLiveTimeInSec());

        templateData.put("requestdate", requestTime.toLocalDate().format(dateFormatter));
        templateData.put("requesttime", requestTime.toLocalTime().format(timeFormatter));

        templateData.put("expiredate", expireTime.toLocalDate().format(dateFormatter));
        templateData.put("expiretime", expireTime.toLocalTime().format(timeFormatter));

        return templateData;
    }

    private String compileTextTemplate(final String template, final String name, final Map<String, Object> params) {
        final MustacheFactory mf = new DefaultMustacheFactory();
        final Mustache mustache = mf.compile(new StringReader(template), name);

        final StringWriter stringWriter = new StringWriter();
        mustache.execute(stringWriter, params);

        return stringWriter.toString();
    }
}
