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
package org.apache.fineract.treasury.api;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasurySetupData;
import org.apache.fineract.treasury.service.TreasurySetupService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury/setup")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury setup", description = "Onboarding by client type: templates, apply-template and the checklist")
@RequiredArgsConstructor
public class TreasurySetupApiResource {

    public static final String TREASURY_SETUP_RESOURCE_NAME = "TREASURYSETUP";

    private final PlatformSecurityContext context;
    private final TreasurySetupService setupService;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Path("checklist")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Onboarding checklist", description = "Each step with DONE, TODO or NOT_APPLICABLE")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasurySetupData.class)))
    public TreasurySetupData checklist() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_SETUP_RESOURCE_NAME);
        return this.setupService.checklist();
    }

    @GET
    @Path("templates")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Client-type templates", description = "The rule set and regulatory limits each client type applies")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasurySetupData.Template.class))))
    public List<TreasurySetupData.Template> templates() {
        this.context.authenticatedUser().validateHasReadPermission(TREASURY_SETUP_RESOURCE_NAME);
        return this.setupService.templates();
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Apply a client-type template", description = "command=apply-template, clientType (PSP, BANK, MDI, SACCO_MFI) as a query parameter or in the body, enableMakerChecker (default true); idempotent; maker-checker capable")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    public CommandProcessingResult apply(@QueryParam("command") final String commandParam,
            @QueryParam("clientType") final String clientType, @Parameter(hidden = true) final String body) {
        if (!"apply-template".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, new Object[] { "apply-template" });
        }
        final JsonObject json = StringUtils.isBlank(body) ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
        if (clientType != null) {
            json.addProperty("clientType", clientType);
        }
        return this.commandsSourceWritePlatformService.logCommandSource(new MicropayCommandWrapperBuilder()
                .treasuryCommand(TREASURY_SETUP_RESOURCE_NAME, "APPLYTEMPLATE", null, "/treasury/setup?command=apply-template")
                .withJson(json.toString()).build());
    }
}
