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
package org.apache.fineract.portfolio.sector.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_sector")
@Getter
@Setter
@NoArgsConstructor
public class Sector extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "sector_code", length = 20, nullable = false, unique = true)
    private String sectorCode;

    @Column(name = "sector_name", length = 100, nullable = false)
    private String sectorName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "risk_level", length = 20)
    private String riskLevel;

    @Column(name = "regulatory_code", length = 20)
    private String regulatoryCode;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;
}
