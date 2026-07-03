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
package org.apache.fineract.workflow.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WorkflowDefinitionRepository
        extends JpaRepository<WorkflowDefinition, Long>, JpaSpecificationExecutor<WorkflowDefinition> {

    List<WorkflowDefinition> findByModuleName(String moduleName);

    List<WorkflowDefinition> findByStatus(WorkflowDefinitionStatus status);

    List<WorkflowDefinition> findByModuleNameAndStatus(String moduleName, WorkflowDefinitionStatus status);

    @Query("SELECT wd FROM WorkflowDefinition wd WHERE wd.moduleName = :moduleName AND wd.status = org.apache.fineract.workflow.domain.WorkflowDefinitionStatus.ACTIVE ORDER BY wd.priority DESC, wd.id ASC")
    List<WorkflowDefinition> findActiveByModuleNameOrderByPriorityDesc(@Param("moduleName") String moduleName);

    boolean existsByModuleNameAndNameIgnoreCase(String moduleName, String name);
}
