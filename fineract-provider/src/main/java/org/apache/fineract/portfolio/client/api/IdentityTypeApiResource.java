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
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;
import org.apache.fineract.infrastructure.codes.service.CodeValueReadPlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.portfolio.client.api.IdentityTypeConstants;
import org.apache.fineract.portfolio.client.data.IdentityTypeData;
import org.apache.fineract.portfolio.client.data.IdentityTypeRequest;
import org.apache.fineract.portfolio.client.service.IdentityTypeReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/identitytypes")
@Component
@Tag(name = "Identity Type", description = "Micropay identity document validation rules linked to Customer Identifier code values")
@RequiredArgsConstructor
public class IdentityTypeApiResource {

    private final IdentityTypeReadPlatformService readPlatformService;
    private final CodeValueReadPlatformService codeValueReadPlatformService;
    private final ToApiJsonSerializer<IdentityTypeRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List identity type validation rules", operationId = "retrieveAllIdentityTypes")
    public List<IdentityTypeData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve identity type template", operationId = "retrieveIdentityTypeTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        final List<CodeValueData> codeValueOptions = this.codeValueReadPlatformService
                .retrieveCodeValuesByCode(IdentityTypeConstants.CUSTOMER_IDENTIFIER_CODE_NAME);
        response.put("codeValueOptions", codeValueOptions);
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{identityTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve identity type validation rule", operationId = "retrieveIdentityType")
    public IdentityTypeData retrieveOne(@PathParam("identityTypeId") final Long identityTypeId) {
        return this.readPlatformService.retrieveOne(identityTypeId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create identity type validation rule", operationId = "createIdentityType")
    public CommandProcessingResult create(final IdentityTypeRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createIdentityType()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{identityTypeId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update identity type validation rule", operationId = "updateIdentityType")
    public CommandProcessingResult update(@PathParam("identityTypeId") final Long identityTypeId, final IdentityTypeRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateIdentityType(identityTypeId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{identityTypeId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete identity type validation rule", operationId = "deleteIdentityType")
    public CommandProcessingResult delete(@PathParam("identityTypeId") final Long identityTypeId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteIdentityType(identityTypeId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
