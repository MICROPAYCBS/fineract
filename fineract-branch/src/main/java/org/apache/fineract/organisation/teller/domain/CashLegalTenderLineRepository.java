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
package org.apache.fineract.organisation.teller.domain;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CashLegalTenderLineRepository extends JpaRepository<CashLegalTenderLine, Long> {

    @Query("select line from CashLegalTenderLine line join fetch line.legalTender lt "
            + "where line.sourceType = :sourceType and line.sourceId in :sourceIds order by lt.displayOrder asc")
    List<CashLegalTenderLine> findBySourceTypeAndSourceIds(@Param("sourceType") Integer sourceType,
            @Param("sourceIds") Collection<Long> sourceIds);

    @Query("SELECT CASE WHEN COUNT(line) > 0 THEN TRUE ELSE FALSE END FROM CashLegalTenderLine line WHERE line.legalTender.id = :legalTenderId")
    boolean existsByLegalTenderId(@Param("legalTenderId") Long legalTenderId);
}
