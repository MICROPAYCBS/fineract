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
import java.util.List;

/**
 * Page of two-factor session history. Session instants are epoch milliseconds.
 */
final class UserSessionHistoryApiResourceSwagger {

    private UserSessionHistoryApiResourceSwagger() {}

    @Schema(description = "Page of two-factor sessions, newest first")
    public static final class GetUserSessionHistoryResponse {

        private GetUserSessionHistoryResponse() {}

        @Schema(description = "Total sessions matching the filters, before paging", example = "1")
        public Integer totalFilteredRecords;
        @Schema(description = "Sessions on this page")
        public List<UserSessionsApiResourceSwagger.GetUserSessionResponse> pageItems;
    }
}
