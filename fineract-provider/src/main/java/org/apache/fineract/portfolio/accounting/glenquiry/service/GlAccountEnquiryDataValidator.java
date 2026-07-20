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
import org.apache.fineract.portfolio.accounting.glenquiry.data.GlAccountEnquiryRequest;
import org.springframework.stereotype.Component;

@Component
public class GlAccountEnquiryDataValidator {

    public void validate(final GlAccountEnquiryRequest request) {
        final List<ApiParameterError> dataValidationErrors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(dataValidationErrors).resource("GLACCOUNT");

        if (!request.hasAnyFilter()) {
            baseDataValidator.reset().parameter("filters").failWithCode("at.least.one.required");
        }
        baseDataValidator.reset().parameter("officeId").value(request.getOfficeId()).ignoreIfNull().longGreaterThanZero();
        baseDataValidator.reset().parameter("departmentId").value(request.getDepartmentId()).ignoreIfNull().longGreaterThanZero();
        baseDataValidator.reset().parameter("glPrefix").value(request.getGlPrefix()).ignoreIfNull().notExceedingLengthOf(100);
        baseDataValidator.reset().parameter("ledgerNumber").value(request.getLedgerNumber()).ignoreIfNull().notExceedingLengthOf(100);
        baseDataValidator.reset().parameter("currencyCode").value(request.getCurrencyCode()).ignoreIfNull().notExceedingLengthOf(3);
        baseDataValidator.reset().parameter("description").value(request.getDescription()).ignoreIfNull().notExceedingLengthOf(500);

        if (!dataValidationErrors.isEmpty()) {
            throw new PlatformApiDataValidationException(dataValidationErrors);
        }
    }
}
