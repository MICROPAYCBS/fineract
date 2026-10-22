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
import java.util.Map;
import org.apache.fineract.client.models.JournalEntryCommand;
import org.apache.fineract.client.models.SingleDebitOrCreditEntryCommand;
import org.apache.fineract.integrationtests.common.SchedulerJobHelper;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.apache.fineract.integrationtests.common.accounting.JournalEntryHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Treasury accounts and positions (P1.1): an account on an asset GL account shows the GL balance of its office and
 * currency, with its level status, and the day-end snapshot job records it.
 */
public class TreasuryAccountPositionIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/treasury";
    private static final String CURRENCY = "USD";
    private static final long HEAD_OFFICE = 1L;

    private RequestSpecification requestSpec;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        MicropayCurrencyHelper.ensureSelected(CURRENCY);
        this.requestSpec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.requestSpec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void positionFollowsTheGlAndFlagsLevels() {
        final long treasuryGl = AccountHelper.createAssetGlAccount("TRS_BANK").getAccountID();
        final long contraGl = AccountHelper.createAssetGlAccount("TRS_CONTRA").getAccountID();
        final long accountId = createAccount(treasuryGl, "100", "1000");

        JsonPath position = get("/accounts/" + accountId + "/position");
        assertThat(position.getDouble("balance")).isZero();
        assertThat(position.getString("levelStatus")).isEqualTo("BELOW_MIN");

        postJournalEntry(treasuryGl, contraGl, "250", Utils.getLocalDateOfTenant());
        position = get("/accounts/" + accountId + "/position");
        assertThat(position.getDouble("balance")).isEqualTo(250.0);
        assertThat(position.getString("levelStatus")).isEqualTo("OK");

        postJournalEntry(treasuryGl, contraGl, "900", Utils.getLocalDateOfTenant());
        position = get("/accounts/" + accountId + "/position");
        assertThat(position.getDouble("balance")).isEqualTo(1150.0);
        assertThat(position.getString("levelStatus")).isEqualTo("ABOVE_MAX");

        final JsonPath positions = get("/positions?officeId=" + HEAD_OFFICE + "&currency=" + CURRENCY + "&" + Utils.TENANT_IDENTIFIER);
        final List<Map<String, Object>> accounts = positions.getList("[0].accounts");
        assertThat(accounts).anySatisfy(account -> {
            assertThat(((Number) account.get("treasuryAccountId")).longValue()).isEqualTo(accountId);
            assertThat(account.get("levelStatus")).isEqualTo("ABOVE_MAX");
        });
        assertThat(positions.getInt("[0].accountsAboveMax")).isPositive();
    }

    @Test
    void snapshotJobRecordsTheClosedDay() {
        final long treasuryGl = AccountHelper.createAssetGlAccount("TRS_VAULT").getAccountID();
        final long contraGl = AccountHelper.createAssetGlAccount("TRS_CONTRA").getAccountID();
        final long accountId = createAccount(treasuryGl, null, "50");
        final LocalDate closedDay = Utils.getLocalDateOfTenant().minusDays(1);
        postJournalEntry(treasuryGl, contraGl, "75", closedDay);
        postJournalEntry(treasuryGl, contraGl, "5", Utils.getLocalDateOfTenant());

        SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_SNAP");
        // a second run for the same day overwrites rather than duplicates
        SchedulerJobHelper.executeAndAwaitJobByShortName("TRS_SNAP");

        final JsonPath position = get("/accounts/" + accountId + "/position");
        assertThat(position.getString("lastSnapshotDate")).isNotNull();
        assertThat(position.getDouble("lastSnapshotBalance")).isEqualTo(75.0);
        assertThat(position.getString("lastSnapshotLevelStatus")).isEqualTo("ABOVE_MAX");
        assertThat(position.getDouble("balance")).isEqualTo(80.0);
    }

    @Test
    void accountMustUseAnAssetDetailGlAccount() {
        final long incomeGl = AccountHelper.createIncomeGlAccount("TRS_INCOME").getAccountID();

        final Response response = post("/accounts", accountJson(incomeGl, null, null));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.asString()).contains("error.msg.treasury.account.gl.account.not.asset.detail");
    }

    @Test
    void minimumAboveMaximumIsRejected() {
        final long treasuryGl = AccountHelper.createAssetGlAccount("TRS_RANGE").getAccountID();

        final Response response = post("/accounts", accountJson(treasuryGl, "500", "100"));

        assertThat(response.statusCode()).isEqualTo(400);
        assertThat(response.asString()).contains("minBalance");
    }

    @Test
    void closedAccountLeavesTheListAndCannotBeChanged() {
        final long treasuryGl = AccountHelper.createAssetGlAccount("TRS_CLOSE").getAccountID();
        final long accountId = createAccount(treasuryGl, null, null);

        final Response update = put("/accounts/" + accountId, "{\"name\": \"Renamed\", \"maxBalance\": 999}");
        assertThat(update.statusCode()).isEqualTo(200);
        assertThat(get("/accounts/" + accountId).getString("name")).isEqualTo("Renamed");

        assertThat(post("/accounts/" + accountId + "?command=close&" + Utils.TENANT_IDENTIFIER, "{}").statusCode()).isEqualTo(200);
        assertThat(get("/accounts/" + accountId).getString("status")).isEqualTo("CLOSED");

        final List<Integer> activeIds = get("/accounts?status=ACTIVE&officeId=" + HEAD_OFFICE + "&" + Utils.TENANT_IDENTIFIER)
                .getList("id");
        assertThat(activeIds).doesNotContain((int) accountId);
        assertThat(put("/accounts/" + accountId, "{\"name\": \"Again\"}").statusCode()).isEqualTo(403);
    }

    private long createAccount(final long glAccountId, final String minBalance, final String maxBalance) {
        final Response response = post("/accounts", accountJson(glAccountId, minBalance, maxBalance));
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath().getLong("resourceId");
    }

    private static String accountJson(final long glAccountId, final String minBalance, final String maxBalance) {
        final String code = Utils.uniqueRandomStringGenerator("TRS_", 8);
        return "{\"code\": \"" + code + "\", \"name\": \"Treasury " + code + "\", \"accountType\": \"BANK\", \"glAccountId\": "
                + glAccountId + ", \"officeId\": " + HEAD_OFFICE + ", \"currencyCode\": \"" + CURRENCY
                + "\", \"counterpartyName\": \"Test Bank\"" + (minBalance == null ? "" : ", \"minBalance\": " + minBalance)
                + (maxBalance == null ? "" : ", \"maxBalance\": " + maxBalance) + "}";
    }

    private static void postJournalEntry(final long debitGl, final long creditGl, final String amount, final LocalDate date) {
        final BigDecimal value = new BigDecimal(amount);
        JournalEntryHelper.createJournalEntry(null,
                new JournalEntryCommand().amount(value).officeId(HEAD_OFFICE).currencyCode(CURRENCY).locale("en").dateFormat("uuuu-MM-dd")
                        .transactionDate(date).addDebitsItem(new SingleDebitOrCreditEntryCommand().glAccountId(debitGl).amount(value))
                        .addCreditsItem(new SingleDebitOrCreditEntryCommand().glAccountId(creditGl).amount(value)));
    }

    private JsonPath get(final String path) {
        final String url = API + path + (path.contains("?") ? "" : "?" + Utils.TENANT_IDENTIFIER);
        final Response response = given().spec(this.requestSpec).when().get(url).andReturn();
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath();
    }

    private Response post(final String path, final String json) {
        final String url = API + path + (path.contains("?") ? "" : "?" + Utils.TENANT_IDENTIFIER);
        return given().spec(this.requestSpec).body(json).when().post(url).andReturn();
    }

    private Response put(final String path, final String json) {
        return given().spec(this.requestSpec).body(json).when().put(API + path + "?" + Utils.TENANT_IDENTIFIER).andReturn();
    }
}
