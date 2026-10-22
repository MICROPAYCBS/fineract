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
 * Treasury funding transfers (P1.2): request, approve, send and receive by three different users, with cash in transit
 * counted once and the GL moved at send and receive.
 */
public class TreasuryTransferIntegrationTest {

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
    void transferMovesThroughCashInTransit() {
        final long inTransitGl = AccountHelper.createAssetGlAccount("TRS_TRANSIT").getAccountID();
        final long bankGl = AccountHelper.createAssetGlAccount("TRS_BANK").getAccountID();
        final long vaultGl = AccountHelper.createAssetGlAccount("TRS_VAULT").getAccountID();
        final long fundingGl = AccountHelper.createAssetGlAccount("TRS_FUND").getAccountID();
        assertThat(
                put(this.admin, "/settings", "{\"currencyCode\": \"" + CURRENCY + "\", \"cashInTransitGlAccountId\": " + inTransitGl + "}")
                        .statusCode())
                .isEqualTo(200);
        final long bank = createAccount(bankGl, "BANK");
        final long vault = createAccount(vaultGl, "VAULT");
        postJournalEntry(bankGl, fundingGl, "1000");

        final RequestSpecification approver = userWith("APPROVE_TREASURYTRANSFER", "READ_TREASURYTRANSFER");
        final RequestSpecification sender = userWith("SEND_TREASURYTRANSFER", "RECEIVE_TREASURYTRANSFER", "READ_TREASURYTRANSFER");

        final long transferId = post(this.admin, "/transfers",
                "{\"fromAccountId\": " + bank + ", \"toAccountId\": " + vault + ", \"amount\": 400, \"reference\": \"IT-1\"}").jsonPath()
                .getLong("resourceId");

        // the requester may not approve
        assertThat(post(this.admin, "/transfers/" + transferId + "?command=approve", "{}").asString()).contains("same.user");
        assertThat(post(approver, "/transfers/" + transferId + "?command=approve", "{}").statusCode()).isEqualTo(200);
        assertThat(position(bank).getDouble("pendingOut")).isEqualTo(400.0);
        assertThat(position(bank).getDouble("balance")).isEqualTo(1000.0);

        // the approver may not send
        assertThat(post(approver, "/transfers/" + transferId + "?command=send", "{}").statusCode()).isNotEqualTo(200);
        assertThat(post(sender, "/transfers/" + transferId + "?command=send", "{}").statusCode()).isEqualTo(200);
        assertThat(position(bank).getDouble("balance")).isEqualTo(600.0);
        assertThat(position(bank).getDouble("pendingOut")).isZero();
        assertThat(position(vault).getDouble("pendingIn")).isEqualTo(400.0);
        assertThat(position(vault).getDouble("projectedBalance")).isEqualTo(400.0);
        final List<Map<String, Object>> inTransit = get(this.admin, "/transfers/in-transit").getList("$");
        assertThat(inTransit).anySatisfy(row -> assertThat(((Number) row.get("id")).longValue()).isEqualTo(transferId));
        assertThat(post(this.admin, "/transfers/" + transferId + "?command=cancel", "{}").asString()).contains("cannot.cancel");

        assertThat(post(sender, "/transfers/" + transferId + "?command=receive", "{}").statusCode()).isEqualTo(200);
        assertThat(position(vault).getDouble("balance")).isEqualTo(400.0);
        assertThat(position(vault).getDouble("pendingIn")).isZero();

        final JsonPath transfer = get(this.admin, "/transfers/" + transferId);
        assertThat(transfer.getString("status")).isEqualTo("RECEIVED");
        assertThat(transfer.getString("sendTransactionId")).isEqualTo("TR-TFS-" + transferId);
        assertThat(transfer.getString("receiveTransactionId")).isEqualTo("TR-TFR-" + transferId);
    }

