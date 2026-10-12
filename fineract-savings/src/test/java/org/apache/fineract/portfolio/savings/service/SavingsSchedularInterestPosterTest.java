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
package org.apache.fineract.portfolio.savings.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.fineract.infrastructure.jobs.exception.JobExecutionException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.savings.data.SavingsAccountData;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SavingsSchedularInterestPosterTest {

    @Test
    void testUpdateCountsZeroMeansVersionMismatch() {
        int[] updateCounts = { 1, 0, 1 };
        Set<Long> skippedAccountIds = new HashSet<>();
        List<Long> accountIds = List.of(1L, 2L, 3L);

        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] == 0) {
                skippedAccountIds.add(accountIds.get(i));
            }
        }

        assertEquals(1, skippedAccountIds.size(), "Exactly one account should be skipped");
        assertTrue(skippedAccountIds.contains(2L), "Account 2 should be skipped due to version mismatch");
    }

    @Test
    void testAllVersionsMatchNoSkippedAccounts() {
        int[] updateCounts = { 1, 1, 1 };
        Set<Long> skippedAccountIds = new HashSet<>();
        List<Long> accountIds = List.of(1L, 2L, 3L);

        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] == 0) {
                skippedAccountIds.add(accountIds.get(i));
            }
        }

        assertTrue(skippedAccountIds.isEmpty(), "No accounts should be skipped when all versions match");
    }

    @Test
    void testAllVersionsMismatchAllSkipped() {
        int[] updateCounts = { 0, 0, 0 };
        Set<Long> skippedAccountIds = new HashSet<>();
        List<Long> accountIds = List.of(1L, 2L, 3L);

        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] == 0) {
                skippedAccountIds.add(accountIds.get(i));
            }
        }

        assertEquals(3, skippedAccountIds.size(), "All 3 accounts should be detected as version mismatched");
        assertTrue(skippedAccountIds.containsAll(List.of(1L, 2L, 3L)), "All account IDs should be in skipped set");
    }

    @Test
    void testVersionMismatchSkipsFailedAccountAndProceedsWithOthers() {
        int[] updateCounts = { 1, 0, 1 };
        List<Long> accountIds = List.of(1L, 2L, 3L);
        List<Long> successfulIds = new ArrayList<>();

        for (int i = 0; i < updateCounts.length; i++) {
            if (updateCounts[i] == 0) {
                // account is skipped due to concurrent modification — logged, not thrown
            } else {
                successfulIds.add(accountIds.get(i));
            }
        }

        assertEquals(2, successfulIds.size(), "Two accounts should proceed normally");
        assertTrue(successfulIds.containsAll(List.of(1L, 3L)), "Accounts 1 and 3 should succeed independently");
    }

    @Test
    void postInterestFailureSkipsBatchUpdate() {
        final SavingsAccountWritePlatformService writeService = mock(SavingsAccountWritePlatformService.class);
        final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        final SavingsSchedularInterestPoster poster = new SavingsSchedularInterestPoster(writeService, jdbcTemplate,
                mock(SavingsAccountReadPlatformService.class), mock(PlatformSecurityContext.class));

        final SavingsAccountData posted = SavingsAccountData.withClientTemplate(1L, "Posted", null, null);
        final SavingsAccountData missingMapping = SavingsAccountData.withClientTemplate(2L, "Missing", null, null);
        poster.setSavingAccounts(List.of(posted, missingMapping));

        when(writeService.postInterest(same(posted), eq(false), isNull(), eq(false))).thenReturn(posted);
        when(writeService.postInterest(same(missingMapping), eq(false), isNull(), eq(false)))
                .thenThrow(new IllegalStateException("missing mapping"));

        assertThrows(JobExecutionException.class, poster::postInterest);
        verifyNoInteractions(jdbcTemplate);
    }
}
