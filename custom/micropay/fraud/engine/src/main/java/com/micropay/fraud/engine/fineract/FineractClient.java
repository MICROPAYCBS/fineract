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
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
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
    public Optional<Long> placeHold(String tenantId, String entityType, Long entityId, String reason, String source,
            String externalReference) {
        if (!isEnabled()) {
            return Optional.empty();
        }
        final Map<String, Object> request = new LinkedHashMap<>();
        request.put("entityType", entityType);
        request.put("entityId", entityId);
        request.put("reason", reason);
        request.put("source", source);
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

    /**
     * One page of a tenant's clients, from {@code GET /clients}.
     *
     * @return the page's clients, empty when there are no more or the call failed
     */
    public List<ClientSummary> listClients(String tenantId, int offset, int limit) {
        if (!isEnabled()) {
            return List.of();
        }
        final Map<String, Object> body = restClient.get()
                .uri("/clients?offset={offset}&limit={limit}&orderBy=id&sortOrder=ASC", offset, limit)
                .header("Fineract-Platform-TenantId", tenantId).retrieve().body(JSON_MAP);
        if (body == null || !(body.get("pageItems") instanceof List<?> items)) {
            return List.of();
        }
        final List<ClientSummary> clients = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> client && client.get("id") instanceof Number id) {
                clients.add(new ClientSummary(id.longValue(), string(client.get("firstname")), string(client.get("middlename")),
                        string(client.get("lastname")), string(client.get("fullname")), string(client.get("displayName")),
                        date(client.get("dateOfBirth")), date(client.get("activationDate")), legalFormId(client.get("legalForm")),
                        client.get("officeId") instanceof Number office ? office.longValue() : null));
            }
        }
        return clients;
    }

    /**
     * The fields of a Fineract client the engine screens.
     */
    public record ClientSummary(Long id, String firstname, String middlename, String lastname, String fullname, String displayName,
            LocalDate dateOfBirth, LocalDate activationDate, Long legalFormId, Long officeId) {
    }

    private static String string(Object value) {
        return value instanceof String s && !s.isBlank() ? s : null;
    }

    /**
     * Fineract writes dates as {@code [yyyy, m, d]} unless a date format is requested; accept that and ISO strings.
     */
    static LocalDate date(Object value) {
        if (value instanceof List<?> parts && parts.size() == 3 && parts.stream().allMatch(Number.class::isInstance)) {
            try {
                return LocalDate.of(((Number) parts.get(0)).intValue(), ((Number) parts.get(1)).intValue(),
                        ((Number) parts.get(2)).intValue());
            } catch (DateTimeException e) {
                return null;
            }
        }
        if (value instanceof String s) {
            try {
                return LocalDate.parse(s);
            } catch (DateTimeParseException e) {
                return null;
            }
        }
        return null;
    }

    private static Long legalFormId(Object value) {
        return value instanceof Map<?, ?> legalForm && legalForm.get("id") instanceof Number id ? id.longValue() : null;
    }
}
