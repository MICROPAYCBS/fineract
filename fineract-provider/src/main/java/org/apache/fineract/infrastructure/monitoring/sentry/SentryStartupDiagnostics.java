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

import io.sentry.Sentry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class SentryStartupDiagnostics implements InitializingBean {

    @Value("${sentry.dsn:}")
    private String configuredDsn;

    @Override
    public void afterPropertiesSet() {
        boolean dsnConfigured = configuredDsn != null && !configuredDsn.isBlank();
        if (!dsnConfigured) {
            log.info("Sentry is disabled (sentry.dsn / SENTRY_DSN is not configured)");
            return;
        }
        log.info("Sentry DSN is configured; SDK enabled={}", Sentry.isEnabled());
        if (!Sentry.isEnabled()) {
            log.warn("Sentry DSN is set but Sentry.isEnabled() is false — check SENTRY_ENABLED and startup logs");
        }
    }
}
