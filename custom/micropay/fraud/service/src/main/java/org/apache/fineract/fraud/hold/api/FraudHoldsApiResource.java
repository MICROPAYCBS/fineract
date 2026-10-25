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
package org.apache.fineract.fraud.hold.api;

import com.micropay.fineract.platform.command.MicropayCommandWrapperBuilder;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.fraud.hold.data.FraudHoldData;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.fraud.hold.data.FraudHoldStatus;
import org.apache.fineract.fraud.hold.service.FraudHoldConstants;
import org.apache.fineract.fraud.hold.service.FraudHoldReadService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/fraud/holds")
@Component
@Tag(name = "Micropay Fraud Holds", description = "Place and release fraud holds on clients, savings accounts and loans. A hold on a "
        + "savings account blocks its debits, a hold on a client blocks debits on all of the client's active savings accounts, and a "
        + "hold on a loan flags it as fraud. Releasing a hold reverts only what that hold changed.")
@RequiredArgsConstructor
@ConditionalOnProperty(value = FraudHoldConstants.MODULE_ENABLED_PROPERTY, havingValue = "true")
public class FraudHoldsApiResource {

    private static final String RELEASE_COMMAND = "release";

    private final PlatformSecurityContext context;
    private final FraudHoldReadService readService;
    private final DefaultToApiJsonSerializer<FraudHoldData> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List fraud holds", operationId = "retrieveFraudHolds", description = "Newest first. Filter by entityType, "
            + "entityId and status.")
    public String retrieveHolds(
            @QueryParam("entityType") @Parameter(description = "CLIENT, SAVINGS_ACCOUNT or LOAN") final String entityType,
            @QueryParam("entityId") @Parameter(description = "entityId") final Long entityId,
            @QueryParam("status") @Parameter(description = "ACTIVE or RELEASED") final String status,
            @QueryParam("limit") @Parameter(description = "maximum number of holds, default 200") final Integer limit) {
        context.authenticatedUser().validateHasReadPermission(FraudHoldConstants.ENTITY_NAME);
        final List<FraudHoldData> holds = readService.retrieveHolds(parseEnum(FraudHoldEntityType.class, "entityType", entityType),
                entityId, parseEnum(FraudHoldStatus.class, "status", status), limit);
        return toApiJsonSerializer.serialize(holds);
    }

    @GET
    @Path("{holdId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a fraud hold", operationId = "retrieveFraudHold", description = "Includes the account changes the "
            + "hold made.")
    public String retrieveHold(@PathParam("holdId") @Parameter(description = "holdId") final Long holdId) {
        context.authenticatedUser().validateHasReadPermission(FraudHoldConstants.ENTITY_NAME);
        return toApiJsonSerializer.serialize(readService.retrieveHold(holdId));
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Place a fraud hold", operationId = "createFraudHold", description = "Body: entityType (CLIENT, "
            + "SAVINGS_ACCOUNT or LOAN), entityId, reason, optional source (MANUAL, FRAUD_ENGINE or WATCHLIST) and optional "
            + "externalReference such as the fraud alert id.")
    public String createHold(@Parameter(hidden = true) final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new MicropayCommandWrapperBuilder().createFraudHold().withJson(apiRequestBodyAsJson).build();
        final CommandProcessingResult result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return toApiJsonSerializer.serialize(result);
    }

    @POST
    @Path("{holdId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Release a fraud hold", operationId = "releaseFraudHold", description = "Use command=release. Optional body: "
            + "note.")
    public String releaseHold(@PathParam("holdId") @Parameter(description = "holdId") final Long holdId,
            @QueryParam("command") @Parameter(description = "release") final String commandParam,
            @Parameter(hidden = true) final String apiRequestBodyAsJson) {
        if (!RELEASE_COMMAND.equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, RELEASE_COMMAND);
        }
        final String json = apiRequestBodyAsJson == null || apiRequestBodyAsJson.isBlank() ? "{}" : apiRequestBodyAsJson;
        final CommandWrapper commandRequest = new MicropayCommandWrapperBuilder().releaseFraudHold(holdId).withJson(json).build();
        final CommandProcessingResult result = commandsSourceWritePlatformService.logCommandSource(commandRequest);
        return toApiJsonSerializer.serialize(result);
    }

    private static <E extends Enum<E>> E parseEnum(Class<E> type, String paramName, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        final String name = value.trim().toUpperCase(Locale.ROOT);
        return Arrays.stream(type.getEnumConstants()).filter(constant -> constant.name().equals(name)).findFirst()
                .orElseThrow(() -> new UnrecognizedQueryParamException(paramName, value, (Object[]) type.getEnumConstants()));
    }
}
