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
package org.apache.fineract.infrastructure.security.api;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Response models for a user's two-factor sessions. ZonedDateTime is epoch milliseconds.
 */
final class UserSessionsApiResourceSwagger {

    private UserSessionsApiResourceSwagger() {}

    @Schema(description = "A two-factor session. The token value is never returned.")
    public static final class GetUserSessionResponse {

        private GetUserSessionResponse() {}

        @Schema(description = "Session id", example = "15")
        public Long id;
        @Schema(description = "User id", example = "1")
        public Long userId;
        @Schema(description = "Username", example = "mifos")
        public String username;
        @Schema(description = "Session valid-from instant, as epoch milliseconds", example = "1710000000000")
        public Long validFrom;
        @Schema(description = "Session valid-to instant, as epoch milliseconds", example = "1710003600000")
        public Long validTo;
        @Schema(description = "Client IP address recorded at login", example = "203.0.113.10")
        public String ipAddress;
        @Schema(description = "User agent recorded at login", example = "Mozilla/5.0")
        public String userAgent;
        @Schema(description = "True while the session is still enabled and inside its validity window", example = "true")
        public Boolean active;
        @Schema(description = "Why the session was revoked. Omitted while the session is still enabled.", example = "REVOKED_BY_ADMIN")
        public String revocationReason;
    }

    @Schema(description = "Command result after a session is revoked")
    public static final class PostRevokeUserSessionResponse {

        private PostRevokeUserSessionResponse() {}

        @Schema(description = "Maker-checker command id", example = "42")
        public Long commandId;
        @Schema(description = "Revoked session id. On a held command the session is still active.", example = "15")
        public Long resourceId;
        @Schema(description = "True when maker-checker held the command and rolled the transaction back", example = "true")
        public Boolean rollbackTransaction;
    }
}
