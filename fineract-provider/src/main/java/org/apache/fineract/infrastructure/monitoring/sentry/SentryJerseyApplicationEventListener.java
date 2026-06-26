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

import jakarta.ws.rs.ext.Provider;
import org.glassfish.jersey.server.ContainerRequest;
import org.glassfish.jersey.server.ContainerResponse;
import org.glassfish.jersey.server.monitoring.ApplicationEvent;
import org.glassfish.jersey.server.monitoring.ApplicationEventListener;
import org.glassfish.jersey.server.monitoring.RequestEvent;
import org.glassfish.jersey.server.monitoring.RequestEventListener;
import org.springframework.stereotype.Component;

/**
 * Reports Jersey API failures to Sentry after exception mapping completes.
 *
 * <p>Fineract handles API errors through JAX-RS {@code ExceptionMapper} beans, so failures never
 * bubble up as servlet uncaught exceptions. This listener captures only mapped 5xx responses.
 * Expected 4xx validation and business-rule failures are intentionally excluded.
 */
@Component
@Provider
public class SentryJerseyApplicationEventListener implements ApplicationEventListener {

    @Override
    public void onEvent(ApplicationEvent event) {
        // no-op
    }

    @Override
    public RequestEventListener onRequest(RequestEvent requestEvent) {
        return new SentryJerseyRequestEventListener();
    }

    private static final class SentryJerseyRequestEventListener implements RequestEventListener {

        @Override
        public void onEvent(RequestEvent event) {
            if (event.getType() != RequestEvent.Type.EXCEPTION_MAPPING_FINISHED) {
                return;
            }

            Throwable exception = event.getException();
            if (exception == null) {
                return;
            }

            ContainerResponse response = event.getContainerResponse();
            int status = response != null ? response.getStatus() : 500;
            if (status < 500) {
                return;
            }

            ContainerRequest request = event.getContainerRequest();
            String method = request != null ? request.getMethod() : null;
            String path = event.getUriInfo() != null ? event.getUriInfo().getPath() : null;
            FineractSentrySupport.reportServerSideApiError(exception, status, method, path);
        }
    }
}
