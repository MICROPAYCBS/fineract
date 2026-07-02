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
package org.apache.fineract.integrationtests.interbranch;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.GetClientsClientIdResponse;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.GlobalConfigurationPropertyData;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostOfficesResponse;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.FineractClient;
import org.apache.fineract.infrastructure.interbranch.api.CrossBranchServicingConstants;
import org.apache.fineract.integrationtests.ConfigProperties;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

/**
 * Verifies Micropay cross-branch client visibility: a cashier at branch A can view a client whose home office is branch
 * B when cross-branch servicing is enabled and the user has the view permission.
 */
public class CrossBranchClientVisibilityIntegrationTest extends IntegrationTest {

    private static final String CASHIER_PASSWORD = "A1b2c3d4e5f$";
    private static final LocalDate OFFICE_OPENING_DATE = LocalDate.of(2007, 7, 1);

    private OfficeHelper officeHelper;

    @BeforeAll
    void requireFineractServerAndCrossBranchConfig() {
        final Response<GetOfficesResponse> officeResponse = Calls.executeU(fineractClient().offices.retrieveOffice(1L));
        if (officeResponse.code() != 200) {
            throw new AssertionError("Fineract API is not available at " + backendApiUrl() + " (HTTP " + officeResponse.code() + "). "
                    + "Run without -PcargoDisabled so Gradle starts Tomcat, or start your own instance and set BACKEND_PROTOCOL, "
                    + "BACKEND_HOST, BACKEND_PORT to match (default https://localhost:8443/fineract-provider/api/).");
        }
        ensureCrossBranchServicingEnabled();
    }

    @BeforeEach
    void setup() {
        officeHelper = new OfficeHelper();
    }

    @Test
    void cashierAtBranchACanViewClientAtBranchBWithCrossBranchPermission() {
        final PostOfficesResponse branchA = officeHelper.createOffice(OFFICE_OPENING_DATE);
        final PostOfficesResponse branchB = officeHelper.createOffice(OFFICE_OPENING_DATE);

        final PostClientsRequest clientRequest = ClientHelper.defaultClientCreationRequest();
        clientRequest.setOfficeId(branchB.getOfficeId());
        final PostClientsResponse branchBClient = ClientHelper.createClient(clientRequest);

        final FineractClient cashierClient = createCashierClient(branchA.getOfficeId(), Map.of("READ_CLIENT", true,
                CrossBranchServicingConstants.VIEW_OTHER_BRANCH_CLIENT_PERMISSION, true));

        final GetClientsClientIdResponse retrieved = ok(
                cashierClient.clients.retrieveOneClient(branchBClient.getClientId(), false));

        assertThat(retrieved.getId()).isEqualTo(branchBClient.getClientId());
        assertThat(retrieved.getOfficeId()).isEqualTo(branchB.getOfficeId());
    }

    @Test
    void cashierAtBranchACannotViewBranchBClientWithoutViewOtherBranchPermission() {
        final PostOfficesResponse branchA = officeHelper.createOffice(OFFICE_OPENING_DATE);
        final PostOfficesResponse branchB = officeHelper.createOffice(OFFICE_OPENING_DATE);

        final PostClientsRequest clientRequest = ClientHelper.defaultClientCreationRequest();
        clientRequest.setOfficeId(branchB.getOfficeId());
        final PostClientsResponse branchBClient = ClientHelper.createClient(clientRequest);

        final FineractClient cashierClient = createCashierClient(branchA.getOfficeId(), Map.of("READ_CLIENT", true));

        final Response<GetClientsClientIdResponse> response = Calls
                .executeU(cashierClient.clients.retrieveOneClient(branchBClient.getClientId(), false));

        assertThat(response.code()).isEqualTo(404);
    }

    private FineractClient createCashierClient(final Long branchOfficeId, final Map<String, Boolean> permissions) {
        final String roleName = Utils.uniqueRandomStringGenerator("TEST_ROLE_", 10);
        final Long roleId = ok(fineractClient().roles.createRole(new PostRolesRequest().name(roleName).description("Cross-branch test role")))
                .getResourceId();

        final PutRolesRoleIdPermissionsRequest permissionsRequest = new PutRolesRoleIdPermissionsRequest();
        permissions.forEach(permissionsRequest::putPermissionsItem);
        ok(fineractClient().roles.updateRolePermissions(roleId, permissionsRequest));

        final String cashierUsername = Utils.uniqueRandomStringGenerator("cashier_", 8);
        ok(fineractClient().users.createUser(new PostUsersRequest().username(cashierUsername).password(CASHIER_PASSWORD)
                .repeatPassword(CASHIER_PASSWORD).firstname("Branch").lastname("Cashier").email(cashierUsername + "@test.example.org")
                .officeId(branchOfficeId).roles(List.of(roleId)).sendPasswordToEmail(false)));

        return newFineractClient(cashierUsername, CASHIER_PASSWORD);
    }

    private void ensureCrossBranchServicingEnabled() {
        final Response<GlobalConfigurationPropertyData> configResponse = Calls.executeU(fineractClient().globalConfigurations
                .retrieveOneByName(CrossBranchServicingConstants.ENABLE_CROSS_BRANCH_SERVICING));
        if (configResponse.code() == 404) {
            throw new AssertionError("Micropay configuration '" + CrossBranchServicingConstants.ENABLE_CROSS_BRANCH_SERVICING
                    + "' was not found. Ensure tenant migrations 3042 and 3055 have been applied.");
        }
        if (configResponse.code() != 200 || configResponse.body() == null) {
            throw new AssertionError("Could not read '" + CrossBranchServicingConstants.ENABLE_CROSS_BRANCH_SERVICING + "' (HTTP "
                    + configResponse.code() + ").");
        }
        if (!Boolean.TRUE.equals(configResponse.body().getEnabled())) {
            ok(fineractClient().globalConfigurations.updateConfigurationByName(
                    CrossBranchServicingConstants.ENABLE_CROSS_BRANCH_SERVICING, new PutGlobalConfigurationsRequest().enabled(true)));
        }
    }

    private static String backendApiUrl() {
        return ConfigProperties.Backend.PROTOCOL + "://" + ConfigProperties.Backend.HOST + ":" + ConfigProperties.Backend.PORT
                + "/fineract-provider/api/";
    }
}
