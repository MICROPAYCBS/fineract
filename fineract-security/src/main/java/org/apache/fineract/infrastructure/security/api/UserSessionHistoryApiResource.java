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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.service.Page;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.UserSessionData;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.UserSessionHistoryReadService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/usersessions")
@Component
@ConditionalOnProperty("fineract.security.2fa.enabled")
@Tag(name = "User Sessions", description = "An API capability to view and revoke users' two-factor sessions.")
@RequiredArgsConstructor
public class UserSessionHistoryApiResource {

    private final PlatformSecurityContext context;
    private final UserSessionHistoryReadService userSessionHistoryReadService;
    private final ToApiJsonSerializer<Page<UserSessionData>> sessionHistorySerializer;

    @GET
    @Path("history")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Login history across all users", description = "Paginated history of two-factor sessions (successful logins) across all users, newest first, including sessions that have since been revoked or expired. Supports filtering by user and by login date. Token values are never returned. Note: logins by users with BYPASS_TWOFACTOR never create a session and do not appear here.")
    public String retrieveSessionHistory(
            @QueryParam("userId") @Parameter(description = "Restrict to one user") final Long userId,
            @QueryParam("fromDate") @Parameter(description = "Earliest login date, ISO format (yyyy-MM-dd)") final String fromDate,
            @QueryParam("toDate") @Parameter(description = "Latest login date inclusive, ISO format (yyyy-MM-dd)") final String toDate,
            @QueryParam("offset") @Parameter(description = "Record offset, a multiple of limit") final Integer offset,
            @QueryParam("limit") @Parameter(description = "Page size, default 50, max 200") final Integer limit) {

        context.authenticatedUser().validateHasReadPermission(TwoFactorConstants.USERSESSION_RESOURCE_NAME);

        final Page<UserSessionData> sessionHistory = userSessionHistoryReadService.retrieveSessionHistory(userId,
                parseIsoDate("fromDate", fromDate), parseIsoDate("toDate", toDate), offset, limit);
        return this.sessionHistorySerializer.serialize(sessionHistory);
    }

    private LocalDate parseIsoDate(final String parameterName, final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            final ApiParameterError error = ApiParameterError.parameterError("validation.msg.usersession.history.invalid.date",
                    "The parameter `" + parameterName + "` must be a date in ISO format (yyyy-MM-dd)", parameterName, value);
            throw new PlatformApiDataValidationException(List.of(error));
        }
    }
}
