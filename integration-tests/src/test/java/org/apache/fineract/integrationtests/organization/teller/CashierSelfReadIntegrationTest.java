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
package org.apache.fineract.integrationtests.organization.teller;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.GetOfficesResponse;
import org.apache.fineract.client.models.GetPermissionsResponse;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersCashierIdResponse;
import org.apache.fineract.client.models.GetTellersTellerIdCashiersResponse;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostTellersRequest;
import org.apache.fineract.client.models.PostTellersTellerIdCashiersRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.models.StaffCreateRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.client.util.FineractClient;
import org.apache.fineract.integrationtests.ConfigProperties;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.organisation.teller.api.TellerCashierAccessConstants;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

/**
 * Verifies Micropay cashier self-read: users with {@code READ_MY_CASHIER} may view only their own cashier assignment;
 * users with {@code READ_TELLER} may view any cashier in scope.
 */
public class CashierSelfReadIntegrationTest extends IntegrationTest {

    private static final String TEST_PASSWORD = "A1b2c3d4e5f$";
    private static final Long OFFICE_ID = OfficeHelper.HEAD_OFFICE_ID;
    private static final LocalDate TELLER_START = LocalDate.of(2007, 7, 1);
    private static final LocalDate CASHIER_START = LocalDate.of(2020, 1, 1);
    private static final LocalDate CASHIER_END = LocalDate.of(2030, 12, 31);

    @BeforeAll
    void requireFineractServerAndCashierReadPermissions() {
        final Response<GetOfficesResponse> officeResponse = Calls.executeU(fineractClient().offices.retrieveOffice(OFFICE_ID));
        if (officeResponse.code() != 200) {
            throw new AssertionError("Fineract API is not available at " + backendApiUrl() + " (HTTP " + officeResponse.code() + "). "
                    + "Run without -PcargoDisabled so Gradle starts Tomcat, or start your own instance and set BACKEND_PROTOCOL, "
                    + "BACKEND_HOST, BACKEND_PORT to match (default https://localhost:8443/fineract-provider/api/).");
        }
        ensureCashierReadPermissionsExist();
    }

    @Test
    void readMyCashierUserCanViewOwnCashier() {
        final CashierFixture fixture = createCashierFixture();

        final FineractClient tellerClient = createUserClient(fixture.ownStaffId(), Map.of(TellerCashierAccessConstants.READ_MY_CASHIER_PERMISSION,
                true));

        final GetTellersTellerIdCashiersCashierIdResponse cashier = ok(tellerClient.tellers.retrieveOneCashierForTeller(fixture.tellerId(),
                fixture.ownCashierId()));

        assertThat(cashier.getId()).isEqualTo(fixture.ownCashierId());
        assertThat(cashier.getStaffId()).isEqualTo(fixture.ownStaffId());
    }

    @Test
    void readMyCashierUserGets404ForOtherStaffCashier() {
        final CashierFixture fixture = createCashierFixture();

        final FineractClient tellerClient = createUserClient(fixture.ownStaffId(), Map.of(TellerCashierAccessConstants.READ_MY_CASHIER_PERMISSION,
                true));

        final Response<GetTellersTellerIdCashiersCashierIdResponse> response = Calls.executeU(tellerClient.tellers
                .retrieveOneCashierForTeller(fixture.tellerId(), fixture.otherCashierId()));

        assertThat(response.code()).isEqualTo(404);
    }

    @Test
    void readMyCashierUserSeesOnlyOwnCashierInList() {
        final CashierFixture fixture = createCashierFixture();

        final FineractClient tellerClient = createUserClient(fixture.ownStaffId(), Map.of(TellerCashierAccessConstants.READ_MY_CASHIER_PERMISSION,
                true));

        final GetTellersTellerIdCashiersResponse cashiers = ok(tellerClient.tellers.retrieveAllCashiersForTeller(fixture.tellerId(), null, null));

        assertThat(cashiers.getCashiers()).hasSize(1);
        assertThat(cashiers.getCashiers().get(0).getId()).isEqualTo(fixture.ownCashierId());
        assertThat(cashiers.getCashiers().get(0).getStaffId()).isEqualTo(fixture.ownStaffId());
    }

    @Test
    void readTellerUserCanViewAnyCashier() {
        final CashierFixture fixture = createCashierFixture();

        final FineractClient managerClient = createUserClient(fixture.ownStaffId(),
                Map.of(TellerCashierAccessConstants.READ_TELLER_PERMISSION, true));

        final GetTellersTellerIdCashiersCashierIdResponse ownCashier = ok(managerClient.tellers.retrieveOneCashierForTeller(fixture.tellerId(),
                fixture.ownCashierId()));
        final GetTellersTellerIdCashiersCashierIdResponse otherCashier = ok(managerClient.tellers.retrieveOneCashierForTeller(fixture.tellerId(),
                fixture.otherCashierId()));

        assertThat(ownCashier.getId()).isEqualTo(fixture.ownCashierId());
        assertThat(otherCashier.getId()).isEqualTo(fixture.otherCashierId());
    }

