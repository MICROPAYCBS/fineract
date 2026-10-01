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
package org.apache.fineract.portfolio.savings.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.data.SavingsAccountChannelLimitData;
import org.apache.fineract.portfolio.savings.data.SavingsAccountPaymentChannelData;
import org.apache.fineract.portfolio.savings.service.SavingsAccountPaymentChannelWritePlatformService;
import org.apache.fineract.portfolio.savings.service.SavingsChannelLimitService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.stereotype.Component;

@Path("/v1/savingsaccounts/{savingsAccountId}/paymentchannels")
@Component
@Tag(name = "Savings Account Payment Channels", description = "Product payment-channel catalog with account subscription status. Premium channels require subscribe before use on deposits.")
@RequiredArgsConstructor
public class SavingsAccountPaymentChannelsApiResource {

    private static final String RESOURCE_NAME = "SAVINGSACCOUNTPAYMENTCHANNEL";

    private final PlatformSecurityContext context;
    private final SavingsAccountPaymentChannelWritePlatformService writePlatformService;
    private final DefaultToApiJsonSerializer<SavingsAccountPaymentChannelData> toApiJsonSerializer;
    private final ApiRequestParameterHelper apiRequestParameterHelper;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final SavingsChannelLimitService savingsChannelLimitService;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List payment channels for a savings account", description = "Returns the product channel catalog with subscription status and whether each channel is allowed for deposit.")
    @ApiResponse(responseCode = "200", description = "OK")
    public String retrieveAll(@PathParam("savingsAccountId") @Parameter(description = "savingsAccountId") final Long savingsAccountId,
            @Context final UriInfo uriInfo) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final Collection<SavingsAccountPaymentChannelData> channels = this.writePlatformService.retrieveAccountChannels(savingsAccountId);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.toApiJsonSerializer.serialize(settings, channels);
    }

    @GET
    @Path("{productPaymentChannelId}/limits")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Read channel transaction limits", description = "Returns the bank ceiling, the customer limit, any pending increase, and remaining day and month usage. direction is optional (DEBIT or CREDIT).")
    @ApiResponse(responseCode = "200", description = "OK")
    public String retrieveLimits(@PathParam("savingsAccountId") @Parameter(description = "savingsAccountId") final Long savingsAccountId,
            @PathParam("productPaymentChannelId") @Parameter(description = "productPaymentChannelId") final Long productPaymentChannelId,
            @QueryParam("direction") @Parameter(description = "direction") final String direction) {
        final AppUser user = this.context.authenticatedUser();
        if (user.hasNotPermissionForAnyOf("ALL_FUNCTIONS", "ALL_FUNCTIONS_READ", "READCHANNELLIMIT_SAVINGSACCOUNT")) {
            throw new NoAuthorizationException("User has no authority to read savings account channel limits");
        }
        final List<SavingsAccountChannelLimitData> limits = this.savingsChannelLimitService.retrieve(savingsAccountId,
                productPaymentChannelId, direction);
        return this.toApiJsonSerializer.serialize(limits);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Subscribe, unsubscribe, block, or unblock a payment channel", description = "command=subscribe|unsubscribe|block|unblock with body { \"paymentTypeId\": n }")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = String.class)))
    @ApiResponse(responseCode = "200", description = "OK")
    public String handleCommands(@PathParam("savingsAccountId") @Parameter(description = "savingsAccountId") final Long savingsAccountId,
            @QueryParam("command") @Parameter(description = "command") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {

        final CommandWrapperBuilder builder = new CommandWrapperBuilder().withJson(apiRequestBodyAsJson);
        CommandWrapper commandRequest = null;
        if (is(commandParam, "subscribe")) {
            commandRequest = builder.subscribeSavingsAccountPaymentChannel(savingsAccountId).build();
        } else if (is(commandParam, "unsubscribe")) {
            commandRequest = builder.unsubscribeSavingsAccountPaymentChannel(savingsAccountId).build();
        } else if (is(commandParam, "block")) {
            commandRequest = builder.blockSavingsAccountPaymentChannel(savingsAccountId).build();
        } else if (is(commandParam, "unblock")) {
            commandRequest = builder.unblockSavingsAccountPaymentChannel(savingsAccountId).build();
        } else if (is(commandParam, "updateLimit")) {
            commandRequest = builder.updateSavingsAccountChannelLimit(savingsAccountId).build();
        }

        if (commandRequest == null) {
            throw new UnrecognizedQueryParamException("command", commandParam, "subscribe", "unsubscribe", "block", "unblock",
                    "updateLimit");
        }

        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return this.toApiJsonSerializer.serialize(result);
    }

    private boolean is(final String commandParam, final String commandValue) {
        return StringUtils.isNotBlank(commandParam) && commandParam.trim().equalsIgnoreCase(commandValue);
    }
}
