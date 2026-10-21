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
package com.micropay.fineract.integrationtests;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.PostClientsIdentifier;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.util.JSON;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.Utils;

/**
 * Identifier-aware wrappers around upstream {@link ClientHelper}. Micropay rejects a person client without at least one
 * identifier, so fork tests attach one that matches an active identity type.
 */
@Slf4j
public final class MicropayClientHelper {

    private static final Gson GSON = new JSON().getGson();
    private static final String IDENTITY_TYPES_URL = "/fineract-provider/api/v1/identitytypes?" + Utils.TENANT_IDENTIFIER;
    private static final String IDENTIFIER_PROBE = "AB1234CD5";
    private static volatile Long defaultIdentifierDocumentTypeId;
    private static volatile Pattern defaultIdentifierPattern;

    private MicropayClientHelper() {}

    public static PostClientsResponse createClient(final PostClientsRequest request) {
        return ClientHelper.createClient(withDefaultIdentifierIfPerson(request));
    }

    public static Integer createClient(final RequestSpecification requestSpec,
            final io.restassured.specification.ResponseSpecification responseSpec, final PostClientsRequest request) {
        return ClientHelper.createClient(requestSpec, responseSpec, withDefaultIdentifierIfPerson(request));
    }

    public static Long defaultIdentifierDocumentTypeId() {
        ensureDefaultIdentityType();
        return defaultIdentifierDocumentTypeId;
    }

    public static String randomIdentifierKey() {
        ensureDefaultIdentityType();
        for (int attempt = 0; attempt < 100; attempt++) {
            final String candidate = Utils.uniqueRandomStringGenerator("", 9).toUpperCase();
            if (defaultIdentifierPattern == null || defaultIdentifierPattern.matcher(candidate).matches()) {
                return candidate;
            }
        }
        throw new IllegalStateException("Could not generate an identifier key matching " + defaultIdentifierPattern);
    }

    public static PostClientsIdentifier defaultClientIdentifier() {
        return new PostClientsIdentifier().documentTypeId(defaultIdentifierDocumentTypeId()).documentKey(randomIdentifierKey())
                .status("Active").description("integration-test identifier");
    }

    public static HashMap<String, Object> defaultClientIdentifierAsMap() {
        final HashMap<String, Object> identifier = new HashMap<>();
        identifier.put("documentTypeId", defaultIdentifierDocumentTypeId());
        identifier.put("documentKey", randomIdentifierKey());
        identifier.put("status", "Active");
        identifier.put("description", "integration-test identifier");
        return identifier;
    }

    public static PostClientsRequest withDefaultIdentifierIfPerson(final PostClientsRequest request) {
        if (request != null && ClientHelper.LEGALFORM_ID_PERSON.equals(request.getLegalFormId())
                && (request.getClientIdentifiers() == null || request.getClientIdentifiers().isEmpty())) {
            request.clientIdentifiers(new ArrayList<>(List.of(defaultClientIdentifier())));
        }
        return request;
    }

    public static String clientIdentifiersJson() {
        return "\"clientIdentifiers\": [{ \"documentTypeId\": " + defaultIdentifierDocumentTypeId() + ", \"documentKey\": \""
                + randomIdentifierKey() + "\", \"status\": \"Active\", \"description\": \"integration-test identifier\" }]";
    }

    private static synchronized void ensureDefaultIdentityType() {
        if (defaultIdentifierDocumentTypeId != null) {
            return;
        }
        final RequestSpecification requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON)
                .addHeader("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey()).build();
        final String response = Utils.performServerGet(requestSpec, new ResponseSpecBuilder().expectStatusCode(200).build(),
                IDENTITY_TYPES_URL, null);
        final List<Map<String, Object>> identityTypes = GSON.fromJson(response, new TypeToken<List<Map<String, Object>>>() {}.getType());
        Map<String, Object> chosen = null;
        Pattern chosenPattern = null;
        for (final Map<String, Object> identityType : identityTypes) {
            if (!"ACTIVE".equalsIgnoreCase(String.valueOf(identityType.get("status")))) {
                continue;
            }
            final Object regex = identityType.get("validationRegex");
            if (regex == null || String.valueOf(regex).isBlank()) {
                chosen = identityType;
                chosenPattern = null;
                break;
            }
            final Pattern pattern = Pattern.compile(String.valueOf(regex));
            if (pattern.matcher(IDENTIFIER_PROBE).matches()) {
                chosen = identityType;
                chosenPattern = pattern;
                break;
            }
        }
        if (chosen == null) {
            throw new IllegalStateException("No ACTIVE identity type accepts a 9-character alphanumeric key; seed one via /identitytypes "
                    + "(found: " + identityTypes + ")");
        }
        defaultIdentifierPattern = chosenPattern;
        defaultIdentifierDocumentTypeId = ((Number) chosen.get("codeValueId")).longValue();
        log.info("Default client identifier type for Micropay integration tests: {} (codeValueId {})", chosen.get("codeValueName"),
                defaultIdentifierDocumentTypeId);
    }
}
