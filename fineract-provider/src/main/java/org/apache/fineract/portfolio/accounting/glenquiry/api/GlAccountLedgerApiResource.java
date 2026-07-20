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
package org.apache.fineract.portfolio.accounting.glenquiry.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.ApiRequestParameterHelper;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.ApiRequestJsonSerializationSettings;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerRequest;
import org.apache.fineract.portfolio.accounting.glenquiry.service.GlAccountLedgerReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/glaccounts/{glAccountId}/ledger")
@Component
@Tag(name = "GL Account Ledger", description = """
        Period ledger for a GL account: hybrid opening/closing summary plus journal entries.
        Empty periods still return a summary (opening = closing) with an empty entries array.
        """)
@RequiredArgsConstructor
public class GlAccountLedgerApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSION = "GLACCOUNT";

    private static final Set<String> RESPONSE_DATA_PARAMETERS = new HashSet<>(Arrays.asList("glAccountId", "glCode", "glAccountName",
            "officeId", "officeName", "departmentId", "departmentName", "currencyCode", "startDate", "endDate", "summary", "entries"));

    private final PlatformSecurityContext context;
    private final GlAccountLedgerReadPlatformService glAccountLedgerReadPlatformService;
    private final DefaultToApiJsonSerializer<GlAccountLedgerData> apiJsonSerializerService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve GL account ledger for a period", description = """
            Returns summary (openingBalance, totalDebit, totalCredit, closingBalance, lastUpdated)
            and period journal entries with running balances. Opening is hybrid as of startDate - 1.
            """)
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = GlAccountLedgerApiResourceSwagger.GetGlAccountLedgerResponse.class)))
    public String retrieveLedger(@Context final UriInfo uriInfo,
            @PathParam("glAccountId") @Parameter(description = "glAccountId") final Long glAccountId,
            @QueryParam("startDate") @Parameter(description = "Period start yyyy-MM-dd (inclusive)", required = true, example = "2026-07-01") final String startDate,
            @QueryParam("endDate") @Parameter(description = "Period end yyyy-MM-dd (inclusive)", required = true, example = "2026-07-16") final String endDate,
            @QueryParam("officeId") @Parameter(description = "Branch office id", required = true) final Long officeId,
            @QueryParam("currencyCode") @Parameter(description = "Currency code", required = true) final String currencyCode,
            @QueryParam("departmentId") @Parameter(description = "Department id (omit = all; 0 = unassigned)") final Long departmentId) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSION);

        final GlAccountLedgerRequest request = GlAccountLedgerRequest.builder() //
                .glAccountId(glAccountId) //
                .startDate(parseDate(startDate, "startDate")) //
                .endDate(parseDate(endDate, "endDate")) //
                .officeId(officeId) //
                .currencyCode(currencyCode) //
                .departmentId(departmentId) //
                .build();

        final GlAccountLedgerData result = this.glAccountLedgerReadPlatformService.retrieveLedger(request);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.apiJsonSerializerService.serialize(settings, result, RESPONSE_DATA_PARAMETERS);
    }

    private static LocalDate parseDate(final String value, final String parameterName) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (final Exception ex) {
            final List<ApiParameterError> errors = new ArrayList<>();
            new DataValidatorBuilder(errors).resource("GLACCOUNT").reset().parameter(parameterName).value(value)
                    .failWithCode("invalid.date.format");
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
