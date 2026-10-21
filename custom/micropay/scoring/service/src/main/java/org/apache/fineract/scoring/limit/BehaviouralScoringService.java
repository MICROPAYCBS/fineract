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
package org.apache.fineract.scoring.limit;

import static org.apache.fineract.scoring.api.ScoringApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.scoring.domain.ScoringLimitPolicy;
import org.apache.fineract.scoring.domain.ScoringLimitPolicyRepository;
import org.apache.fineract.scoring.domain.ScoringMode;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * The nightly behavioural run: every client in the segment of every policy that is not OFF is scored and gets a new
 * limit. A client that fails is logged and skipped; the previous limit stays until it expires.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class BehaviouralScoringService {

    private static final int CLIENT_STATUS_ACTIVE = 300;

    private final ScoringLimitPolicyRepository policyRepository;
    private final ClientLimitWriter writer;
    private final JdbcTemplate jdbcTemplate;

    public record RunSummary(int policies, int scored, int failed) {
    }

    public RunSummary runAll() {
        final LocalDate today = DateUtils.getBusinessLocalDate();
        int scored = 0;
        int failed = 0;
        final List<ScoringLimitPolicy> policies = this.policyRepository.findByModeNot(ScoringMode.OFF);
        for (final ScoringLimitPolicy policy : policies) {
            for (final Long clientId : segment(policy)) {
                try {
                    this.writer.scoreClient(policy.getId(), clientId, today);
                    scored++;
                } catch (RuntimeException e) {
                    failed++;
                    log.error("Behavioural scoring failed for client {} under limit policy {}", clientId, policy.getCode(), e);
                }
            }
        }
        log.info("Behavioural scoring run: {} policies, {} clients scored, {} failed", policies.size(), scored, failed);
        return new RunSummary(policies.size(), scored, failed);
    }

    List<Long> segment(final ScoringLimitPolicy policy) {
        final StringBuilder sql = new StringBuilder("SELECT id FROM m_client WHERE status_enum = ?");
        final List<Object> args = new ArrayList<>(List.of(CLIENT_STATUS_ACTIVE));
        if (policy.isStaffOnly()) {
            sql.append(" AND is_staff = ?");
            args.add(Boolean.TRUE);
        }
        if (policy.getOfficeId() != null) {
            sql.append(" AND office_id = ?");
            args.add(policy.getOfficeId());
        }
        if (policy.getCustomerClassId() != null) {
            sql.append(" AND customer_class_id = ?");
            args.add(policy.getCustomerClassId());
        }
        return this.jdbcTemplate.queryForList(sql.append(" ORDER BY id").toString(), Long.class, args.toArray());
    }
}
