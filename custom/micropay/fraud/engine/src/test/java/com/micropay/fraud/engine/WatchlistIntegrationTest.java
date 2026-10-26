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
package com.micropay.fraud.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.micropay.fraud.engine.alert.AlertData;
import com.micropay.fraud.engine.alert.AlertFilter;
import com.micropay.fraud.engine.alert.AlertRepository;
import com.micropay.fraud.engine.alert.AlertType;
import com.micropay.fraud.engine.api.ApiKeyFilter;
import com.micropay.fraud.engine.event.ClientEvent;
import com.micropay.fraud.engine.event.PartyType;
import com.micropay.fraud.engine.scoring.Severity;
import com.micropay.fraud.engine.scoring.TransactionProcessor;
import com.micropay.fraud.engine.watchlist.RefreshResult;
import com.micropay.fraud.engine.watchlist.WatchlistRefresher;
import com.micropay.fraud.engine.watchlist.WatchlistService;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = { "spring.config.name=" + FraudEngineApplication.CONFIG_NAME,
        "spring.datasource.url=jdbc:h2:mem:watchlist;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "fraud.kafka.enabled=false", "fraud.api.key=test-key",
        "fraud.watchlist.load-on-startup=false" })
class WatchlistIntegrationTest {

    private static final String LOCAL_CSV_HEADER = "uid,type,name,aliases,birth_dates,programs\n";
    private static final String LOCAL_CSV = LOCAL_CSV_HEADER + "L-1,PERSON,\"Okello, Brian Mugisha\",Brian Okello,1985-11-20,Internal\n";
    private static final Path LOCAL_FILE = tempFile();

    @Autowired
    private TransactionProcessor processor;
    @Autowired
    private WatchlistRefresher refresher;
    @Autowired
    private WatchlistService watchlist;
    @Autowired
    private AlertRepository alerts;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApiKeyFilter apiKeyFilter;

    private MockMvc mvc;

    @DynamicPropertySource
    static void lists(DynamicPropertyRegistry registry) {
        registry.add("fraud.watchlist.ofac.url", () -> resource("ofac-sdn.xml"));
        registry.add("fraud.watchlist.un.url", () -> resource("un-consolidated.xml"));
        registry.add("fraud.watchlist.eu.url", () -> resource("eu-consolidated.xml"));
        registry.add("fraud.watchlist.files.LOCAL", LOCAL_FILE::toString);
    }

    @BeforeEach
    void setUp() throws IOException {
        jdbcTemplate.update("DELETE FROM fe_watchlist_match");
        jdbcTemplate.update("DELETE FROM fe_alert_reason");
        jdbcTemplate.update("DELETE FROM fe_alert");
        jdbcTemplate.update("DELETE FROM fe_client_profile");
        jdbcTemplate.update("DELETE FROM fe_processed_event");
        jdbcTemplate.update("DELETE FROM fe_watchlist_name");
        jdbcTemplate.update("DELETE FROM fe_watchlist_entry");
        jdbcTemplate.update("DELETE FROM fe_watchlist_source");
        Files.writeString(LOCAL_FILE, LOCAL_CSV, StandardCharsets.UTF_8);
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(apiKeyFilter).build();
    }

    @Test
    void refreshLoadsEveryListAndSkipsUnchangedOnes() {
        assertThat(refresher.refreshLists()).extracting(RefreshResult::source, RefreshResult::outcome).containsExactlyInAnyOrder(
                tuple("OFAC", RefreshResult.Outcome.LOADED), tuple("UN", RefreshResult.Outcome.LOADED),
                tuple("EU", RefreshResult.Outcome.LOADED), tuple("LOCAL", RefreshResult.Outcome.LOADED));
        assertThat(watchlist.index().entryCount()).isEqualTo(3 + 2 + 2 + 1);

        assertThat(refresher.refreshLists()).extracting(RefreshResult::outcome).containsOnly(RefreshResult.Outcome.UNCHANGED);
    }

