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
package org.apache.fineract.portfolio.accounting.glbalance.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class GlBalanceSnapshotId implements Serializable {

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @Column(name = "snapshot_granularity", nullable = false, length = 10)
    private String snapshotGranularity;

    @Column(name = "office_id", nullable = false)
    private Long officeId;

    @Column(name = "department_id", nullable = false)
    private Long departmentId;

    @Column(name = "gl_account_id", nullable = false)
    private Long glAccountId;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode;

    @Override
    public boolean equals(final Object obj) {
        if (!(obj instanceof GlBalanceSnapshotId other)) {
            return false;
        }
        return Objects.equals(snapshotDate, other.snapshotDate) && Objects.equals(snapshotGranularity, other.snapshotGranularity)
                && Objects.equals(officeId, other.officeId) && Objects.equals(departmentId, other.departmentId)
                && Objects.equals(glAccountId, other.glAccountId) && Objects.equals(currencyCode, other.currencyCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(snapshotDate, snapshotGranularity, officeId, departmentId, glAccountId, currencyCode);
    }
}
