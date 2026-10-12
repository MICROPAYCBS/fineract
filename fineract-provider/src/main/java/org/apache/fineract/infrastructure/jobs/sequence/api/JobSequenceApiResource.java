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
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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
@Tag(name = "Job Sequences", description = "Ordered lists of scheduler jobs and operations, such as end of day. "
        + "A run is started in the background. Poll GET /{sequenceId}/runs/{runId} until status is not RUNNING. "
        + "Run status values: RUNNING, COMPLETED, FAILED, CANCELLED, SKIPPED.")
@RequiredArgsConstructor
public class JobSequenceApiResource {

    private static final String WRITE_OK = "Command accepted. When maker-checker holds the command, the body is "
            + "{ commandId, resourceId, rollbackTransaction: true } and a held create's resourceId does not exist yet.";

    private final JobSequenceReadPlatformService readPlatformService;
    private final ToApiJsonSerializer<JobSequenceRequest> toApiJsonSerializer;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List job sequences", operationId = "retrieveAllJobSequences", description = "Returns every job sequence.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobSequenceData.class))))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_JOBSEQUENCE permission.")
    public List<JobSequenceData> retrieveAll() {
        return this.readPlatformService.retrieveAll();
    }

    @GET
    @Path("/{sequenceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve job sequence", operationId = "retrieveJobSequence", description = "Returns one sequence and its steps.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobSequenceData.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_JOBSEQUENCE permission.")
    @ApiResponse(responseCode = "404", description = "No sequence with this id (error.msg.job.sequence.not.found).")
    public JobSequenceData retrieveOne(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId) {
        return this.readPlatformService.retrieveOne(sequenceId);
    }

    @POST
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Create job sequence", operationId = "createJobSequence", description = "Creates a sequence. name is required (max 100). "
            + "description max 500. active defaults to true. steps must not be empty. Each step has a positive unique stepOrder. "
            + "stepType is SCHEDULER_JOB (needs jobShortName of an existing scheduler job) or OPERATION (needs operationCode ADVANCE_BUSINESS_DATE). "
            + "enabled and stopOnFailure default to true.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = JobSequenceRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation (validation.msg.jobsequence.steps[i].<field>.<code>, codes duplicate, invalid, unknown.job, unknown.operation, not.applicable, must.not.be.empty).")
    @ApiResponse(responseCode = "403", description = "The user lacks CREATE_JOBSEQUENCE permission, or the name already exists (error.msg.job.sequence.duplicate.name).")
    public CommandProcessingResult create(final JobSequenceRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().createJobSequence()
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @PUT
    @Path("/{sequenceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Update job sequence", operationId = "updateJobSequence", description = "Replaces the whole sequence. name and steps are required again. "
            + "Rejected while a run is in progress.")
    @RequestBody(required = true, content = @Content(schema = @Schema(implementation = JobSequenceRequest.class)))
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "A field fails validation.")
    @ApiResponse(responseCode = "403", description = "The user lacks UPDATE_JOBSEQUENCE permission, the name already exists (error.msg.job.sequence.duplicate.name), "
            + "or a run is in progress (error.msg.job.sequence.cannot.update.while.running).")
    @ApiResponse(responseCode = "404", description = "No sequence with this id (error.msg.job.sequence.not.found).")
    public CommandProcessingResult update(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId,
            final JobSequenceRequest request) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().updateJobSequence(sequenceId)
                .withJson(this.toApiJsonSerializer.serialize(request)).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @DELETE
    @Path("/{sequenceId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Delete job sequence", operationId = "deleteJobSequence", description = "Deletes a sequence that has no run in progress.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks DELETE_JOBSEQUENCE permission, or a run is in progress (error.msg.job.sequence.cannot.delete.while.running).")
    @ApiResponse(responseCode = "404", description = "No sequence with this id (error.msg.job.sequence.not.found).")
    public CommandProcessingResult delete(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().deleteJobSequence(sequenceId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @POST
    @Path("/{sequenceId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Execute job sequence", operationId = "executeJobSequence", description = "Starts a run in the background. "
            + "The 200 body has resourceId = sequence id and subResourceId = run id. Poll GET /{sequenceId}/runs/{runId} until status is not RUNNING. "
            + "A scheduler step fails when that job's run history says failed.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "command is missing or not execute.")
    @ApiResponse(responseCode = "403", description = "The user lacks EXECUTE_JOBSEQUENCE permission, the sequence is inactive (error.msg.job.sequence.inactive), "
            + "or a run is already in progress (error.msg.job.sequence.already.running).")
    @ApiResponse(responseCode = "404", description = "No sequence with this id (error.msg.job.sequence.not.found).")
    public CommandProcessingResult execute(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId,
            @QueryParam("command") @Parameter(description = "Must be execute", example = "execute") final String commandParam) {
        if (commandParam == null || !"execute".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, "execute");
        }
        final CommandWrapper commandRequest = new CommandWrapperBuilder().executeJobSequence(sequenceId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }

    @GET
    @Path("/{sequenceId}/runs")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List job sequence runs", operationId = "retrieveJobSequenceRuns", description = "Returns runs for one sequence, newest first. Dates are OffsetDateTime.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = JobSequenceRunData.class))))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_JOBSEQUENCE permission.")
    @ApiResponse(responseCode = "404", description = "No sequence with this id (error.msg.job.sequence.not.found).")
    public List<JobSequenceRunData> retrieveRuns(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId) {
        return this.readPlatformService.retrieveRuns(sequenceId);
    }

    @GET
    @Path("/{sequenceId}/runs/{runId}")
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Retrieve job sequence run", operationId = "retrieveJobSequenceRun", description = "Returns one run and its steps. errorMessage is one line and does not include a stack trace. Dates are OffsetDateTime.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = JobSequenceRunData.class)))
    @ApiResponse(responseCode = "403", description = "The user lacks READ_JOBSEQUENCE permission.")
    @ApiResponse(responseCode = "404", description = "No sequence (error.msg.job.sequence.not.found) or no run (error.msg.job.sequence.run.not.found).")
    public JobSequenceRunData retrieveRun(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId,
            @PathParam("runId") @Parameter(description = "Run id", example = "10") final Long runId) {
        return this.readPlatformService.retrieveRun(sequenceId, runId);
    }

    @POST
    @Path("/{sequenceId}/runs/{runId}")
    @Consumes({ MediaType.APPLICATION_JSON })
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Cancel a running job sequence run", operationId = "cancelJobSequenceRun", description = "Marks the run and its running step CANCELLED. A scheduler job that has already started runs to its end; the sequence stops waiting for it and starts no further steps.")
    @ApiResponse(responseCode = "200", description = WRITE_OK, content = @Content(schema = @Schema(implementation = CommandProcessingResult.class)))
    @ApiResponse(responseCode = "400", description = "command is missing or not cancel.")
    @ApiResponse(responseCode = "403", description = "The user lacks CANCEL_JOBSEQUENCE permission, or the run is not running (error.msg.job.sequence.run.not.running).")
    @ApiResponse(responseCode = "404", description = "No run with this id for this sequence (error.msg.job.sequence.run.not.found).")
    public CommandProcessingResult cancelRun(
            @PathParam("sequenceId") @Parameter(description = "Job sequence id", example = "1") final Long sequenceId,
            @PathParam("runId") @Parameter(description = "Run id", example = "10") final Long runId,
            @QueryParam("command") @Parameter(description = "Must be cancel", example = "cancel") final String commandParam) {
        if (commandParam == null || !"cancel".equalsIgnoreCase(commandParam)) {
            throw new UnrecognizedQueryParamException("command", commandParam, "cancel");
        }
        final CommandWrapper commandRequest = new CommandWrapperBuilder().cancelJobSequenceRun(sequenceId, runId).build();
        return this.commandsSourceWritePlatformService.logCommandSource(commandRequest);
    }
}
