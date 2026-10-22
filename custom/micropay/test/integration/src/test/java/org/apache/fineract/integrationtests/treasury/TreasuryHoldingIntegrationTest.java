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
import java.math.RoundingMode;
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
 * Treasury holdings (P2.3): a bond at FVOCI is revalued by the "Treasury Valuation" job, gets an expected credit loss,
 * is pledged (and cannot be sold while pledged), then sold: its fair value adjustment and loss allowance leave with it.
 */
public class TreasuryHoldingIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/treasury";
    private static final String CURRENCY = "USD";
    private static final long HEAD_OFFICE = 1L;
    private static final String PASSWORD = "A1b2c3d4e5f$";
    private static final LocalDate VALUE = LocalDate.of(2026, 3, 2);

    private RequestSpecification admin;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        MicropayCurrencyHelper.ensureSelected(CURRENCY);
        this.admin = spec(Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void bondAtFvociIsRevaluedImpairedPledgedAndSold() {
        BusinessDateHelper.runAt("02 March 2026", () -> {
            final long bankGl = AccountHelper.createAssetGlAccount("TRS_HLD_BANK").getAccountID();
            final long fundGl = AccountHelper.createAssetGlAccount("TRS_HLD_FUND").getAccountID();
            final String isin = Utils.uniqueRandomStringGenerator("UG", 9).toUpperCase() + "7";
            final StringBuilder gls = new StringBuilder();
            gls.append("\"principalGlAccountId\": ").append(AccountHelper.createAssetGlAccount("TRS_HLD_BOND").getAccountID())
                    .append(", \"interestReceivableGlAccountId\": ")
                    .append(AccountHelper.createAssetGlAccount("TRS_HLD_RECV").getAccountID()).append(", \"interestIncomeGlAccountId\": ")
                    .append(AccountHelper.createIncomeGlAccount("TRS_HLD_INC").getAccountID()).append(", \"premiumGlAccountId\": ")
                    .append(AccountHelper.createAssetGlAccount("TRS_HLD_PREM").getAccountID()).append(", \"discountGlAccountId\": ")
                    .append(AccountHelper.createAssetGlAccount("TRS_HLD_DISC").getAccountID()).append(", \"realisedGainGlAccountId\": ")
                    .append(AccountHelper.createIncomeGlAccount("TRS_HLD_GAIN").getAccountID()).append(", \"realisedLossGlAccountId\": ")
                    .append(AccountHelper.createExpenseGlAccount("TRS_HLD_LOSS").getAccountID())
                    .append(", \"fairValueAdjustmentGlAccountId\": ")
                    .append(AccountHelper.createAssetGlAccount("TRS_HLD_FVA").getAccountID()).append(", \"fairValueReserveGlAccountId\": ")
                    .append(AccountHelper.createEquityGlAccount("TRS_HLD_OCI").getAccountID())
                    .append(", \"impairmentExpenseGlAccountId\": ")
                    .append(AccountHelper.createExpenseGlAccount("TRS_HLD_IMP").getAccountID());
            final long account = id(post(this.admin, "/accounts",
                    "{\"code\": \"" + code("TRS_") + "\", \"name\": \"Holding settlement\", \"accountType\": \"BANK\", \"glAccountId\": "
                            + bankGl + ", \"officeId\": " + HEAD_OFFICE + ", \"currencyCode\": \"" + CURRENCY + "\"}"));
            postJournalEntry(bankGl, fundGl, "200000000");
            final long counterparty = id(post(this.admin, "/counterparties", "{\"name\": \"Government\", \"shortCode\": \"" + code("GV")
                    + "\", \"counterpartyType\": \"GOVERNMENT\", \"rating\": \"" + code("R") + "\"}"));
            final long pledgee = id(post(this.admin, "/counterparties",
                    "{\"name\": \"Central bank\", \"shortCode\": \"" + code("CB") + "\", \"counterpartyType\": \"CENTRAL_BANK\"}"));
            final long type = id(post(this.admin, "/deal-types", "{\"code\": \"" + code("BD") + "\", \"name\": \"Bond at FVOCI\","
                    + " \"instrumentKind\": \"GOVERNMENT_BOND\", \"defaultDayCount\": \"ACT_ACT\", " + gls + "}"));
            final long deal = id(post(this.admin, "/deals",
                    "{\"dealTypeId\": " + type + ", \"counterpartyId\": " + counterparty + ", \"settlementAccountId\": " + account
                            + ", \"tradeDate\": \"2026-03-02\", \"valueDate\": \"2026-03-02\", \"maturityDate\": \"2031-03-15\","
                            + " \"amount\": 100000000, \"rate\": 14, \"cleanPrice\": 98.5, \"isin\": \"" + isin
                            + "\", \"classification\": \"FVOCI\"}"));
            assertThat(post(userWith("APPROVE_TREASURYDEAL"), "/deals/" + deal + "?command=approve", "{}").statusCode()).isEqualTo(200);
            assertThat(post(userWith("CONFIRM_TREASURYDEAL"), "/deals/" + deal + "?command=confirm", "{\"confirmationReference\": \"C\"}")
                    .statusCode()).isEqualTo(200);
            assertThat(post(userWith("SETTLE_TREASURYDEAL"), "/deals/" + deal + "?command=settle", "{}").statusCode()).isEqualTo(200);

            // the 15th closes at 99.00: the adjustment is fair value less amortised cost, posted to OCI
            assertThat(post(this.admin, "/prices", "{\"instrumentId\": \"" + isin + "\", \"priceDate\": \"2026-03-13\", \"cleanPrice\": 99,"
                    + " \"source\": \"BOU_SECONDARY\"}").statusCode()).isEqualTo(200);
            BusinessDateHelper.updateBusinessDate(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 3, 16));
            SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_ACCR");
            SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_VAL");
            JsonPath view = get("/deals/" + deal);
            final BigDecimal amortisedCost = new BigDecimal("98500000")
                    .add(new BigDecimal(view.getString("lifecycle.amortisationAccrued")));
            assertThat(new BigDecimal(view.getString("holding.lastFairValue"))).isEqualByComparingTo("99000000");
            assertThat(new BigDecimal(view.getString("holding.fairValueAdjustment")))
                    .isEqualByComparingTo(new BigDecimal("99000000").subtract(amortisedCost));
            assertThat(view.getList("valuations")).hasSize(1);
            SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_VAL");
            assertThat(get("/deals/" + deal).getList("valuations")).hasSize(1);

            // ECL from the parameters of its rating, to the OCI reserve
            final String rating = get("/counterparties/" + counterparty).getString("rating").toUpperCase();
            assertThat(put(this.admin, "/ecl-parameters",
                    "{\"ratingCode\": \"" + rating + "\", \"stage\": 1, \"pd12Month\": 1, \"pdLifetime\": 5, \"lgd\": 45}").statusCode())
                    .isEqualTo(200);
            final JsonPath ecl = post(this.admin, "/deals/" + deal + "?command=recalculateEcl", "{}").jsonPath();
            final BigDecimal exposure = new BigDecimal(ecl.getString("changes.exposure"));
            assertThat(new BigDecimal(ecl.getString("changes.ecl")))
                    .isEqualByComparingTo(exposure.multiply(new BigDecimal("0.0045")).setScale(2, RoundingMode.HALF_UP));

            // pledged: 40,000,000 of the face encumbered, and the bond cannot be sold until released
            final JsonPath pledge = post(this.admin, "/deals/" + deal + "?command=pledge",
                    "{\"pledgeeCounterpartyId\": " + pledgee + ", \"purpose\": \"CENTRAL_BANK_FACILITY\", \"amount\": 40000000}")
                    .jsonPath();
            assertThat(new BigDecimal(get("/deals/" + deal).getString("holding.unencumbered"))).isEqualByComparingTo("60000000");
            assertThat(post(this.admin, "/deals/" + deal + "?command=terminate", "{\"proceeds\": 100000000, \"reason\": \"Sold\"}")
                    .statusCode()).isEqualTo(403);
            final JsonPath portfolio = get("/portfolio?currency=" + CURRENCY);
            assertThat(portfolio.getList("holdings.dealId", Long.class)).contains(deal);
            assertThat(post(this.admin, "/deals/" + deal + "?command=releasePledge",
                    "{\"pledgeId\": " + pledge.getLong("subResourceId") + ", \"reason\": \"Facility repaid\"}").statusCode())
                    .isEqualTo(200);

            // sold: the adjustment and allowance leave with it, the OCI gain is in the realised result
            final Response sold = post(this.admin, "/deals/" + deal + "?command=terminate",
                    "{\"proceeds\": 100500000, \"reason\": \"Sold in the secondary market\"}");
            assertThat(sold.statusCode()).as(sold.asString()).isEqualTo(200);
            assertThat(sold.jsonPath().getString("changes.ociRecycled")).isNotNull();
            view = get("/deals/" + deal);
            assertThat(view.getString("status")).isEqualTo("TERMINATED");
            assertThat(new BigDecimal(view.getString("holding.fairValueAdjustment"))).isEqualByComparingTo("0");
            assertThat(new BigDecimal(view.getString("holding.eclAllowance"))).isEqualByComparingTo("0");
            assertThat(view.getList("events.eventType")).contains("REVALUED", "ECL_CALCULATED", "PLEDGED", "PLEDGE_RELEASED",
                    "ECL_RELEASED", "FAIR_VALUE_RELEASED", "TERMINATED");
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

    private static Response put(final RequestSpecification spec, final String path, final String json) {
        return given().spec(spec).body(json).when().put(url(path)).andReturn();
    }
}
