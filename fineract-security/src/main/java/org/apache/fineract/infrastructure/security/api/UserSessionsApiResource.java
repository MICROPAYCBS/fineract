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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.constants.TwoFactorConstants;
import org.apache.fineract.infrastructure.security.data.UserSessionData;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.TwoFactorService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.AppUserRepository;
import org.apache.fineract.useradministration.exception.UserNotFoundException;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/users/{userId}/sessions")
@Component
@ConditionalOnProperty("fineract.security.2fa.enabled")
@Tag(name = "User Sessions", description = "An API capability to view and revoke a user's active two-factor sessions.")
@RequiredArgsConstructor
public class UserSessionsApiResource {

    private final PlatformSecurityContext context;
    private final AppUserRepository appUserRepository;
    private final TwoFactorService twoFactorService;
    private final ToApiJsonSerializer<UserSessionData> sessionDataSerializer;
    private final ToApiJsonSerializer<CommandProcessingResult> commandResultSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List active sessions for a user", description = "Returns the user's active two-factor sessions with device metadata (IP address, user agent, validity window). Token values are never returned. Users may always view their own sessions; viewing another user's sessions requires the READ_USERSESSION permission.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = UserSessionsApiResourceSwagger.GetUserSessionResponse.class))))
    @ApiResponse(responseCode = "403", description = "The caller is viewing another user and does not have permission to read user sessions")
    @ApiResponse(responseCode = "404", description = "User not found")
    public String retrieveActiveSessions(
            @PathParam("userId") @Parameter(description = "Identifier of the user whose active two-factor sessions are listed") final Long userId) {
        final AppUser requester = context.authenticatedUser();
        if (!requester.getId().equals(userId)) {
            requester.validateHasReadPermission(TwoFactorConstants.USERSESSION_RESOURCE_NAME);
        }

        final AppUser user = appUserRepository.findById(userId).orElseThrow(() -> new UserNotFoundException(userId));

        final List<UserSessionData> sessions = twoFactorService.fetchActiveSessionsForUser(user).stream().map(TFAccessToken::toSessionData)
                .toList();
        return this.sessionDataSerializer.serialize(sessions);
    }

    @Path("{sessionId}/revoke")
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Revoke a user's session", description = "Revokes the given session so the device's next API call is rejected and it must sign in again. Processed through the command framework, so the action is audited and supports maker-checker.")
    @ApiResponse(responseCode = "200", description = "OK. When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = UserSessionsApiResourceSwagger.PostRevokeUserSessionResponse.class)))
    @ApiResponse(responseCode = "403", description = "The user does not have permission to revoke user sessions")
    @ApiResponse(responseCode = "404", description = "User not found, or the session does not belong to that user or is no longer enabled")
    public String revokeSession(
            @PathParam("userId") @Parameter(description = "Identifier of the user who owns the session") final Long userId,
            @PathParam("sessionId") @Parameter(description = "Identifier of the two-factor session to revoke") final Long sessionId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().revokeUserSession(userId, sessionId).build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.commandResultSerializer.serialize(result);
    }
}
