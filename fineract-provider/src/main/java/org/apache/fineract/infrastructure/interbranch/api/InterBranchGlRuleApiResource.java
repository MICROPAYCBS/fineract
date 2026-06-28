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
package org.apache.fineract.infrastructure.interbranch.api;

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
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.interbranch.data.InterBranchGlRuleData;
import org.apache.fineract.infrastructure.interbranch.data.InterBranchGlRuleRequest;
import org.apache.fineract.infrastructure.interbranch.service.InterBranchGlRuleReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/interbranch/rules")
@Component
@Tag(name = "Inter-Branch GL Rules", description = "Micropay inter-branch settlement GL account rules")
@RequiredArgsConstructor
public class InterBranchGlRuleApiResource {

    private final InterBranchGlRuleReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<InterBranchGlRuleRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List inter-branch GL rules", operationId = "retrieveAllInterBranchGlRules")
    public List<InterBranchGlRuleData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve inter-branch GL rule template", operationId = "retrieveInterBranchGlRuleTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("statusOptions", List.of("ACTIVE", "INACTIVE"));
        return response;
    }

    @GET
    @Path("/{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve inter-branch GL rule", operationId = "retrieveInterBranchGlRule")
    public InterBranchGlRuleData retrieveOne(@PathParam("ruleId") final Long ruleId) {
        return this.readPlatformService.retrieveOne(ruleId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create inter-branch GL rule", operationId = "createInterBranchGlRule")
    public CommandProcessingResult create(final InterBranchGlRuleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createInterBranchGlRule()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{ruleId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update inter-branch GL rule", operationId = "updateInterBranchGlRule")
    public CommandProcessingResult update(@PathParam("ruleId") final Long ruleId, final InterBranchGlRuleRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateInterBranchGlRule(ruleId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{ruleId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete inter-branch GL rule", operationId = "deleteInterBranchGlRule")
    public CommandProcessingResult delete(@PathParam("ruleId") final Long ruleId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteInterBranchGlRule(ruleId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
