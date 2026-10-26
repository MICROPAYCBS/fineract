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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.micropay.fraud.engine.alert.AlertFilter;
import com.micropay.fraud.engine.alert.AlertRepository;
import com.micropay.fraud.engine.alert.AlertStatus;
import com.micropay.fraud.engine.api.ApiKeyFilter;
import com.micropay.fraud.engine.event.TransactionKind;
import com.micropay.fraud.engine.ingest.AvroMessages;
import com.micropay.fraud.engine.ingest.FineractEventListener;
import com.micropay.fraud.engine.rule.FraudRules;
import com.micropay.fraud.engine.rule.RuleHit;
import com.micropay.fraud.engine.scoring.RiskAssessment;
import com.micropay.fraud.engine.scoring.TransactionProcessor;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = { "spring.config.name=" + FraudEngineApplication.CONFIG_NAME,
        "spring.datasource.url=jdbc:h2:mem:fraud;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "spring.datasource.username=sa",
        "spring.datasource.password=", "fraud.kafka.enabled=false", "fraud.api.key=test-key", "fraud.anomaly.min-training-samples=100000",
        "fraud.watchlist.enabled=false" })
class FraudEngineIntegrationTest {

    private static final Instant START = Instant.parse("2026-10-01T07:00:00Z");

    @Autowired
    private TransactionProcessor processor;
    @Autowired
    private FineractEventListener listener;
    @Autowired
    private AlertRepository alerts;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private WebApplicationContext context;
    @Autowired
    private ApiKeyFilter apiKeyFilter;

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM fe_alert_reason");
        jdbcTemplate.update("DELETE FROM fe_watchlist_match");
        jdbcTemplate.update("DELETE FROM fe_alert");
        jdbcTemplate.update("DELETE FROM fe_transaction");
        jdbcTemplate.update("DELETE FROM fe_processed_event");
        jdbcTemplate.update("DELETE FROM fe_account_owner");
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(apiKeyFilter).build();
    }

    @Test
    void ordinaryActivityRaisesNoAlert() {
        for (int day = 0; day < 10; day++) {
            final Instant at = START.plus(Duration.ofDays(day));
            assertThat(processor.process(TestEvents.savings(TransactionKind.DEPOSIT, 1L, 1L, 150_000 + day * 1_000, at))).isPresent();
            processor.process(TestEvents.savings(TransactionKind.WITHDRAWAL, 1L, 1L, 60_000, at.plus(Duration.ofHours(3))));
        }
        assertThat(alerts.count(new AlertFilter(null, null, null, null, null, null, null))).isZero();
    }

    @Test
    void muleAccountPatternIsAlerted() {
        for (int day = 0; day < 10; day++) {
            processor.process(TestEvents.savings(TransactionKind.DEPOSIT, 2L, 20L, 100_000, START.plus(Duration.ofDays(day))));
            processor.process(
                    TestEvents.savings(TransactionKind.WITHDRAWAL, 2L, 20L, 50_000, START.plus(Duration.ofDays(day)).plusSeconds(600)));
        }
        final Instant at = START.plus(Duration.ofDays(11));
        processor.process(TestEvents.savings(TransactionKind.DEPOSIT, 2L, 20L, 9_000_000, at));
        final Optional<RiskAssessment> risk = processor
                .process(TestEvents.savings(TransactionKind.WITHDRAWAL, 2L, 20L, 8_800_000, at.plus(Duration.ofMinutes(20))));

        assertThat(risk).hasValueSatisfying(r -> {
            assertThat(r.alert()).isTrue();
            assertThat(r.reasons()).extracting(RuleHit::code).contains(FraudRules.RAPID_IN_OUT, FraudRules.LARGE_AMOUNT);
        });
        assertThat(alerts.count(new AlertFilter("default", null, AlertStatus.NEW, null, 20L, null, null))).isGreaterThanOrEqualTo(1);
    }

    @Test
    void duplicateKafkaDeliveryIsScoredOnce() throws Exception {
        final byte[] record = AvroMessages.savingsTransaction("SavingsWithdrawalBusinessEvent", "dup-1", 1L, 3L, false,
                new BigDecimal("50000000"), "2026-10-20T09:00:00");
        listener.onMessage(new ConsumerRecord<>("external-events", 0, 0L, 3L, record));
        listener.onMessage(new ConsumerRecord<>("external-events", 0, 1L, 3L, record));

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fe_transaction WHERE account_id = 3", Integer.class)).isEqualTo(1);
    }

    @Test
    void undecodableRecordIsSkipped() {
        listener.onMessage(new ConsumerRecord<>("external-events", 0, 0L, 1L, new byte[] { 9, 9, 9 }));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM fe_transaction", Integer.class)).isZero();
    }

    @Test
    void alertApiNeedsTheKeyAndRecordsTheVerdict() throws Exception {
        // 01:00 in Kampala, a first large withdrawal at night
        processor.process(TestEvents.savings(TransactionKind.WITHDRAWAL, 4L, 40L, 60_000_000, Instant.parse("2026-09-30T22:00:00Z")));
        final Long alertId = jdbcTemplate.queryForObject("SELECT MAX(id) FROM fe_alert", Long.class);
        assertThat(alertId).isNotNull();

        mvc.perform(get("/api/v1/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/alerts").header(ApiKeyFilter.HEADER, "wrong")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/alerts?status=NEW").header(ApiKeyFilter.HEADER, "test-key")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"total\":1")));
        mvc.perform(get("/api/v1/alerts/" + alertId).header(ApiKeyFilter.HEADER, "test-key")).andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(FraudRules.FIRST_LARGE_AMOUNT)));

        mvc.perform(patch("/api/v1/alerts/" + alertId).header(ApiKeyFilter.HEADER, "test-key").contentType(MediaType.APPLICATION_JSON)
                .content("{\"verdict\":\"NOT_FRAUD\",\"assignedTo\":\"analyst1\",\"note\":\"Customer sold land\"}"))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("\"status\":\"CLOSED\"")));

        mvc.perform(get("/api/v1/alerts/999999").header(ApiKeyFilter.HEADER, "test-key")).andExpect(status().isNotFound());
    }
}
