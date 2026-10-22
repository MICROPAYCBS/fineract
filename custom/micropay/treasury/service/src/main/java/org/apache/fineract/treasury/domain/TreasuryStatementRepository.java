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
package org.apache.fineract.treasury.domain;

import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TreasuryStatementRepository extends JpaRepository<TreasuryStatement, Long> {

    boolean existsByTreasuryAccountIdAndFileHash(Long treasuryAccountId, String fileHash);

    /** Statements of the account whose period shares at least one day with the given one. */
    @Query("SELECT s.id FROM TreasuryStatement s WHERE s.treasuryAccountId = :accountId AND s.periodFrom <= :periodTo AND s.periodTo >= :periodFrom"
            + " ORDER BY s.id")
    List<Long> findOverlapping(@Param("accountId") Long accountId, @Param("periodFrom") LocalDate periodFrom,
            @Param("periodTo") LocalDate periodTo);
}
