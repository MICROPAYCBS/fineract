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
package org.apache.fineract.infrastructure.security.api;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.headers.Header;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.UriInfo;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.commands.domain.CommandWrapper;
import org.apache.fineract.commands.service.CommandWrapperBuilder;
import org.apache.fineract.commands.service.PortfolioCommandSourceWritePlatformService;
import org.apache.fineract.infrastructure.core.annotation.AlternativeOperationId;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.serialization.DefaultToApiJsonSerializer;
import org.apache.fineract.infrastructure.core.serialization.ToApiJsonSerializer;
import org.apache.fineract.infrastructure.security.data.AccessTokenData;
import org.apache.fineract.infrastructure.security.data.OTPDeliveryMethod;
import org.apache.fineract.infrastructure.security.data.OTPMetadata;
import org.apache.fineract.infrastructure.security.data.OTPRequest;
import org.apache.fineract.infrastructure.security.data.TotpEnrollmentData;
import org.apache.fineract.infrastructure.security.domain.TFAccessToken;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.infrastructure.security.service.TotpService;
import org.apache.fineract.infrastructure.security.service.TwoFactorService;
import org.apache.fineract.useradministration.domain.AppUser;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Path("/v1/twofactor")
@Component
@ConditionalOnProperty("fineract.security.2fa.enabled")
@Tag(name = "Two Factor", description = "Request, validate, and invalidate two-factor access tokens, and enroll an authenticator app. "
        + "Send the token from validate as the Fineract-Platform-TFA-Token header. Users with BYPASS_TWOFACTOR do not send it. "
        + "A superseded session returns 401 with Fineract-Platform-Reason: session-superseded.")
@RequiredArgsConstructor
public class TwoFactorApiResource {

    private final ToApiJsonSerializer<OTPMetadata> otpRequestSerializer;
    private final ToApiJsonSerializer<OTPDeliveryMethod> otpDeliveryMethodSerializer;
    private final ToApiJsonSerializer<AccessTokenData> accessTokenSerializer;
    private final ToApiJsonSerializer<TotpEnrollmentData> totpEnrollmentSerializer;
    private final DefaultToApiJsonSerializer<Map<String, Object>> toApiJsonSerializer;

    private final PlatformSecurityContext context;
    private final PortfolioCommandSourceWritePlatformService commandsSourceWritePlatformService;
    private final TwoFactorService twoFactorService;
    private final TotpService totpService;

