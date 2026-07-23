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
package org.apache.fineract.infrastructure.jobs.sequence.api;

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
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.exception.UnrecognizedQueryParamException;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceData;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRequest;
import org.apache.fineract.infrastructure.jobs.sequence.data.JobSequenceRunData;
import org.apache.fineract.infrastructure.jobs.sequence.service.JobSequenceReadPlatformService;
import org.springframework.stereotype.Component;

@Path("/v1/jobsequences")
@Component
@Tag(name = "Job Sequences", description = "Configurable ordered job/operation sequences for EOD and period close")
@RequiredArgsConstructor
public class JobSequenceApiResource {

    private final JobSequenceReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<JobSequenceRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List job sequences", operationId = "retrieveAllJobSequences")
    public List<JobSequenceData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/{sequenceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve job sequence", operationId = "retrieveJobSequence")
    public JobSequenceData retrieveOne(@PathParam("sequenceId") final Long sequenceId) {
        return this.readPlatformService.retrieveOne(sequenceId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create job sequence", operationId = "createJobSequence")
    public CommandProcessingResult create(final JobSequenceRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createJobSequence()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{sequenceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update job sequence", operationId = "updateJobSequence")
    public CommandProcessingResult update(@PathParam("sequenceId") final Long sequenceId, final JobSequenceRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateJobSequence(sequenceId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{sequenceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete job sequence", operationId = "deleteJobSequence")
    public CommandProcessingResult delete(@PathParam("sequenceId") final Long sequenceId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteJobSequence(sequenceId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @POST
    @Path("/{sequenceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Execute job sequence", operationId = "executeJobSequence")
    public CommandProcessingResult execute(@PathParam("sequenceId") final Long sequenceId,
            @QueryParam("command") final String commandParam) {
        if (commandParam == null || !"execute".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, "execute");
        }
        final CommandWrapper commandRequest = new CommandWrapperBuilder().executeJobSequence(sequenceId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @GET
    @Path("/{sequenceId}/runs")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List job sequence runs", operationId = "retrieveJobSequenceRuns")
    public List<JobSequenceRunData> retrieveRuns(@PathParam("sequenceId") final Long sequenceId) {
        return this.readPlatformService.retrieveRuns(sequenceId);
    }

    @GET
    @Path("/{sequenceId}/runs/{runId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve job sequence run", operationId = "retrieveJobSequenceRun")
    public JobSequenceRunData retrieveRun(@PathParam("sequenceId") final Long sequenceId, @PathParam("runId") final Long runId) {
        return this.readPlatformService.retrieveRun(sequenceId, runId);
    }
}
