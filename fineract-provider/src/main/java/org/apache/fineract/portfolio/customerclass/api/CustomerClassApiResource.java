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
package org.apache.fineract.portfolio.customerclass.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassData;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassRequest;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassTemplateData;
import org.apache.fineract.portfolio.customerclass.service.CustomerClassReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/customerclasses")
@Component
@Tag(name = "Customer Class", description = "Micropay customer class master data")
@RequiredArgsConstructor
public class CustomerClassApiResource {

    private final CustomerClassReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<CustomerClassRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List customer classes", description = "Returns every customer class, ordered by name.", operationId = "retrieveAllCustomerClasses")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = CustomerClassData.class))))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CUSTOMERCLASS.")
    public List<CustomerClassData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/template")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve customer class template", description = "Returns option lists for customer type, legal form, risk level, "
            + "KYC level, status, and active restrictions.", operationId = "retrieveCustomerClassTemplate")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = CustomerClassApiResourceSwagger.GetCustomerClassTemplateResponse.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CUSTOMERCLASS.")
    public Map<String, Object> retrieveTemplate() {
        return buildTemplateResponse(this.readPlatformService.retrieveTemplate());
    }

    private Map<String, Object> buildTemplateResponse(final CustomerClassTemplateData templateData) {
        final Map<String, Object> response = new HashMap<>();
        response.put("customerTypeOptions", templateData.getCustomerTypeOptions());
        response.put("legalFormOptions", templateData.getLegalFormOptions());
        response.put("riskLevelOptions", templateData.getRiskLevelOptions());
        response.put("kycLevelOptions", templateData.getKycLevelOptions());
        response.put("statusOptions", templateData.getStatusOptions());
        response.put("restrictionOptions", templateData.getRestrictionOptions());
        return response;
    }

    @GET
    @Path("/{customerClassId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve a customer class", description = "Returns the customer class. When template is true, the response also "
            + "includes the template option lists under customerTypeOptions, legalFormOptions, riskLevelOptions, kycLevelOptions, "
            + "statusOptions, and restrictionOptions, plus the customer class under customerClass.", operationId = "retrieveCustomerClass")
    @ApiResponse(responseCode = "200", description = "CustomerClassData when template is false. The template response, including customerClass, when template is true.", content = @Content(schema = @Schema(oneOf = {
            CustomerClassData.class, CustomerClassApiResourceSwagger.GetCustomerClassWithTemplateResponse.class })))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks read permission for CUSTOMERCLASS.")
    @ApiResponse(responseCode = "404", description = "The customer class was not found.")
    public Object retrieveOne(
            @PathParam("customerClassId") @Parameter(description = "Customer class identifier", required = true, example = "1") final Long customerClassId,
            @QueryParam("template") @DefaultValue("false") @Parameter(description = "When true, include template option lists with the customer class. Defaults to false.", example = "false") final boolean template) {
        if (template) {
            final CustomerClassData customerClass = this.readPlatformService.retrieveOne(customerClassId);
            final CustomerClassTemplateData templateData = this.readPlatformService.retrieveTemplate();
            final Map<String, Object> response = new HashMap<>();
            response.putAll(buildTemplateResponse(templateData));
            response.put("customerClass", customerClass);
            return response;
        }
        return this.readPlatformService.retrieveOne(customerClassId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create customer class", description = "Creates a customer class. The class code must be unique. On create, "
            + "customerType is limited to GROUP and JOINT, and age limits are rejected for entity legal form. "
            + "The command is submitted through maker-checker.", operationId = "createCustomerClass")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CustomerClassRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, or a field fails validation.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to create the customer class, or the class code already exists.")
    public CommandProcessingResult create(final CustomerClassRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createCustomerClass()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{customerClassId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update customer class", description = "Updates a customer class. A changed class code must stay unique. "
            + "On update, customerType may be INDIVIDUAL, CORPORATE, GROUP, or JOINT. The command is submitted through maker-checker.", operationId = "updateCustomerClass")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = CustomerClassRequest.class)))
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "The JSON body is missing or invalid, or a field fails validation.")
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to update the customer class, or the class code already exists.")
    @ApiResponse(responseCode = "404", description = "The customer class was not found.")
    public CommandProcessingResult update(
            @PathParam("customerClassId") @Parameter(description = "Customer class identifier", required = true, example = "1") final Long customerClassId,
            final CustomerClassRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateCustomerClass(customerClassId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{customerClassId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete customer class", description = "Deletes a customer class that is not referenced by other records. "
            + "The command is submitted through maker-checker.", operationId = "deleteCustomerClass")
    @ApiResponse(responseCode = "200", description = "Command accepted. "
            + "When maker-checker holds the command, the body is { commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.", content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The authenticated user lacks permission to delete the customer class, or the class is referenced by other records.")
    @ApiResponse(responseCode = "404", description = "The customer class was not found.")
    public CommandProcessingResult delete(
            @PathParam("customerClassId") @Parameter(description = "Customer class identifier", required = true, example = "1") final Long customerClassId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteCustomerClass(customerClassId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
