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
package org.apache.fineract.portfolio.accounting.glenquiry.service;

import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountLedgerRequest;
import org.springframework.stereotype.Component;

@Component
public class GlAccountLedgerDataValidator {

    public void validate(final GlAccountLedgerRequest request) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("GLACCOUNT");

        baseDataValidator.reset().parameter("glAccountId").value(request.getGlAccountId()).notNull().longGreaterThanZero();
        baseDataValidator.reset().parameter("startDate").value(request.getStartDate()).notNull();
        baseDataValidator.reset().parameter("endDate").value(request.getEndDate()).notNull();
        baseDataValidator.reset().parameter("officeId").value(request.getOfficeId()).notNull().longGreaterThanZero();
        baseDataValidator.reset().parameter("currencyCode").value(request.getCurrencyCode()).notBlank().notExceedingLengthOf(3);
        baseDataValidator.reset().parameter("departmentId").value(request.getDepartmentId()).ignoreIfNull().longZeroOrGreater();

        if (request.getStartDate() != null && request.getEndDate() != null && request.getEndDate().isBefore(request.getStartDate())) {
            baseDataValidator.reset().parameter("endDate").failWithCode("cannot.be.before.startDate");
        }

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
