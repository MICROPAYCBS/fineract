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
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.integrationtests.common.BusinessDateHelper;
import org.apache.fineract.integrationtests.common.FineractClientHelper;
import org.apache.fineract.integrationtests.common.SchedulerJobHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.accounting.JournalEntryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Treasury accrual and maturity (P2.2): a 30-day placement accrues daily through the "Treasury Accrual" job, a rerun
 * posts nothing, and on maturity the principal and interest come back and the deal is MATURED.
 */
public class TreasuryDealAccrualIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/treasury";
    private static final String CURRENCY = "USD";
    private static final long HEAD_OFFICE = 1L;
    private static final String PASSWORD = "A1b2c3d4e5f$";
    private static final LocalDate VALUE = LocalDate.of(2026, 3, 1);

    private RequestSpecification admin;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        MicropayCurrencyHelper.ensureSelected(CURRENCY);
        this.admin = spec(Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void placementAccruesAndMatures() {
        BusinessDateHelper.runAt("01 March 2026", () -> {
            final long bankGl = AccountHelper.createAssetGlAccount("TRS_ACC_BANK").getAccountID();
            final long fundGl = AccountHelper.createAssetGlAccount("TRS_ACC_FUND").getAccountID();
            final long placedGl = AccountHelper.createAssetGlAccount("TRS_ACC_PLACED").getAccountID();
            final long receivableGl = AccountHelper.createAssetGlAccount("TRS_ACC_RECV").getAccountID();
            final long incomeGl = AccountHelper.createIncomeGlAccount("TRS_ACC_INCOME").getAccountID();
            final long account = id(post(this.admin, "/accounts",
                    "{\"code\": \"" + code("TRS_") + "\", \"name\": \"Accrual settlement\","
                            + " \"accountType\": \"BANK\", \"glAccountId\": " + bankGl + ", \"officeId\": " + HEAD_OFFICE
                            + ", \"currencyCode\": \"" + CURRENCY + "\"}"));
            postJournalEntry(bankGl, fundGl, "1000000");
            final long counterparty = id(post(this.admin, "/counterparties",
                    "{\"name\": \"Accrual bank\", \"shortCode\": \"" + code("CP") + "\", \"counterpartyType\": \"BANK\"}"));
            final long type = id(post(this.admin, "/deal-types", "{\"code\": \"" + code("MM") + "\", \"name\": \"Placement\","
                    + " \"instrumentKind\": \"MONEY_MARKET_PLACEMENT\", \"principalGlAccountId\": " + placedGl
                    + ", \"interestReceivableGlAccountId\": " + receivableGl + ", \"interestIncomeGlAccountId\": " + incomeGl + "}"));
            final long deal = id(post(this.admin, "/deals",
                    "{\"dealTypeId\": " + type + ", \"counterpartyId\": " + counterparty + ", \"settlementAccountId\": " + account
                            + ", \"tradeDate\": \"2026-03-01\", \"valueDate\": \"2026-03-01\","
                            + " \"maturityDate\": \"2026-03-31\", \"amount\": 500000, \"rate\": 12}"));
            assertThat(post(userWith("APPROVE_TREASURYDEAL"), "/deals/" + deal + "?command=approve", "{}").statusCode()).isEqualTo(200);
            assertThat(post(userWith("CONFIRM_TREASURYDEAL"), "/deals/" + deal + "?command=confirm", "{\"confirmationReference\": \"C1\"}")
                    .statusCode()).isEqualTo(200);
            assertThat(post(userWith("SETTLE_TREASURYDEAL"), "/deals/" + deal + "?command=settle", "{}").statusCode()).isEqualTo(200);

            // day 16: the job closes the 15th, accruing the 15 days 1-15 March: 500,000 x 0.12 x 15/365 = 2,465.75
            BusinessDateHelper.updateBusinessDate(BusinessDateType.BUSINESS_DATE, VALUE.plusDays(15));
            SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_ACCR");
            JsonPath view = get("/deals/" + deal);
            assertThat(view.getString("lifecycle.accruedToDate")).isEqualTo("2026-03-16");
            assertThat(new BigDecimal(view.getString("lifecycle.interestAccrued"))).isEqualByComparingTo("2465.75");
            final int accruals = view.getList("accruals").size();
            SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_ACCR");
            assertThat(get("/deals/" + deal).getList("accruals")).hasSize(accruals);

            // after maturity: 4,931.51 interest and the principal back, deal MATURED
            BusinessDateHelper.updateBusinessDate(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 4, 1));
            SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_ACCR");
            view = get("/deals/" + deal);
            assertThat(view.getString("status")).isEqualTo("MATURED");
            assertThat(new BigDecimal(view.getString("lifecycle.interestAccrued"))).isEqualByComparingTo("4931.51");
            assertThat(view.getList("cashflows.status")).containsOnly("SETTLED");
            assertThat(new BigDecimal(get("/accounts/" + account + "/position").getString("balance"))).isEqualByComparingTo("1004931.51");
            assertThat(new BigDecimal(get("/counterparties/" + counterparty + "/exposure").getString("exposure")))
                    .isEqualByComparingTo("0");
        });
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
                        .transactionDate(VALUE).addDebitsItem(new SingleDebitOrCreditEntryCommand().glAccountId(debitGl).amount(value))
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
