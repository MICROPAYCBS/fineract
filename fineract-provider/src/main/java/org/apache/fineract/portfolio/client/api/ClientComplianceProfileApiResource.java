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

package org.apache.fineract.portfolio.client.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.client.data.ClientComplianceProfileData;
import org.apache.fineract.portfolio.client.data.ClientComplianceProfileRequest;
import org.apache.fineract.portfolio.client.service.ClientComplianceProfileReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/clients/{clientId}/complianceprofile")
@Component
@Tag(name = "Client Compliance Profile", description = "")
@RequiredArgsConstructor
public class ClientComplianceProfileApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSIONS = "ComplianceProfile";

    private final PlatformSecurityContext context;
    private final ClientComplianceProfileReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<ClientComplianceProfileRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a client compliance profile", operationId = "retrieveClientComplianceProfile")
    public ClientComplianceProfileData getComplianceProfile(@PathParam("clientId") final long clientId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSIONS);
        return this.readPlatformService.getClientComplianceProfile(clientId);
    }

    @PUT
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update a client compliance profile", operationId = "updateClientComplianceProfile")
    public CommandProcessingResult updateComplianceProfile(@PathParam("clientId") final long clientId,
            ClientComplianceProfileRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateComplianceProfile(clientId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