    @GET
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "List two-factor delivery methods", description = "Returns the delivery methods available to the authenticated user: email, sms, and totp when each is enabled and the user has a destination.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(array = @ArraySchema(schema = @Schema(implementation = TwoFactorApiResourceSwagger.GetTwoFactorDeliveryMethodResponse.class))))
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated, or Fineract-Platform-TFA-Token is invalid. When a newer login superseded this session the response includes Fineract-Platform-Reason: session-superseded.", headers = {
            @Header(name = "Fineract-Platform-Reason", description = "session-superseded when this access token was revoked by a newer login", schema = @Schema(example = "session-superseded")) })
    public String getOTPDeliveryMethods(@Context final UriInfo uriInfo) {
        AppUser user = context.authenticatedUser();

        List<OTPDeliveryMethod> otpDeliveryMethods = twoFactorService.getDeliveryMethodsForUser(user);
        return this.otpDeliveryMethodSerializer.serialize(otpDeliveryMethods);
    }

    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Request a one-time password", description = "Creates a one-time password and sends it by the named delivery method. The password is not in the response. Use totp to skip delivery and validate a code from an authenticator app.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.PostTwoFactorResponse.class)))
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated, or Fineract-Platform-TFA-Token is invalid. When a newer login superseded this session the response includes Fineract-Platform-Reason: session-superseded.", headers = {
            @Header(name = "Fineract-Platform-Reason", description = "session-superseded when this access token was revoked by a newer login", schema = @Schema(example = "session-superseded")) })
    @ApiResponse(responseCode = "403", description = "deliveryMethod is not enabled for this user, or TOTP was requested before enrollment")
    public String requestToken(
            @QueryParam("deliveryMethod") @Parameter(description = "How to send the one-time password: email, sms, or totp") final String deliveryMethod,
            @QueryParam("extendedToken") @DefaultValue("false") @Parameter(description = "When true, the access token created by validate uses the extended lifetime") boolean extendedAccessToken,
            @Context final UriInfo uriInfo) {
        final AppUser user = context.authenticatedUser();

        final OTPRequest request = twoFactorService.createNewOTPToken(user, deliveryMethod, extendedAccessToken);
        return this.otpRequestSerializer.serialize(request.getMetadata());
    }

    @Path("validate")
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Validate a one-time password", description = "Exchanges a one-time password, or a TOTP code, for an access token. Send that token as Fineract-Platform-TFA-Token. validFrom and validTo are epoch milliseconds.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.PostTwoFactorValidateResponse.class)))
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated, or Fineract-Platform-TFA-Token is invalid. When a newer login superseded this session the response includes Fineract-Platform-Reason: session-superseded.", headers = {
            @Header(name = "Fineract-Platform-Reason", description = "session-superseded when this access token was revoked by a newer login", schema = @Schema(example = "session-superseded")) })
    @ApiResponse(responseCode = "403", description = "The one-time password is missing, expired, or does not match, or TOTP is not enrolled")
    public String validate(@QueryParam("token") @Parameter(description = "One-time password or authenticator code") final String token,
            @Context final HttpServletRequest request) {
        final AppUser user = context.authenticatedUser();

        TFAccessToken accessToken = twoFactorService.createAccessTokenFromOTP(user, token, resolveClientIpAddress(request),
                request.getHeader(HttpHeaders.USER_AGENT));

        return accessTokenSerializer.serialize(accessToken.toTokenData());
    }

    private String resolveClientIpAddress(final HttpServletRequest request) {
        final String forwardedFor = request.getHeader("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwardedFor)) {
            // First entry is the original client when behind a proxy/load balancer
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    @Path("invalidate")
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(operationId = "updateConfiguration")
    @AlternativeOperationId("updateConfiguration_2")
    public String updateConfiguration(final String apiRequestBodyAsJson) {
        final CommandWrapper commandRequest = new CommandWrapperBuilder().invalidateTwoFactorAccessToken().withJson(apiRequestBodyAsJson)
                .build();
        final CommandProcessingResult result = this.commandsSourceWritePlatformService.logCommandSource(commandRequest);

        return this.toApiJsonSerializer.serialize(result);
    }

    @Path("totp/enroll")
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Start TOTP enrollment", description = "Stages a new authenticator secret for the authenticated user and returns it once, with an otpauth URI. Confirm it with POST /v1/twofactor/totp/confirm.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.PostTwoFactorTotpEnrollResponse.class)))
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated, or Fineract-Platform-TFA-Token is invalid. When a newer login superseded this session the response includes Fineract-Platform-Reason: session-superseded.", headers = {
            @Header(name = "Fineract-Platform-Reason", description = "session-superseded when this access token was revoked by a newer login", schema = @Schema(example = "session-superseded")) })
    @ApiResponse(responseCode = "403", description = "TOTP delivery is not configured, or this user is already enrolled")
    public String enrollTotp() {
        final AppUser user = context.authenticatedUser();
        final TotpEnrollmentData enrollment = totpService.enroll(user);
        return totpEnrollmentSerializer.serialize(enrollment);
    }

    @Path("totp/confirm")
    @POST
    @Produces({ MediaType.APPLICATION_JSON })
    @Operation(summary = "Confirm TOTP enrollment", description = "Checks a code from the authenticator app against the staged secret and enables TOTP for the user.")
    @ApiResponse(responseCode = "200", description = "OK", content = @Content(schema = @Schema(implementation = TwoFactorApiResourceSwagger.PostTwoFactorTotpConfirmResponse.class)))
    @ApiResponse(responseCode = "401", description = "The caller is not authenticated, or Fineract-Platform-TFA-Token is invalid. When a newer login superseded this session the response includes Fineract-Platform-Reason: session-superseded.", headers = {
            @Header(name = "Fineract-Platform-Reason", description = "session-superseded when this access token was revoked by a newer login", schema = @Schema(example = "session-superseded")) })
    @ApiResponse(responseCode = "403", description = "There is no staged secret, TOTP is already enabled, or the code does not match")
    public String confirmTotp(
            @QueryParam("token") @Parameter(description = "Current authenticator code for the staged secret") final String token) {
        final AppUser user = context.authenticatedUser();
        totpService.confirm(user, token);
        final Map<String, Object> result = new HashMap<>();
        result.put("totpEnabled", true);
        return toApiJsonSerializer.serialize(result);
    }
}
