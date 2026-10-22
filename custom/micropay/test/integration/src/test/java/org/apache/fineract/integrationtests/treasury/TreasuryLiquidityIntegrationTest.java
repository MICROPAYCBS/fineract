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
package org.apache.fineract.integrationtests.treasury;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

import com.micropay.fineract.integrationtests.MicropayCurrencyHelper;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Treasury liquidity ladder (P3.1): an assumption set is activated by another user, a ladder item is configured, and a
 * ladder run stores lines whose opening stock matches the treasury account positions; a rerun replaces it.
 */
public class TreasuryLiquidityIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/treasury";
    private static final String CURRENCY = "USD";
    private static final String PASSWORD = "A1b2c3d4e5f$";

    private RequestSpecification admin;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        MicropayCurrencyHelper.ensureSelected(CURRENCY);
        this.admin = spec(Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void ladderRunWithAnActivatedAssumptionSetAndALadderItem() {
        final String code = Utils.uniqueRandomStringGenerator("IT_", 8);
        final Response draft = post(this.admin, "/assumptions",
                "{\"code\": \"" + code + "\", \"name\": \"IT assumptions\", \"effectiveDate\": \"2020-01-01\","
                        + " \"assumptions\": [{\"type\": \"CORE_SHARE\", \"source\": \"SAVINGS\", \"rate\": 70},"
                        + " {\"type\": \"RUN_OFF\", \"source\": \"SAVINGS\", \"bucketCode\": \"ON\", \"rate\": 10}]}");
        assertThat(draft.statusCode()).as(draft.asString()).isEqualTo(200);
        final long setId = draft.jsonPath().getLong("resourceId");
        final RequestSpecification checker = userWith("READ_TREASURYASSUMPTIONSET", "ACTIVATE_TREASURYASSUMPTIONSET");
        assertThat(post(checker, "/assumptions/" + setId + "?command=activate", "{}").statusCode()).isEqualTo(200);
        assertThat(get("/assumptions/" + setId).getString("status")).isEqualTo("ACTIVE");
        assertThat(put(this.admin, "/assumptions/" + setId, "{\"name\": \"Changed\"}").statusCode()).isEqualTo(403);

        assertThat(post(this.admin, "/ladder-items", "{\"name\": \"IT salaries\", \"direction\": \"OUTFLOW\", \"amount\": 12345,"
                + " \"currencyCode\": \"" + CURRENCY + "\", \"dayOfMonth\": 25, \"activeFrom\": \"2020-01-01\"}").statusCode())
                .isEqualTo(200);

        final Response run = post(this.admin, "/runs?type=ladder&currency=" + CURRENCY + "&assumptionSetId=" + setId, "{}");
        assertThat(run.statusCode()).as(run.asString()).isEqualTo(200);
        final long runId = run.jsonPath().getLong("resourceId");
        final JsonPath view = get("/runs/" + runId);
        assertThat(view.getInt("assumptionSetId")).isEqualTo((int) setId);
        assertThat(view.getList("buckets")).isNotEmpty();
        final List<Map<String, Object>> lines = get("/runs/" + runId + "/lines").getList("$");
        assertThat(lines).anyMatch(l -> "FIXED".equals(l.get("source")) && "IT salaries".equals(l.get("productName")));
        final BigDecimal opening = lines.stream().filter(l -> "OPENING".equals(l.get("source")))
                .map(l -> new BigDecimal(String.valueOf(l.get("contractual")))).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(new BigDecimal(view.getString("opening"))).isEqualByComparingTo(opening);

        final Response rerun = post(this.admin, "/runs?type=ladder&currency=" + CURRENCY + "&assumptionSetId=" + setId, "{}");
        assertThat(rerun.statusCode()).isEqualTo(200);
        assertThat(given().spec(this.admin).when().get(url("/runs/" + runId)).andReturn().statusCode()).isEqualTo(403);
    }

    private static RequestSpecification userWith(final String... permissions) {
        final Long roleId = Calls
                .ok(FineractClientHelper.getFineractClient().roles.createRole(
                        new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("TRS_ROLE_", 8)).description("Treasury test")))
                .getResourceId();
        final PutRolesRoleIdPermissionsRequest grant = new PutRolesRoleIdPermissionsRequest();
        for (final String permission : permissions) {
            grant.putPermissionsItem(permission, true);
        }
        Calls.ok(FineractClientHelper.getFineractClient().roles.updateRolePermissions(roleId, grant));
        final String username = Utils.uniqueRandomStringGenerator("trs_", 8);
        Calls.ok(FineractClientHelper.getFineractClient().users.createUser(new PostUsersRequest().username(username).password(PASSWORD)
                .repeatPassword(PASSWORD).firstname("Treasury").lastname("Tester").email(username + "@test.example.org").officeId(1L)
                .roles(List.of(roleId)).sendPasswordToEmail(false)));
        return spec(Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, PASSWORD));
    }

    private static RequestSpecification spec(final String basicAuthKey) {
        final RequestSpecification spec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        spec.header("Authorization", "Basic " + basicAuthKey);
        return spec;
    }

    private static String url(final String path) {
        return API + path + (path.contains("?") ? "&" : "?") + Utils.TENANT_IDENTIFIER;
    }

    private JsonPath get(final String path) {
        final Response response = given().spec(this.admin).when().get(url(path)).andReturn();
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath();
    }

    private static Response post(final RequestSpecification spec, final String path, final String json) {
        return given().spec(spec).body(json).when().post(url(path)).andReturn();
    }

    private static Response put(final RequestSpecification spec, final String path, final String json) {
        return given().spec(spec).body(json).when().put(url(path)).andReturn();
    }
}
