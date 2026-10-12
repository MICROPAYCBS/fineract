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
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.TwoFactorConfigurationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/twofactor/configure")
@Produces({ MediaType.APPLICATION_JSON })
@Component
@ConditionalOnProperty("fineract.security.2fa.enabled")
@Tag(name = "Two Factor", description = "Read and update tenant two-factor delivery and token lifetime settings. Requires the TWOFACTOR_CONFIGURATION permission.")
@RequiredArgsConstructor
public class TwoFactorConfigurationApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "TWOFACTOR_CONFIGURATION";

    private final PlatformSecurityContext context;
    private final TwoFactorConfigurationService configurationService;
    private final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Operation(summary = "Retrieve two-factor configuration", description = "Returns the tenant two-factor settings. Keys use the stored configuration names, such as otp-delivery-email-enable and access-token-live-time.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.GetTwoFactorConfigurationResponse.class)))
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated")
    @ApiResponse(responseCode = "403", description = "The caller does not have read permission on TWOFACTOR_CONFIGURATION")
    public String retrieveAll() {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        Map<String, Object> configurationMap = configurationService.retrieveAll();
        return toApiJsonSerializer.serialize(configurationMap);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update two-factor configuration", description = "Updates one or more two-factor settings. otp-delivery-method must be email, sms, or totp. Boolean keys are otp-delivery-email-enable and otp-delivery-sms-enable. Number keys are otp-delivery-sms-provider, otp-token-live-time, otp-token-length, access-token-live-time, and access-token-live-time-extended.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.PutTwoFactorConfigurationRequest.class)))
    @ApiResponse(responseCode = "200", description = "OK. When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.PutTwoFactorConfigurationResponse.class)))
    @ApiResponse(responseCode = "400", description = "A key is unknown or a value has the wrong type")
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated")
    @ApiResponse(responseCode = "403", description = "The caller may not update TWOFACTOR_CONFIGURATION")
    public String updateConfiguration(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateTwoFactorConfiguration().withJson(apiRequestBodyAsJson)
                .build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }
}
