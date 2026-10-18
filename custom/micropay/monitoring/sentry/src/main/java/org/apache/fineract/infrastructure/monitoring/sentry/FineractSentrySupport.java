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

import io.sentry.IScope;
import io.sentry.Sentry;
import io.sentry.SentryEvent;
import io.sentry.protocol.SentryId;
import org.apache.fineract.infrastructure.core.exception.ErrorHandler;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.slf4j.MDC;

final class FineractSentrySupport {

    private FineractSentrySupport() {}

    static String captureServerError(Throwable exception, int httpStatus, String httpMethod, String requestPath) {
        if (!Sentry.isEnabled() || exception == null || httpStatus < 500) {
            return null;
        }

        Throwable reportable = toReportableThrowable(exception);
        SentryId eventId = Sentry.captureException(reportable, scope -> applyRequestContext(scope, httpStatus, httpMethod, requestPath));
        return eventId != null ? eventId.toString() : null;
    }

    static void enrichEvent(SentryEvent event) {
        applyRequestContext(event);
    }

    private static Throwable toReportableThrowable(Throwable exception) {
        if (exception instanceof Exception ex) {
            return ErrorHandler.findMostSpecificException(ex);
        }
        return exception;
    }

    private static void applyRequestContext(IScope scope, int httpStatus, String httpMethod, String requestPath) {
        scope.setTag("http.status_code", String.valueOf(httpStatus));
        scope.setTag("surface", "jersey-api");
        if (httpMethod != null) {
            scope.setTag("http.method", httpMethod);
        }
        if (requestPath != null) {
            scope.setExtra("request.path", requestPath);
        }
        applyFineractContext(scope);
    }

    private static void applyRequestContext(SentryEvent event) {
        var tenant = ThreadLocalContextUtil.getTenant();
        if (tenant != null) {
            event.setTag("tenant", tenant.getTenantIdentifier());
        }

        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            event.setTag("correlationId", correlationId);
        }
    }

    private static void applyFineractContext(IScope scope) {
        var tenant = ThreadLocalContextUtil.getTenant();
        if (tenant != null) {
            scope.setTag("tenant", tenant.getTenantIdentifier());
        }

        String correlationId = MDC.get("correlationId");
        if (correlationId != null) {
            scope.setTag("correlationId", correlationId);
        }
    }
}
