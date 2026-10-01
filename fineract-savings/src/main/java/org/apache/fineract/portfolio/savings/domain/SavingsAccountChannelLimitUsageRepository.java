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
package org.apache.fineract.portfolio.savings.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SavingsAccountChannelLimitUsageRepository
        extends JpaRepository<SavingsAccountChannelLimitUsage, Long>, JpaSpecificationExecutor<SavingsAccountChannelLimitUsage> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select u from SavingsAccountChannelLimitUsage u
            where u.savingsAccount.id = :accountId
              and u.productPaymentChannel.id = :channelId
              and u.direction = :direction
              and u.periodType = :periodType
              and u.periodStart = :periodStart
            """)
    Optional<SavingsAccountChannelLimitUsage> findForUpdate(@Param("accountId") Long accountId, @Param("channelId") Long channelId,
            @Param("direction") SavingsChannelLimitDirection direction, @Param("periodType") SavingsChannelLimitPeriodType periodType,
            @Param("periodStart") LocalDate periodStart);

    Optional<SavingsAccountChannelLimitUsage> findBySavingsAccountIdAndProductPaymentChannelIdAndDirectionAndPeriodTypeAndPeriodStart(
            Long savingsAccountId, Long productPaymentChannelId, SavingsChannelLimitDirection direction,
            SavingsChannelLimitPeriodType periodType, LocalDate periodStart);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from SavingsAccountChannelLimitUsage u where u.id = :id")
    Optional<SavingsAccountChannelLimitUsage> findOneForUpdate(@Param("id") Long id);
}
