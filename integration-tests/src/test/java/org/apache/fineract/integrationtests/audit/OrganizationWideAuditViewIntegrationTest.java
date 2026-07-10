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
package org.apache.fineract.integrationtests.audit;

import io.restassured.builder.RequestSpecBuilder;
import io.restassured.builder.ResponseSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.specification.RequestSpecification;
import io.restassured.specification.ResponseSpecification;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.GlobalConfigurationPropertyData;
import org.apache.fineract.client.models.PostClientsRequest;
import org.apache.fineract.client.models.PostClientsResponse;
import org.apache.fineract.client.models.PostOfficesResponse;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutGlobalConfigurationsRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.infrastructure.audit.api.OrganizationWideAuditConstants;
import org.apache.fineract.integrationtests.client.IntegrationTest;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.OfficeHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import retrofit2.Response;

/**
 * Verifies Micropay organization-wide audit view: a branch auditor can view audit trails from other branches when the
 * institution toggle and VIEW_ORGANIZATION_AUDIT permission are enabled.
 */
public class OrganizationWideAuditViewIntegrationTest extends IntegrationTest {

    private static final String AUDITOR_PASSWORD = "A1b2c3d4e5f$";
    private static final LocalDate OFFICE_OPENING_DATE = LocalDate.of(2007, 7, 1);
    private static final ResponseSpecification OK_RESPONSE = new ResponseSpecBuilder().expectStatusCode(200).build();

    private OfficeHelper officeHelper;

    @BeforeAll
    void requireOrganizationWideAuditConfig() {
        ensureOrganizationWideAuditViewConfigPresent();
    }

    @BeforeEach
    void setup() {
        officeHelper = new OfficeHelper();
        disableOrganizationWideAuditView();
    }

    @Test
    void branchAuditorCannotSeeOtherBranchClientAuditWithoutOrgWidePermission() {
        final PostOfficesResponse branchA = officeHelper.createOffice(OFFICE_OPENING_DATE);
        final PostOfficesResponse branchB = officeHelper.createOffice(OFFICE_OPENING_DATE);

        final PostClientsResponse branchBClient = createClientAtOffice(branchB.getOfficeId());

        final String branchAuditor = createAuditorClient(branchA.getOfficeId(), Map.of("READ_AUDIT", true));

        final List<HashMap<String, Object>> audits = fetchClientCreateAudits(branchAuditor, branchBClient.getClientId());
        assertThat(audits).isEmpty();
    }

    @Test
    void branchAuditorCanSeeOtherBranchClientAuditWithOrgWidePermission() {
        final PostOfficesResponse branchA = officeHelper.createOffice(OFFICE_OPENING_DATE);
        final PostOfficesResponse branchB = officeHelper.createOffice(OFFICE_OPENING_DATE);

        final PostClientsResponse branchBClient = createClientAtOffice(branchB.getOfficeId());

        enableOrganizationWideAuditView();

        final String branchAuditor = createAuditorClient(branchA.getOfficeId(),
                Map.of("READ_AUDIT", true, OrganizationWideAuditConstants.VIEW_ORGANIZATION_AUDIT_PERMISSION, true));

        final List<HashMap<String, Object>> audits = fetchClientCreateAudits(branchAuditor, branchBClient.getClientId());
        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).get("resourceId").toString()).isEqualTo(branchBClient.getClientId().toString());
    }

    private PostClientsResponse createClientAtOffice(final Long officeId) {
        final PostClientsRequest clientRequest = ClientHelper.defaultClientCreationRequest();
        clientRequest.setOfficeId(officeId);
        return ClientHelper.createClient(clientRequest);
    }

    private String createAuditorClient(final Long branchOfficeId, final Map<String, Boolean> permissions) {
        final String roleName = Utils.uniqueRandomStringGenerator("AUDIT_ROLE_", 10);
        final Long roleId = ok(fineractClient().roles.createRole(new PostRolesRequest().name(roleName).description("Audit test role")))
                .getResourceId();

        final PutRolesRoleIdPermissionsRequest permissionsRequest = new PutRolesRoleIdPermissionsRequest();
        permissions.forEach(permissionsRequest::putPermissionsItem);
        ok(fineractClient().roles.updateRolePermissions(roleId, permissionsRequest));

        final String username = Utils.uniqueRandomStringGenerator("auditor_", 8);
        ok(fineractClient().users.createUser(new PostUsersRequest().username(username).password(AUDITOR_PASSWORD)
                .repeatPassword(AUDITOR_PASSWORD).firstname("Branch").lastname("Auditor").email(username + "@test.example.org")
                .officeId(branchOfficeId).roles(List.of(roleId)).sendPasswordToEmail(false)));

        return username;
    }

    @SuppressWarnings("unchecked")
    private List<HashMap<String, Object>> fetchClientCreateAudits(final String username, final Long clientId) {
        final RequestSpecification requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, AUDITOR_PASSWORD));
        final String url = "/fineract-provider/api/v1/audits?" + Utils.TENANT_IDENTIFIER + "&entityName=CLIENT&resourceId=" + clientId
                + "&actionName=CREATE";
        return Utils.performServerGet(requestSpec, OK_RESPONSE, url, "");
    }

    private void ensureOrganizationWideAuditViewConfigPresent() {
        final Response<GlobalConfigurationPropertyData> configResponse = Calls.executeU(fineractClient().globalConfigurations
                .retrieveOneByName(OrganizationWideAuditConstants.ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW));
        if (configResponse.code() == 404) {
            throw new AssertionError("Micropay configuration '" + OrganizationWideAuditConstants.ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW
                    + "' was not found. Ensure tenant migration 3076 has been applied.");
        }
        if (configResponse.code() != 200 || configResponse.body() == null) {
            throw new AssertionError("Could not read '" + OrganizationWideAuditConstants.ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW + "' (HTTP "
                    + configResponse.code() + ").");
        }
    }

    private void enableOrganizationWideAuditView() {
        ok(fineractClient().globalConfigurations.updateConfigurationByName(
                OrganizationWideAuditConstants.ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW, new PutGlobalConfigurationsRequest().enabled(true)));
    }

    private void disableOrganizationWideAuditView() {
        ok(fineractClient().globalConfigurations.updateConfigurationByName(
                OrganizationWideAuditConstants.ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW, new PutGlobalConfigurationsRequest().enabled(false)));
    }
}
