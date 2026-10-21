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
package org.apache.fineract.portfolio.client.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractAuditableWithUTCDateTimeCustom;

@Entity
@Table(name = "m_contact_type")
@Getter
@Setter
@NoArgsConstructor
public class ContactType extends AbstractAuditableWithUTCDateTimeCustom<Long> {

    @Column(name = "type_code", length = 20, nullable = false, unique = true)
    private String typeCode;

    @Column(name = "type_name", length = 100, nullable = false)
    private String typeName;

    @Column(name = "example", length = 255)
    private String example;

    @Column(name = "validation_regex", length = 500)
    private String validationRegex;

    @Column(name = "mandatory_ind", length = 1, nullable = false)
    private String mandatoryInd = "N";

    @Column(name = "display_order")
    private Integer displayOrder;

    @Column(name = "status", length = 20, nullable = false)
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    public boolean isMandatory() {
        return "Y".equalsIgnoreCase(this.mandatoryInd);
    }

    public void setMandatory(final Boolean mandatory) {
        this.mandatoryInd = Boolean.TRUE.equals(mandatory) ? "Y" : "N";
    }
}
