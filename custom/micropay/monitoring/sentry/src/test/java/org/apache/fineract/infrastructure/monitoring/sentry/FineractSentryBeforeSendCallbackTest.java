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

import io.sentry.SentryEvent;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class FineractSentryBeforeSendCallbackTest {

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
        MDC.clear();
    }

    @Test
    void enrichEventAddsTenantAndCorrelationTags() {
        ThreadLocalContextUtil.setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", null));
        MDC.put("correlationId", "corr-123");

        SentryEvent event = new SentryEvent();
        new FineractSentryBeforeSendCallback().execute(event, null);

        Assertions.assertEquals("default", event.getTag("tenant"));
        Assertions.assertEquals("corr-123", event.getTag("correlationId"));
    }
}
