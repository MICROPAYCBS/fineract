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
package com.micropay.fraud.engine.fineract;

import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.event.AccountType;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Calls Fineract's REST API with a service user: to find the client behind an account, and to place fraud holds
 * (custom/micropay/fraud, POST /fraud/holds). Disabled unless {@code fraud.fineract.enabled=true}.
 */
@Slf4j
@Component
public class FineractClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP = new ParameterizedTypeReference<>() {};

    private final FraudEngineProperties.Fineract config;
    private final RestClient restClient;

    public FineractClient(FraudEngineProperties properties) {
        this.config = properties.fineract();
        final String credentials = config.username() + ":" + config.password();
        this.restClient = RestClient.builder().baseUrl(config.baseUrl())
                .defaultHeader(HttpHeaders.AUTHORIZATION,
                        "Basic " + Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8)))
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE).build();
    }

    public boolean isEnabled() {
        return config.enabled();
    }

    public Optional<Long> findClientId(String tenantId, AccountType accountType, Long accountId) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        final String path = accountType == AccountType.SAVINGS ? "/savingsaccounts/{id}" : "/loans/{id}";
        try {
            final Map<String, Object> body = restClient.get().uri(path + "?fields=clientId", accountId)
                    .header("Fineract-Platform-TenantId", tenantId).retrieve().body(JSON_MAP);
            return Optional.ofNullable(body).map(b -> b.get("clientId")).filter(Number.class::isInstance)
                    .map(n -> ((Number) n).longValue());
        } catch (RestClientException e) {
            log.warn("Could not look up the client of {} account {} in tenant {}: {}", accountType, accountId, tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * @return the new hold's id, or empty when Fineract refused (for example an active hold already exists)
     */
    public Optional<Long> placeHold(String tenantId, String entityType, Long entityId, String reason, String externalReference) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("entityType", entityType);
        request.put("entityId", entityId);
        request.put("reason", reason);
        request.put("source", "FRAUD_ENGINE");
        request.put("externalReference", externalReference);
        try {
            final Map<String, Object> body = restClient.post().uri("/fraud/holds").header("Fineract-Platform-TenantId", tenantId)
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve().body(JSON_MAP);
            return Optional.ofNullable(body).map(b -> b.get("resourceId")).filter(Number.class::isInstance)
                    .map(n -> ((Number) n).longValue());
        } catch (RestClientException e) {
            log.warn("Could not place a fraud hold on {} {} in tenant {}: {}", entityType, entityId, tenantId, e.getMessage());
            return Optional.empty();
        }
    }
}