    @Test
    void sendIsBlockedWithoutTheBalance() {
        final long inTransitGl = AccountHelper.createAssetGlAccount("TRS_TRANSIT").getAccountID();
        put(this.admin, "/settings", "{\"currencyCode\": \"" + CURRENCY + "\", \"cashInTransitGlAccountId\": " + inTransitGl
                + ", \"allowInsufficientBalance\": false}");
        final long bank = createAccount(AccountHelper.createAssetGlAccount("TRS_EMPTY").getAccountID(), "BANK");
        final long vault = createAccount(AccountHelper.createAssetGlAccount("TRS_VAULT").getAccountID(), "VAULT");
        final RequestSpecification approver = userWith("APPROVE_TREASURYTRANSFER");
        final RequestSpecification sender = userWith("SEND_TREASURYTRANSFER");

        final long transferId = post(this.admin, "/transfers",
                "{\"fromAccountId\": " + bank + ", \"toAccountId\": " + vault + ", \"amount\": 5}").jsonPath().getLong("resourceId");
        post(approver, "/transfers/" + transferId + "?command=approve", "{}");

        assertThat(post(sender, "/transfers/" + transferId + "?command=send", "{}").asString()).contains("insufficient.balance");
        assertThat(post(this.admin, "/transfers/" + transferId + "?command=cancel", "{\"note\": \"No funds\"}").statusCode())
                .isEqualTo(200);
        assertThat(get(this.admin, "/transfers/" + transferId).getString("status")).isEqualTo("CANCELLED");
    }

    @Test
    void transferBetweenCurrenciesIsRejected() {
        final long ugx = createAccount(AccountHelper.createAssetGlAccount("TRS_UGX").getAccountID(), "BANK", "UGX");
        final long usd = createAccount(AccountHelper.createAssetGlAccount("TRS_USD").getAccountID(), "BANK", CURRENCY);

        assertThat(
                post(this.admin, "/transfers", "{\"fromAccountId\": " + ugx + ", \"toAccountId\": " + usd + ", \"amount\": 5}").asString())
                .contains("currency.mismatch");
    }

    private long createAccount(final long glAccountId, final String type) {
        return createAccount(glAccountId, type, CURRENCY);
    }

    private long createAccount(final long glAccountId, final String type, final String currency) {
        MicropayCurrencyHelper.ensureSelected(currency);
        final String code = Utils.uniqueRandomStringGenerator("TRS_", 8);
        final Response response = post(this.admin, "/accounts",
                "{\"code\": \"" + code + "\", \"name\": \"Treasury " + code + "\", \"accountType\": \"" + type + "\", \"glAccountId\": "
                        + glAccountId + ", \"officeId\": " + HEAD_OFFICE + ", \"currencyCode\": \"" + currency + "\"}");
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath().getLong("resourceId");
    }

    private RequestSpecification userWith(final String... permissions) {
        final Long roleId = Calls
                .ok(FineractClientHelper.getFineractClient().roles.createRole(
                        new PostRolesRequest().name(Utils.uniqueRandomStringGenerator("TRS_ROLE_", 8)).description("Treasury test")))
                .getResourceId();
        final PutRolesRoleIdPermissionsRequest grant = new PutRolesRoleIdPermissionsRequest();
        grant.putPermissionsItem("READ_TREASURYACCOUNT", true);
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

    private JsonPath position(final long accountId) {
        return get(this.admin, "/accounts/" + accountId + "/position");
    }

    private static void postJournalEntry(final long debitGl, final long creditGl, final String amount) {
        final BigDecimal value = new BigDecimal(amount);
        JournalEntryHelper.createJournalEntry(null,
                new JournalEntryCommand().amount(value).officeId(HEAD_OFFICE).currencyCode(CURRENCY).locale("en").dateFormat("uuuu-MM-dd")
                        .transactionDate(Utils.getLocalDateOfTenant())
                        .addDebitsItem(new SingleDebitOrCreditEntryCommand().glAccountId(debitGl).amount(value))
                        .addCreditsItem(new SingleDebitOrCreditEntryCommand().glAccountId(creditGl).amount(value)));
    }

    private static RequestSpecification spec(final String basicAuthKey) {
        final RequestSpecification spec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        spec.header("Authorization", "Basic " + basicAuthKey);
        return spec;
    }

    private static String url(final String path) {
        return API + path + (path.contains("?") ? "&" : "?") + Utils.TENANT_IDENTIFIER;
    }

    private static JsonPath get(final RequestSpecification spec, final String path) {
        final Response response = given().spec(spec).when().get(url(path)).andReturn();
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
