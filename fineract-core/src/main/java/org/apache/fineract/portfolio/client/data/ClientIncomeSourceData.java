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

package org.apache.fineract.portfolio.client.data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Collection;
import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.infrastructure.codes.data.CodeValueData;

@Getter
@Builder
public final class ClientIncomeSourceData implements Serializable {

    private final Long id;
    private final Long clientId;
    private final Long incomeSourceTypeId;
    private final String incomeSourceType;
    private final Long sourceOfFundsId;
    private final String sourceOfFunds;
    private final String employerBusinessName;
    private final String employerAddress;
    private final String occupation;
    private final Long subIndustryId;
    private final String subIndustryName;
    private final BigDecimal monthlyIncome;
    private final String incomeCurrencyCode;
    private final Long incomeFrequencyId;
    private final String incomeFrequency;
    private final LocalDate startDate;
    private final LocalDate endDate;
    private final Boolean isPrimarySource;
    private final Long verificationStatusId;
    private final String verificationStatus;
    private final Long verifiedBy;
    private final String verifiedByUsername;
    private final OffsetDateTime verifiedOnUtc;
    private final String supportingDocument;
    private final String remarks;
    private final String status;

    // template holder
    private final Collection<CodeValueData> incomeSourceTypeOptions;
    private final Collection<CodeValueData> sourceOfFundsOptions;
    private final Collection<CodeValueData> incomeFrequencyOptions;
    private final Collection<CodeValueData> verificationStatusOptions;
}
