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
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.client.models.JournalEntryCommand;
import org.apache.fineract.client.models.PostRolesRequest;
import org.apache.fineract.client.models.PostUsersRequest;
import org.apache.fineract.client.models.PutRolesRoleIdPermissionsRequest;
import org.apache.fineract.client.models.SingleDebitOrCreditEntryCommand;
import org.apache.fineract.client.util.Calls;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.accounting.JournalEntryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Treasury deals (P2.1): a money market placement captured, approved, confirmed and settled by different users, within
 * its counterparty's limit, and a second placement refused above it.
 */
public class TreasuryDealIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/treasury";
    private static final String CURRENCY = "USD";
    private static final long HEAD_OFFICE = 1L;
    private static final String PASSWORD = "A1b2c3d4e5f$";

    private RequestSpecification admin;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        MicropayCurrencyHelper.ensureSelected(CURRENCY);
        this.admin = spec(Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void placementIsCapturedApprovedConfirmedAndSettled() {
        final long bankGl = AccountHelper.createAssetGlAccount("TRS_DEAL_BANK").getAccountID();
        final long fundGl = AccountHelper.createAssetGlAccount("TRS_DEAL_FUND").getAccountID();
        final long placementGl = AccountHelper.createAssetGlAccount("TRS_DEAL_PLACED").getAccountID();
        final long receivableGl = AccountHelper.createAssetGlAccount("TRS_DEAL_RECV").getAccountID();
        final long incomeGl = AccountHelper.createIncomeGlAccount("TRS_DEAL_INCOME").getAccountID();
        final long account = createAccount(bankGl);
        postJournalEntry(bankGl, fundGl, "1000");

        final long counterparty = id(post(this.admin, "/counterparties", "{\"name\": \"Test Bank\", \"shortCode\": \"" + code("CP")
                + "\", \"counterpartyType\": \"BANK\", \"exposureLimit\": 1000, \"limitCurrencyCode\": \"" + CURRENCY + "\"}"));
        final long type = id(post(this.admin, "/deal-types",
                "{\"code\": \"" + code("MM") + "\", \"name\": \"Placement\","
                        + " \"instrumentKind\": \"MONEY_MARKET_PLACEMENT\", \"principalGlAccountId\": " + placementGl
                        + ", \"interestReceivableGlAccountId\": " + receivableGl + ", \"interestIncomeGlAccountId\": " + incomeGl + "}"));

        final LocalDate today = Utils.getLocalDateOfTenant();
        final String deal = "{\"dealTypeId\": " + type + ", \"counterpartyId\": " + counterparty + ", \"settlementAccountId\": " + account
                + ", \"tradeDate\": \"" + today + "\", \"valueDate\": \"" + today + "\", \"maturityDate\": \"" + today.plusDays(90)
                + "\", \"amount\": 850, \"rate\": 12}";
        final Response captured = post(this.admin, "/deals", deal);
        assertThat(captured.statusCode()).as(captured.asString()).isEqualTo(200);
        assertThat(captured.jsonPath().getString("changes.warning")).contains("85.00%");
        final long dealId = captured.jsonPath().getLong("resourceId");

        final RequestSpecification approver = userWith("APPROVE_TREASURYDEAL");
        final RequestSpecification confirmer = userWith("CONFIRM_TREASURYDEAL");
        final RequestSpecification settler = userWith("SETTLE_TREASURYDEAL");
        assertThat(post(this.admin, "/deals/" + dealId + "?command=approve", "{}").asString()).contains("same.user");
        assertThat(post(approver, "/deals/" + dealId + "?command=approve", "{}").statusCode()).isEqualTo(200);
        assertThat(post(confirmer, "/deals/" + dealId + "?command=confirm", "{\"confirmationReference\": \"MT320-1\"}").statusCode())
                .isEqualTo(200);
        final Response settled = post(settler, "/deals/" + dealId + "?command=settle", "{}");
        assertThat(settled.statusCode()).as(settled.asString()).isEqualTo(200);

        final JsonPath view = get("/deals/" + dealId);
        assertThat(view.getString("status")).isEqualTo("SETTLED");
        assertThat(view.getString("settlementTransactionId")).isEqualTo("TR-DS-" + dealId);
        assertThat(view.getList("cashflows")).hasSize(2);
        assertThat(view.getList("events.eventType")).contains("CAPTURED", "LIMIT_WARNING", "APPROVED", "CONFIRMED", "SETTLED");
        assertThat(new BigDecimal(get("/accounts/" + account + "/position").getString("balance"))).isEqualByComparingTo("150");
        assertThat(new BigDecimal(get("/counterparties/" + counterparty + "/exposure").getString("exposure"))).isEqualByComparingTo("850");

        final Response second = post(this.admin, "/deals", deal.replace("\"amount\": 850", "\"amount\": 200"));
        assertThat(second.asString()).contains("limit.exceeded");
    }

    private long createAccount(final long glAccountId) {
        final Response response = post(this.admin, "/accounts",
                "{\"code\": \"" + code("TRS_") + "\", \"name\": \"Deal settlement\"," + " \"accountType\": \"BANK\", \"glAccountId\": "
                        + glAccountId + ", \"officeId\": " + HEAD_OFFICE + ", \"currencyCode\": \"" + CURRENCY + "\"}");
        return id(response);
    }

    private static RequestSpecification userWith(final String... permissions) {
        final Long roleId = Calls
                .ok(FineractClientHelper.getFineractClient().roles.createRole(
                        new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("TRS_ROLE_", 8)).description("Treasury test")))
                .getResourceId();
        final PutRolesRoleIdPermissionsRequest grant = new PutRolesRoleIdPermissionsRequest();
        grant.putPermissionsItem("READ_TREASURYDEAL", true);
        for (final String permission : permissions) {
            grant.putPermissionsItem(permission, true);
        }
        Calls.ok(FineractClientHelper.getFineractClient().roles.updateRolePermissions(roleId, grant));
        final String username = Utils.uniqueRandomStringGenerator("trs_", 8);
        Calls.ok(FineractClientHelper.getFineractClient().users.createUser(new PostUsersRequest().username(username).password(PASSWORD)
                .repeatPassword(PASSWORD).firstname("Treasury").lastname("Tester").email(username + "@test.example.org")
                .officeId(HEAD_OFFICE).roles(List.of(roleId)).sendPasswordToEmail(false)));
        return spec(Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, PASSWORD));
    }

    private static void postJournalEntry(final long debitGl, final long creditGl, final String amount) {
        final BigDecimal value = new BigDecimal(amount);
        JournalEntryHelper.createJournalEntry(null,
                new JournalEntryCommand().amount(value).officeId(HEAD_OFFICE).currencyCode(CURRENCY).locale("en").dateFormat("uuuu-MM-dd")
                        .transactionDate(Utils.getLocalDateOfTenant())
                        .addDebitsItem(new SingleDebitOrCreditEntryCommand().glAccountId(debitGl).amount(value))
                        .addCreditsItem(new SingleDebitOrCreditEntryCommand().glAccountId(creditGl).amount(value)));
    }

    private static String code(final String prefix) {
        return Utils.uniqueRandomStringGenerator(prefix, 8);
    }

    private static long id(final Response response) {
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath().getLong("resourceId");
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
}
