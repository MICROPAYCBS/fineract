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
package org.apache.fineract.infrastructure.monitoring.sentry;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.sentry.Sentry;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Temporary diagnostic endpoint to verify Sentry ingestion.
 *
 * <p>Enable with {@code FINERACT_SENTRY_TEST_ENDPOINT_ENABLED=true}. Remove this class when no longer needed.
 */
@Component
@ConditionalOnProperty(name = "fineract.monitoring.sentry.test-endpoint.enabled", havingValue = "true")
@Path("/v1/diagnostics/sentry-test")
@Slf4j
@Hidden
public class SentryTestApiResource implements InitializingBean {

    static final String TEST_ERROR_MESSAGE = "Fineract Sentry test: intentional server error (safe to ignore)";

    @Value("${sentry.dsn:}")
    private String configuredDsn;

    @Override
    @SuppressFBWarnings("SLF4J_SIGN_ONLY_FORMAT")
    public void afterPropertiesSet() {
        log.warn("------------------------------------------------------------");
        log.warn("Sentry test endpoint ENABLED at GET /api/v1/diagnostics/sentry-test");
        log.warn("Use ?dryRun=true or ?captureOnly=true before the default 500 test");
        log.warn("DO NOT enable FINERACT_SENTRY_TEST_ENDPOINT_ENABLED in production!");
        log.warn("------------------------------------------------------------");
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Trigger or inspect the Sentry test endpoint", hidden = true)
    public Map<String, Object> sentryTest(@QueryParam("dryRun") @DefaultValue("false") boolean dryRun,
            @QueryParam("captureOnly") @DefaultValue("false") boolean captureOnly) {
        if (dryRun) {
            return statusResponse(null);
        }

        RuntimeException error = new RuntimeException(TEST_ERROR_MESSAGE);
        if (captureOnly) {
            String eventId = FineractSentrySupport.captureServerError(error, 500, "GET", "/v1/diagnostics/sentry-test");
            Sentry.flush(3000);
            return statusResponse(eventId);
        }

        throw error;
    }

    private Map<String, Object> statusResponse(String eventId) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", eventId == null ? "ready" : "captured");
        response.put("sentrySdkEnabled", Sentry.isEnabled());
        response.put("dsnConfigured", configuredDsn != null && !configuredDsn.isBlank());
        response.put("eventId", eventId);
        response.put("message",
                "Errors appear under Sentry Issues (not Logs). Use ?captureOnly=true for a safe 200 test, or call without params for HTTP 500.");
        return response;
    }
}