    @Test
    void aNewClientOnAListIsAlertedOnce() {
        refresher.refreshLists();

        processor.process(client("ClientCreateBusinessEvent", 501L, "Joseph", "Kony", LocalDate.of(1964, 9, 18)));
        processor.process(client("ClientActivateBusinessEvent", 501L, "Joseph", "Kony", LocalDate.of(1964, 9, 18)));

        final List<AlertData> found = alerts.find(new AlertFilter("default", AlertType.WATCHLIST, null, null, 501L, null, null), 0, 10);
        assertThat(found).hasSize(1);
        final AlertData alert = alerts.findById(found.get(0).id()).orElseThrow();
        assertThat(alert.severity()).isEqualTo(Severity.HIGH);
        assertThat(alert.riskScore()).isEqualTo(100);
        assertThat(alert.accountId()).isNull();
        assertThat(alert.reasons()).extracting(r -> r.code()).containsExactly(WatchlistService.REASON_PREFIX + "OFAC");
        assertThat(alert.watchlistMatches()).singleElement().satisfies(m -> {
            assertThat(m.entryUid()).isEqualTo("9001");
            assertThat(m.birthDateCheck()).isEqualTo("MATCH");
            assertThat(m.programs()).containsExactly("CAR", "SDGT");
        });
    }

    @Test
    void anOrdinaryClientIsNotAlerted() {
        refresher.refreshLists();
        processor.process(client("ClientCreateBusinessEvent", 502L, "Sarah", "Namubiru", LocalDate.of(1990, 1, 1)));
        assertThat(alerts.count(new AlertFilter(null, AlertType.WATCHLIST, null, null, null, null, null))).isZero();
    }

    @Test
    void aListChangeReScreensExistingClients() throws IOException {
        refresher.refreshLists();
        processor.process(client("ClientCreateBusinessEvent", 503L, "Peter", "Kato Wasswa", null));
        assertThat(alerts.count(new AlertFilter(null, AlertType.WATCHLIST, null, null, 503L, null, null))).isZero();

        Files.writeString(LOCAL_FILE, LOCAL_CSV + "L-3,PERSON,Peter Wasswa Kato,,,Internal\n", StandardCharsets.UTF_8);
        final WatchlistRefresher.RunSummary summary = refresher.run();

        assertThat(summary.alertsRaised()).isEqualTo(1);
        assertThat(alerts.count(new AlertFilter(null, AlertType.WATCHLIST, null, null, 503L, null, null))).isEqualTo(1);
        // Nothing changed, so the next run screens nobody
        assertThat(refresher.run().clientsScreened()).isZero();
    }

    @Test
    void aBrokenDownloadKeepsTheStoredList() throws IOException {
        refresher.refreshLists();
        Files.writeString(LOCAL_FILE, LOCAL_CSV_HEADER, StandardCharsets.UTF_8);

        assertThat(refresher.refreshLists()).filteredOn(r -> r.source().equals("LOCAL")).singleElement()
                .satisfies(r -> assertThat(r.outcome()).isEqualTo(RefreshResult.Outcome.FAILED));
        assertThat(watchlist.index().entryCount()).isEqualTo(8);
    }

    @Test
    void apiChecksNamesAndShowsListStatus() throws Exception {
        refresher.refreshLists();

        mvc.perform(post("/api/v1/watchlist/check").header(ApiKeyFilter.HEADER, "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Amina Hassan Abdi\",\"dateOfBirth\":\"1979-03-02\",\"partyType\":\"PERSON\"}"))
                .andExpect(status().isOk()).andExpect(content().string(Matchers.containsString("\"entryUid\":\"QDi.900\"")));
        mvc.perform(post("/api/v1/watchlist/check").header(ApiKeyFilter.HEADER, "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/watchlist/sources").header(ApiKeyFilter.HEADER, "test-key")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"source\":\"OFAC\"")))
                .andExpect(content().string(Matchers.containsString("\"entries\":8")));
        mvc.perform(get("/api/v1/watchlist/sources")).andExpect(status().isUnauthorized());

        processor.process(client("ClientCreateBusinessEvent", 504L, "Brian", "Okello", LocalDate.of(1985, 11, 20)));
        mvc.perform(get("/api/v1/alerts?type=WATCHLIST").header(ApiKeyFilter.HEADER, "test-key")).andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("\"total\":1")));
    }

    private static ClientEvent client(String type, Long clientId, String first, String last, LocalDate dob) {
        return new ClientEvent(TestEvents.TENANT, UUID.randomUUID().toString(), type, clientId, LocalDate.of(2026, 10, 1), 1L,
                first + " " + last, first + " " + last, dob, PartyType.PERSON);
    }

    private static String resource(String name) {
        try {
            return Path.of(WatchlistIntegrationTest.class.getResource("/watchlist/" + name).toURI()).toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path tempFile() {
        try {
            final Path file = Files.createTempFile("local-watchlist", ".csv");
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
