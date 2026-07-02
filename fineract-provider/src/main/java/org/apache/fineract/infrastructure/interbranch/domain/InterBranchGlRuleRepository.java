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
package org.apache.fineract.infrastructure.interbranch.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InterBranchGlRuleRepository extends JpaRepository<InterBranchGlRule, Long> {

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END FROM InterBranchGlRule r
            WHERE r.leftOffice IS NULL AND r.rightOffice IS NULL AND r.id <> :excludeId
            """)
    boolean existsDefaultRuleExcludingId(@Param("excludeId") Long excludeId);

    @Query("""
            SELECT CASE WHEN COUNT(r) > 0 THEN TRUE ELSE FALSE END FROM InterBranchGlRule r
            WHERE r.leftOffice IS NULL AND r.rightOffice IS NULL
            """)
    boolean existsDefaultRule();

    @Query("""
            SELECT r FROM InterBranchGlRule r WHERE r.status = 'ACTIVE'
            AND (r.currencyCode IS NULL OR r.currencyCode = :currencyCode)
            AND (
              (r.leftOffice.id = :servicingOfficeId AND r.rightOffice.id = :homeOfficeId)
              OR (r.leftOffice.id = :homeOfficeId AND r.rightOffice.id = :servicingOfficeId)
              OR (r.leftOffice IS NULL AND r.rightOffice IS NULL)
            )
            ORDER BY CASE WHEN r.leftOffice IS NULL THEN 1 ELSE 0 END
            """)
    List<InterBranchGlRule> findActiveRulesForOffices(@Param("servicingOfficeId") Long servicingOfficeId,
            @Param("homeOfficeId") Long homeOfficeId, @Param("currencyCode") String currencyCode);
}
