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
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
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
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryData;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryRequest;
import org.apache.fineract.portfolio.accounting.glenquiry.service.GlAccountEnquiryReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/glaccounts/enquiry")
@Component
@Tag(name = "Advanced GL Account Enquiry", description = """
        Search GL accounts with hybrid running balances (snapshot + journal delta through an as-of date).
        All search filters are optional but at least one must be provided (description and type count; asOfDate and excludeZeroBalance do not).
        By default zero-balance rows are excluded unless excludeZeroBalance=false.
        Each result row is one office × department × GL account × currency.
        """)
@RequiredArgsConstructor
public class GlAccountEnquiryApiResource {

    private static final String RESOURCE_NAME_FOR_PERMISSION = "GLACCOUNT";

    private static final Set<String> RESPONSE_DATA_PARAMETERS = new HashSet<>(Arrays.asList("officeId", "officeName", "departmentId",
            "departmentName", "glAccountId", "glCode", "glAccountName", "currencyCode", "balance", "disabled", "type"));

    private final PlatformSecurityContext context;
    private final GlAccountEnquiryReadPlatformService glAccountEnquiryReadPlatformService;
    private final DefaultToApiJsonSerializer<GlAccountEnquiryData> apiJsonSerializerService;
    private final ApiRequestParameterHelper apiRequestParameterHelper;

    @GET
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Advanced GL account enquiry", description = """
            Returns matching branch × department × GL × currency rows with hybrid balances.
            Search filters: glPrefix, ledgerNumber, officeId, departmentId, currencyCode, disabled, description, type — at least one required.
            type is the chart classification: 1 asset, 2 liability, 3 equity, 4 income, 5 expense.
            Result modifiers: excludeZeroBalance (default true); asOfDate (yyyy-MM-dd, default tenant business date).
            """)
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = GlAccountEnquiryApiResourceSwagger.GetGlAccountEnquiryResponse.class))))
    @ApiResponse(responseCode = "400", description = "No search filter was given (validation.msg.GLACCOUNT.filters.at.least.one.required), officeId is not positive, departmentId is negative, type is outside 1-5, asOfDate is not yyyy-MM-dd, or a text filter is too long.")
    @ApiResponse(responseCode = "403", description = "The user lacks READ_GLACCOUNT permission.")
    public String enquire(@Context final UriInfo uriInfo,
            @QueryParam("glPrefix") @Parameter(description = "GL code prefix (starts-with)") final String glPrefix,
            @QueryParam("ledgerNumber") @Parameter(description = "Partial GL code match") final String ledgerNumber,
            @QueryParam("officeId") @Parameter(description = "Branch office id") final Long officeId,
            @QueryParam("departmentId") @Parameter(description = "Department id. Omit for every department. 0 is unassigned only.") final Long departmentId,
            @QueryParam("currencyCode") @Parameter(description = "Currency code") final String currencyCode,
            @QueryParam("disabled") @Parameter(description = "Account disabled status") final Boolean disabled,
            @QueryParam("description") @Parameter(description = "Case-insensitive contains match on GL account name or description") final String description,
            @QueryParam("excludeZeroBalance") @Parameter(description = "When true or omitted, exclude rows with enquiry balance exactly zero; false includes them") final Boolean excludeZeroBalance,
            @QueryParam("asOfDate") @Parameter(description = "Balance as of yyyy-MM-dd. Omit to use the tenant business date.", example = "2026-05-31") final String asOfDate,
            @QueryParam("type") @Parameter(description = "Account type: 1 asset, 2 liability, 3 equity, 4 income, 5 expense. Omit for every type.") final Integer type) {

        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME_FOR_PERMISSION);

        final GlAccountEnquiryRequest request = GlAccountEnquiryRequest.builder() //
                .glPrefix(glPrefix) //
                .ledgerNumber(ledgerNumber) //
                .officeId(officeId) //
                .departmentId(departmentId) //
                .currencyCode(currencyCode) //
                .disabled(disabled) //
                .description(description) //
                .excludeZeroBalance(excludeZeroBalance) //
                .asOfDate(parseDate(asOfDate)) //
                .type(type) //
                .build();

        final Collection<GlAccountEnquiryData> results = this.glAccountEnquiryReadPlatformService.enquire(request);
        final ApiRequestJsonSerializationSettings settings = this.apiRequestParameterHelper.process(uriInfo.getQueryParameters());
        return this.apiJsonSerializerService.serialize(settings, results, RESPONSE_DATA_PARAMETERS);
    }

    private static LocalDate parseDate(final String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value.trim());
        } catch (final DateTimeParseException ex) {
            final List<ApiParameterError> errors = new ArrayList<>();
            new DataValidatorBuilder(errors).resource("GLACCOUNT").reset().parameter("asOfDate").value(value)
                    .failWithCode("invalid.date.format");
            throw new PlatformApiDataValidationException(errors);
        }
    }
}
