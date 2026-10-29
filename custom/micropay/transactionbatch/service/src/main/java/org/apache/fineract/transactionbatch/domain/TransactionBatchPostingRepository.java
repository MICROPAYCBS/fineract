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

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TransactionBatchPostingRepository extends JpaRepository<TransactionBatchPosting, Long> {

    Optional<TransactionBatchPosting> findByRunIdAndItemId(Long runId, Long itemId);

    List<TransactionBatchPosting> findByRunId(Long runId);

    Optional<TransactionBatchPosting> findByIdAndItemId(Long id, Long itemId);

    @Query("select p from TransactionBatchPosting p where p.itemId = :itemId and p.status = :status order by p.id asc")
    List<TransactionBatchPosting> findByItemAndStatus(@Param("itemId") Long itemId, @Param("status") Integer status);

    @Query("""
            select count(p) from TransactionBatchPosting p
            where p.status = :suspended
            and p.itemId in (select i.id from TransactionBatchItem i where i.batch.id = :batchId)
            """)
    long countUnrepaired(@Param("batchId") Long batchId, @Param("suspended") Integer suspended);

    @Query("""
            select count(p) from TransactionBatchPosting p
            where p.itemId = :itemId and p.status = :suspended
            """)
    long countSuspendedForItem(@Param("itemId") Long itemId, @Param("suspended") Integer suspended);

    @Query("select p.itemId from TransactionBatchPosting p where p.runId = :runId")
    List<Long> findItemIdsByRunId(@Param("runId") Long runId);
}
