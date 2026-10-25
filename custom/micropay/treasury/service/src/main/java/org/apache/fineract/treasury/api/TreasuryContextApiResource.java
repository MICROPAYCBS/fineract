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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.treasury.data.TreasuryContextData;
import org.apache.fineract.treasury.service.TreasuryContextService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/treasury")
@Component
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
@Tag(name = "Treasury context", description = "Form defaults and the signed-in user's own commands awaiting a checker")
@RequiredArgsConstructor
public class TreasuryContextApiResource {

    /** Any of these lets a user read the form defaults: whoever works in treasury needs the business date. */
    private static final String[] TREASURY_READ_PERMISSIONS = { "ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "READ_TREASURYACCOUNT",
            "READ_TREASURYTRANSFER", "READ_TREASURYAGENTFLOAT", "READ_TREASURYFLOATREQUEST", "READ_TREASURYSTATEMENT",
            "READ_TREASURYCOUNTERPARTY", "READ_TREASURYDEAL", "READ_TREASURYPRICE", "READ_TREASURYRUN", "READ_TREASURYBREACH",
            "READ_TREASURYTRUST", "READ_TREASURYFXPOSITION", "READ_TREASURYLIMIT", "READ_TREASURYSETTING" };

    private final PlatformSecurityContext context;
    private final TreasuryContextService contextService;

    @GET
    @Path("template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Defaults for treasury forms", description = "The business date and the base currency, for any user who may read some part of treasury (the business date endpoint needs READ_BUSINESSDATE)")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TreasuryContextData.Template.class)))
    public TreasuryContextData.Template template() {
        final AppUser user = this.context.authenticatedUser();
        if (user.hasNotPermissionForAnyOf(TREASURY_READ_PERMISSIONS)) {
            throw new NoAuthorizationException("User has no authority to read treasury");
        }
        return this.contextService.template();
    }

    @GET
    @Path("pending-commands")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "My treasury commands awaiting a checker", description = "The signed-in user's own treasury commands awaiting approval, newest first (200 at most); optionally of one entityName (e.g. TREASURYDEAL) and resourceId")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TreasuryContextData.PendingCommand.class))))
    public List<TreasuryContextData.PendingCommand> pendingCommands(@QueryParam("entityName") final String entityName,
            @QueryParam("resourceId") final Long resourceId) {
        final AppUser user = this.context.authenticatedUser();
        return this.contextService.pendingCommands(user.getId(), entityName, resourceId);
    }
}
