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
package org.apache.fineract.infrastructure.dataqueries.api;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableColumnValidationWriteService;
import org.apache.fineract.infrastructure.dataqueries.service.DatatableColumnValidationWriteService.DatatableColumnValidationEntry;
import org.apache.fineract.infrastructure.security.exception.NoAuthorizationException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.springframework.stereotype.Component;

@Path("/v1/datatables")
@Component
@Tag(name = "Data Tables", description = "Datatable column validation metadata (Micropay extension)")
@RequiredArgsConstructor
public class DatatableColumnValidationsApiResource {

    private static final String RESOURCE_NAME = "DATATABLE";

    private final PlatformSecurityContext context;
    private final FromJsonHelper fromJsonHelper;
    private final DatatableColumnValidationWriteService columnValidationWriteService;

    @PUT
    @Path("{datatableName}/columnvalidations")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Sync datatable column validation metadata")
    public CommandProcessingResult syncColumnValidations(
            @PathParam("datatableName") @Parameter(description = "datatableName") final String datatableName,
            final String apiRequestBodyAsJson) {
        validateHasCreateOrUpdatePermission();

        final JsonObject element = this.fromJsonHelper.parse(apiRequestBodyAsJson).getAsJsonObject();
        final JsonArray columnsArray = this.fromJsonHelper.extractJsonArrayNamed("columnValidations", element);
        final List<DatatableColumnValidationEntry> entries = new ArrayList<>();
        if (columnsArray != null) {
            for (final JsonElement columnElement : columnsArray) {
                final JsonObject column = columnElement.getAsJsonObject();
                final String columnName = this.fromJsonHelper.extractStringNamed("columnName", column);
                final String validationRegex = this.fromJsonHelper.extractStringNamed("validationRegex", column);
                final String validationExample = this.fromJsonHelper.extractStringNamed("validationExample", column);
                final String validationMessage = this.fromJsonHelper.extractStringNamed("validationMessage", column);
                entries.add(new DatatableColumnValidationEntry(columnName, validationRegex, validationExample, validationMessage));
            }
        }

        this.columnValidationWriteService.syncColumnValidations(datatableName, entries);

        final JsonArray deleteArray = this.fromJsonHelper.extractJsonArrayNamed("deleteColumnNames", element);
        if (deleteArray != null) {
            for (final JsonElement deleteElement : deleteArray) {
                if (deleteElement.isJsonPrimitive()) {
                    this.columnValidationWriteService.deleteColumnValidation(datatableName, deleteElement.getAsString());
                }
            }
        }

        return CommandProcessingResult.empty();
    }

    private void validateHasCreateOrUpdatePermission() {
        try {
            this.context.authenticatedUser().validateHasUpdatePermission(RESOURCE_NAME);
        } catch (final NoAuthorizationException updateDenied) {
            this.context.authenticatedUser().validateHasCreatePermission(RESOURCE_NAME);
        }
    }
}
