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
package org.apache.fineract.transactionbatch.domain;

import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionBatchRepository extends JpaRepository<TransactionBatch, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TransactionBatch b where b.id = :id")
    Optional<TransactionBatch> findByIdForUpdate(@Param("id") Long id);

    @Query("""
            select b.id from TransactionBatch b
            where b.status = :active
            and ((b.recurring = false and (b.effectiveDate is null or b.effectiveDate <= :businessDate))
              or (b.recurring = true and b.nextPostingDate is not null and b.nextPostingDate <= :businessDate))
            order by b.id
            """)
    List<Long> findDueIds(@Param("active") Integer active, @Param("businessDate") LocalDate businessDate);
}
