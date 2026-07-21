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
package org.apache.fineract.portfolio.department.api;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.organisation.office.service.OfficeReadPlatformService;
import org.apache.fineract.portfolio.department.data.DepartmentData;
import org.apache.fineract.portfolio.department.data.DepartmentRequest;
import org.apache.fineract.portfolio.department.service.DepartmentReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/departments")
@Component
@Tag(name = "Department", description = "GL department master data")
@RequiredArgsConstructor
public class DepartmentApiResource {

    private final DepartmentReadPlatformService readPlatformService;
    private final OfficeReadPlatformService officeReadPlatformService;
    private final ToApiJsonSerializer<DepartmentRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List departments", description = "When officeId is provided, returns active departments mapped to that office "
            + "via entity-to-entity mapping (office_access_to_departments). Otherwise returns all departments.", operationId = "retrieveAllDepartments")
    public List<DepartmentData> retrieveAll(@QueryParam("officeId") final Long officeId) {
        if (officeId != null) {
            return this.readPlatformService.retrieveActiveMappedToOffice(officeId);
        }
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve department template", operationId = "retrieveDepartmentTemplate")
    public Map<String, Object> retrieveTemplate() {
        final Map<String, Object> response = new HashMap<>();
        response.put("activeOptions",
                List.of(new EnumOptionData(1L, "true", "Active"), new EnumOptionData(0L, "false", "Inactive")));
        response.put("officeOptions", this.officeReadPlatformService.retrieveAllOfficesForDropdown());
        return response;
    }

    @GET
    @Path("/{departmentId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve department", operationId = "retrieveDepartment")
    public DepartmentData retrieveOne(@PathParam("departmentId") final Long departmentId) {
        return this.readPlatformService.retrieveOne(departmentId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create department", operationId = "createDepartment")
    public CommandProcessingResult create(final DepartmentRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createDepartment()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{departmentId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update department", operationId = "updateDepartment")
    public CommandProcessingResult update(@PathParam("departmentId") final Long departmentId, final DepartmentRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateDepartment(departmentId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{departmentId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete department", operationId = "deleteDepartment")
    public CommandProcessingResult delete(@PathParam("departmentId") final Long departmentId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteDepartment(departmentId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
