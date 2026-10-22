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
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
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
 * Treasury statement reconciliation (P1.3): a CSV statement is imported, matched against the GL automatically, a bank
 * charge is explained with an adjusting entry, and the account ends up reconciled.
 */
public class TreasuryReconciliationIntegrationTest {

    private static final String API = "/fineract-provider/api/v1/treasury";
    private static final String CURRENCY = "USD";
    private static final long HEAD_OFFICE = 1L;
    private static final DateTimeFormatter CSV_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String PASSWORD = "A1b2c3d4e5f$";

    private RequestSpecification admin;

    @BeforeEach
    void setup() {
        Utils.initializeRESTAssured();
        MicropayCurrencyHelper.ensureSelected(CURRENCY);
        this.admin = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        this.admin.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey());
    }

    @Test
    void statementIsImportedMatchedExplainedAndReconciled() {
        final long bankGl = AccountHelper.createAssetGlAccount("TRS_RECON_BANK").getAccountID();
        final long fundingGl = AccountHelper.createAssetGlAccount("TRS_RECON_FUND").getAccountID();
        final long chargesGl = AccountHelper.createExpenseGlAccount("TRS_RECON_CHARGES").getAccountID();
        final long account = createAccount(bankGl);
        final LocalDate today = Utils.getLocalDateOfTenant();
        final LocalDate yesterday = today.minusDays(1);
        postJournalEntry(bankGl, fundingGl, "1000", yesterday, "DEP-" + account);
        postJournalEntry(fundingGl, bankGl, "300", today, null);

        final String format = Utils.uniqueRandomStringGenerator("CSV_", 6);
        assertThat(post("/statement-formats", "{\"code\": \"" + format + "\", \"name\": \"Test bank CSV\", \"formatType\": \"CSV\","
                + " \"delimiter\": \",\", \"headerRows\": 1, \"dateFormat\": \"dd/MM/yyyy\", \"dateColumn\": \"Date\","
                + " \"descriptionColumn\": \"Narrative\", \"referenceColumn\": \"Ref\", \"debitColumn\": \"Debit\", \"creditColumn\": \"Credit\","
                + " \"balanceColumn\": \"Balance\"}").statusCode()).isEqualTo(200);

        // bank view: +1000 deposit (our debit), -300 payment (our credit), -15 charge not yet in the GL
        final String csv = "Date,Narrative,Ref,Debit,Credit,Balance\n" + CSV_DATE.format(yesterday) + ",Deposit,DEP-" + account
                + ",,1000.00,1000.00\n" + CSV_DATE.format(today) + ",Payment,PAY-1,300.00,,700.00\n" + CSV_DATE.format(today)
                + ",Ledger fee,FEE-1,15.00,,685.00\n";
        final Response imported = post("/statements",
                "{\"treasuryAccountId\": " + account + ", \"formatCode\": \"" + format + "\", \"fileName\": \"it.csv\", \"content\": \""
                        + Base64.getEncoder().encodeToString(csv.getBytes(StandardCharsets.UTF_8)) + "\"}");
        assertThat(imported.statusCode()).as(imported.asString()).isEqualTo(200);
        final long statement = imported.jsonPath().getLong("resourceId");

        final Response again = post("/statements", "{\"treasuryAccountId\": " + account + ", \"formatCode\": \"" + format
                + "\", \"content\": \"" + Base64.getEncoder().encodeToString(csv.getBytes(StandardCharsets.UTF_8)) + "\"}");
        assertThat(again.asString()).contains("duplicate.file");

        final Response automatch = post("/statements/" + statement + "?command=automatch", "{}");
        assertThat(automatch.statusCode()).as(automatch.asString()).isEqualTo(200);
        assertThat(automatch.jsonPath().getInt("changes.matched")).isEqualTo(2);
        assertThat(automatch.jsonPath().getInt("changes.stillUnmatched")).isEqualTo(1);

        final List<Map<String, Object>> unmatched = get("/statements/" + statement + "/lines?matchStatus=UNMATCHED").getList("$");
        assertThat(unmatched).hasSize(1);
        final long feeLine = ((Number) unmatched.getFirst().get("id")).longValue();
        assertThat(new BigDecimal(unmatched.getFirst().get("amount").toString())).isEqualByComparingTo("-15");

        final String explanation = "{\"reasonCode\": \"BANK_CHARGE\", \"glAccountId\": " + chargesGl
                + ", \"note\": \"Monthly ledger fee\"}";
        // the importer may not book an adjusting entry from its own statement
        assertThat(post("/statements/lines/" + feeLine + "?command=explain", explanation).asString())
                .contains("importer.cannot.post.adjustment");
        final Response explained = given().spec(userWith("EXPLAIN_TREASURYSTATEMENTLINE")).body(explanation).when()
                .post(url("/statements/lines/" + feeLine + "?command=explain")).andReturn();
        assertThat(explained.statusCode()).as(explained.asString()).isEqualTo(200);

        assertThat(get("/statements/" + statement).getString("status")).isEqualTo("RECONCILED");
        final JsonPath recon = get("/accounts/" + account + "/reconciliation");
        assertThat(recon.getString("lastReconciledDate")).isEqualTo(today.toString());
        assertThat(new BigDecimal(recon.getString("difference"))).isEqualByComparingTo("0");
        assertThat(recon.getLong("unmatchedJournalEntries")).isZero();
        assertThat(get("/accounts/" + account + "/position").getLong("unreconciledCount")).isZero();

        // undoing the explanation reverses the adjusting entry and reopens the statement
        final long matchId = explained.jsonPath().getLong("changes.matchId");
        final Response unmatchedAgain = given().spec(this.admin).body("{\"reason\": \"Booked twice\"}").when()
                .delete(url("/reconciliation/matches/" + matchId)).andReturn();
        assertThat(unmatchedAgain.statusCode()).as(unmatchedAgain.asString()).isEqualTo(200);
        assertThat(get("/statements/" + statement).getString("status")).isEqualTo("IMPORTED");
        assertThat(get("/reconciliation/unmatched?accountId=" + account).getList("statementLines")).hasSize(1);
        assertThat(new BigDecimal(get("/accounts/" + account + "/position").getString("balance"))).isEqualByComparingTo("700");
    }

    @Test
    void statementThatDoesNotAddUpIsRefused() {
        final long account = createAccount(AccountHelper.createAssetGlAccount("TRS_RECON_BAD").getAccountID());
        final String csv = "15/10/2026,Deposit,X,,100.00,100.00\n16/10/2026,Payment,Y,30.00,,80.00\n";
        final String format = Utils.uniqueRandomStringGenerator("CSV_", 6);
        post("/statement-formats",
                "{\"code\": \"" + format + "\", \"name\": \"No header\", \"formatType\": \"CSV\", \"dateFormat\": \"dd/MM/yyyy\","
                        + " \"dateColumn\": \"1\", \"debitColumn\": \"4\", \"creditColumn\": \"5\", \"balanceColumn\": \"6\"}");

        final Response response = post("/statements", "{\"treasuryAccountId\": " + account + ", \"formatCode\": \"" + format
                + "\", \"content\": \"" + Base64.getEncoder().encodeToString(csv.getBytes(StandardCharsets.UTF_8)) + "\"}");
        assertThat(response.asString()).contains("running.balance.mismatch").contains("Line 2");
    }

    private long createAccount(final long glAccountId) {
        final String code = Utils.uniqueRandomStringGenerator("TRS_", 8);
        final Response response = post("/accounts",
                "{\"code\": \"" + code + "\", \"name\": \"Recon " + code + "\", \"accountType\": \"BANK\", \"glAccountId\": " + glAccountId
                        + ", \"officeId\": " + HEAD_OFFICE + ", \"currencyCode\": \"" + CURRENCY + "\"}");
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath().getLong("resourceId");
    }

    private static void postJournalEntry(final long debitGl, final long creditGl, final String amount, final LocalDate date,
            final String reference) {
        final BigDecimal value = new BigDecimal(amount);
        final JournalEntryCommand command = new JournalEntryCommand().amount(value).officeId(HEAD_OFFICE).currencyCode(CURRENCY)
                .locale("en").dateFormat("uuuu-MM-dd").transactionDate(date)
                .addDebitsItem(new SingleDebitOrCreditEntryCommand().glAccountId(debitGl).amount(value))
                .addCreditsItem(new SingleDebitOrCreditEntryCommand().glAccountId(creditGl).amount(value));
        if (reference != null) {
            command.referenceNumber(reference);
        }
        JournalEntryHelper.createJournalEntry(null, command);
    }

    private static String url(final String path) {
        return API + path + (path.contains("?") ? "&" : "?") + Utils.TENANT_IDENTIFIER;
    }

    private JsonPath get(final String path) {
        final Response response = given().spec(this.admin).when().get(url(path)).andReturn();
        assertThat(response.statusCode()).as(response.asString()).isEqualTo(200);
        return response.jsonPath();
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
                .repeatPassword(PASSWORD).firstname("Treasury").lastname("Tester").email(username + "@test.example.org")
                .officeId(HEAD_OFFICE).roles(List.of(roleId)).sendPasswordToEmail(false)));
        final RequestSpecification spec = new RequestSpecBuilder().setContentType(ContentType.JSON).build();
        spec.header("Authorization", "Basic " + Utils.loginIntoServerAndGetBase64EncodedAuthenticationKey(username, PASSWORD));
        return spec;
    }

    private Response post(final String path, final String json) {
        return given().spec(this.admin).body(json).when().post(url(path)).andReturn();
    }
}