    @Test
    void userWithoutCashierReadPermissionGets403() {
        final CashierFixture fixture = createCashierFixture();

        final FineractClient restrictedClient = createUserClient(fixture.ownStaffId(), Map.of("READ_CLIENT", true));

        final Response<GetTellersTellerIdCashiersCashierIdResponse> response = Calls.executeU(restrictedClient.tellers
                .retrieveOneCashierForTeller(fixture.tellerId(), fixture.ownCashierId()));

        assertThat(response.code()).isEqualTo(403);
    }

    private CashierFixture createCashierFixture() {
        final Long ownStaffId = createStaff();
        final Long otherStaffId = createStaff();
        final Long tellerId = createTeller();
        final Long ownCashierId = createCashier(tellerId, ownStaffId);
        final Long otherCashierId = createCashier(tellerId, otherStaffId);
        return new CashierFixture(tellerId, ownStaffId, otherStaffId, ownCashierId, otherCashierId);
    }

    private Long createStaff() {
        return ok(fineractClient().staff.createStaff(new StaffCreateRequest().officeId(OFFICE_ID).firstname(Utils.randomFirstNameGenerator())
                .lastname(Utils.randomLastNameGenerator()).joiningDate(TELLER_START.toString()).dateFormat(FineractClient.DATE_FORMAT)
                .locale("en"))).getResourceId();
    }

    private Long createTeller() {
        return ok(fineractClient().tellers.createTeller(new PostTellersRequest().officeId(OFFICE_ID)
                .name(Utils.uniqueRandomStringGenerator("Teller_", 8)).description("Cashier read test teller").startDate(TELLER_START)
                .status(PostTellersRequest.StatusEnum.ACTIVE).dateFormat(FineractClient.DATE_FORMAT).locale("en"))).getResourceId();
    }

    private Long createCashier(final Long tellerId, final Long staffId) {
        return ok(fineractClient().tellers.createCashierForTeller(tellerId,
                new PostTellersTellerIdCashiersRequest().staffId(staffId).startDate(CASHIER_START).endDate(CASHIER_END).isFullDay(true)
                        .description("Cashier read test").dateFormat(FineractClient.DATE_FORMAT).locale("en")))
                .getResourceId();
    }

    private FineractClient createUserClient(final Long staffId, final Map<String, Boolean> permissions) {
        final String roleName = Utils.uniqueRandomStringGenerator("CASHIER_ROLE_", 10);
        final Long roleId = ok(fineractClient().roles.createRole(new PostRolesRequest().name(roleName).description("Cashier read test role")))
                .getResourceId();

        final PutRolesRoleIdPermissionsRequest permissionsRequest = new PutRolesRoleIdPermissionsRequest();
        permissions.forEach(permissionsRequest::putPermissionsItem);
        ok(fineractClient().roles.updateRolePermissions(roleId, permissionsRequest));

        final String username = Utils.uniqueRandomStringGenerator("teller_", 8);
        ok(fineractClient().users.createUser(new PostUsersRequest().username(username).password(TEST_PASSWORD).repeatPassword(TEST_PASSWORD)
                .firstname("Test").lastname("Teller").email(username + "@test.example.org").officeId(OFFICE_ID).staffId(staffId)
                .roles(List.of(roleId)).sendPasswordToEmail(false)));

        return newFineractClient(username, TEST_PASSWORD);
    }

    private void ensureCashierReadPermissionsExist() {
        final List<GetPermissionsResponse> permissions = ok(fineractClient().permissions.retrieveAllPermissions());
        final boolean hasReadMyCashier = permissions.stream()
                .anyMatch(p -> TellerCashierAccessConstants.READ_MY_CASHIER_PERMISSION.equals(p.getCode()));
        final boolean hasReadTeller = permissions.stream()
                .anyMatch(p -> TellerCashierAccessConstants.READ_TELLER_PERMISSION.equals(p.getCode()));
        if (!hasReadMyCashier || !hasReadTeller) {
            throw new AssertionError("Micropay cashier read permissions were not found. Ensure tenant migration 3058 has been applied.");
        }
    }

    private static String backendApiUrl() {
        return ConfigProperties.Backend.PROTOCOL + "://" + ConfigProperties.Backend.HOST + ":" + ConfigProperties.Backend.PORT
                + "/fineract-provider/api/";
    }

    private record CashierFixture(Long tellerId, Long ownStaffId, Long otherStaffId, Long ownCashierId, Long otherCashierId) {}
}
