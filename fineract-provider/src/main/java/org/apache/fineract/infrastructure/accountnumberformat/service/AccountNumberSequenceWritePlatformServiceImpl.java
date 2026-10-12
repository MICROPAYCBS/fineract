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
package org.apache.fineract.infrastructure.accountnumberformat.service;

import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequence;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceRepository;
import org.apache.fineract.infrastructure.core.service.database.DatabaseTypeResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AccountNumberSequenceWritePlatformServiceImpl implements AccountNumberSequenceWritePlatformService {

    private final AccountNumberSequenceRepository accountNumberSequenceRepository;
    private final JdbcTemplate jdbcTemplate;
    private final DatabaseTypeResolver databaseTypeResolver;

    @Override
    @Transactional
    public long nextSequenceValue(final String scopeKey) {
        ensureSequenceRow(scopeKey);
        final AccountNumberSequence sequence = accountNumberSequenceRepository.findByScopeKeyForUpdate(scopeKey)
                .orElseThrow(() -> new IllegalStateException("Account number sequence row was not inserted for " + scopeKey));
        sequence.setLastValue(sequence.getLastValue() + 1);
        accountNumberSequenceRepository.saveAndFlush(sequence);
        return sequence.getLastValue();
    }

    private void ensureSequenceRow(final String scopeKey) {
        final String sql = databaseTypeResolver.isPostgreSQL()
                ? "INSERT INTO m_account_number_sequence (scope_key, last_value, version) VALUES (?, 0, 0) ON CONFLICT (scope_key) DO NOTHING"
                : "INSERT IGNORE INTO m_account_number_sequence (scope_key, last_value, version) VALUES (?, 0, 0)";
        jdbcTemplate.update(sql, scopeKey);
    }

    @Override
    @Transactional(readOnly = true)
    public long previewNextSequenceValue(final String scopeKey) {
        return accountNumberSequenceRepository.findByScopeKey(scopeKey).map(sequence -> sequence.getLastValue() + 1).orElse(1L);
    }
}
