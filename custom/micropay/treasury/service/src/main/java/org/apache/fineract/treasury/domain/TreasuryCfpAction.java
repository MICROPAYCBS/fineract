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

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;

/** An action of a trigger, in its order. */
@Getter
@Setter
@Entity
@NoArgsConstructor
@Table(name = "m_treasury_cfp_action")
public class TreasuryCfpAction extends AbstractPersistableCustom<Long> {

    @Column(name = "trigger_id", nullable = false)
    private Long triggerId;

    @Column(name = "seq", nullable = false)
    private int sequenceNumber;

    @Column(name = "description", nullable = false, length = 500)
    private String description;

    @Column(name = "owner_role_id")
    private Long ownerRoleId;

    @Column(name = "owner_user_id")
    private Long ownerUserId;

    @Column(name = "funding_line_id")
    private Long fundingLineId;
}
