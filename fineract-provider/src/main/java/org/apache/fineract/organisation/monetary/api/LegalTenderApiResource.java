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
package org.apache.fineract.organisation.monetary.api;

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
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import com.google.gson.JsonElement;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.organisation.monetary.data.CurrencyLegalTenderData;
import org.apache.fineract.organisation.monetary.data.CurrencyLegalTenderRequest;
import org.apache.fineract.organisation.monetary.service.LegalTenderReadPlatformService;
import org.apache.fineract.organisation.monetary.service.LegalTenderWritePlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/currencies/{currencyCode}/legal-tenders")
@Component
@Tag(name = "Legal Tender", description = "Currency legal tender master data")
@RequiredArgsConstructor
public class LegalTenderApiResource {

    private static final String ENTITY_NAME = "LEGAL_TENDER";

    private final LegalTenderReadPlatformService readPlatformService;
    private final LegalTenderWritePlatformService writePlatformService;
    private final ToApiJsonSerializer<CurrencyLegalTenderRequest> toApiJsonSerializer;
    private final FromJsonHelper fromJsonHelper;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List legal tenders for currency", operationId = "retrieveLegalTenders")
    public List<CurrencyLegalTenderData> retrieveAll(@PathParam("currencyCode") final String currencyCode,
            @QueryParam("includeInactive") final Boolean includeInactive) {
        return this.readPlatformService.retrieveAll(currencyCode, Boolean.TRUE.equals(includeInactive));
    }

    @GET
    @Path("{legalTenderId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve legal tender", operationId = "retrieveLegalTender")
    public CurrencyLegalTenderData retrieveOne(@PathParam("currencyCode") final String currencyCode,
            @PathParam("legalTenderId") final Long legalTenderId) {
        return this.readPlatformService.retrieveOne(currencyCode, legalTenderId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create legal tender", operationId = "createLegalTender")
    public CommandProcessingResult create(@PathParam("currencyCode") final String currencyCode, final CurrencyLegalTenderRequest request) {
        final String json = this.toApiJsonSerializer.serialize(request);
        final JsonElement parsed = this.fromJsonHelper.parse(json);
        final JsonCommand command = JsonCommand.from(json, parsed, this.fromJsonHelper, ENTITY_NAME, null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        return this.writePlatformService.createLegalTender(currencyCode, command);
    }

    @PUT
    @Path("{legalTenderId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update legal tender", operationId = "updateLegalTender")
    public CommandProcessingResult update(@PathParam("currencyCode") final String currencyCode,
            @PathParam("legalTenderId") final Long legalTenderId, final CurrencyLegalTenderRequest request) {
        final String json = this.toApiJsonSerializer.serialize(request);
        final JsonElement parsed = this.fromJsonHelper.parse(json);
        final JsonCommand command = JsonCommand.from(json, parsed, this.fromJsonHelper, ENTITY_NAME, legalTenderId, null, null, null, null,
                null, null, null, null, null, null, null, null);
        return this.writePlatformService.updateLegalTender(currencyCode, legalTenderId, command);
    }

    @DELETE
    @Path("{legalTenderId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete legal tender", operationId = "deleteLegalTender")
    public CommandProcessingResult delete(@PathParam("currencyCode") final String currencyCode,
            @PathParam("legalTenderId") final Long legalTenderId) {
        final String json = "{}";
        final JsonElement parsed = this.fromJsonHelper.parse(json);
        final JsonCommand command = JsonCommand.from(json, parsed, this.fromJsonHelper, ENTITY_NAME, legalTenderId, null, null, null, null,
                null, null, null, null, null, null, null, null);
        return this.writePlatformService.deleteLegalTender(currencyCode, legalTenderId, command);
    }
}
