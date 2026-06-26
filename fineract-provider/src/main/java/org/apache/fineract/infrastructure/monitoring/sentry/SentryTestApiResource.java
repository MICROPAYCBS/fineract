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
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Temporary diagnostic endpoint to verify the Jersey → Sentry reporting path.
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

    @Override
    @SuppressFBWarnings("SLF4J_SIGN_ONLY_FORMAT")
    public void afterPropertiesSet() {
        log.warn("------------------------------------------------------------");
        log.warn("Sentry test endpoint ENABLED at GET /api/v1/diagnostics/sentry-test");
        log.warn("DO NOT enable FINERACT_SENTRY_TEST_ENDPOINT_ENABLED in production!");
        log.warn("------------------------------------------------------------");
    }

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Trigger or inspect the Sentry test endpoint", hidden = true)
    public Map<String, Object> sentryTest(@QueryParam("dryRun") @DefaultValue("false") boolean dryRun) {
        if (dryRun) {
            return Map.of("status", "ready", "sentryEnabled", Sentry.isEnabled(), "message",
                    "Call without dryRun=true to emit a test 500 to Sentry");
        }
        throw new RuntimeException(TEST_ERROR_MESSAGE);
    }
}
