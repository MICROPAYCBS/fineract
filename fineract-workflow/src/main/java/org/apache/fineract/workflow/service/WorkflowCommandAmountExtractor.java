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
package org.apache.fineract.workflow.service;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.springframework.stereotype.Component;

/**
 * Resolves amount and currency from a held command for workflow selection criteria.
 */
@Component
@RequiredArgsConstructor
public class WorkflowCommandAmountExtractor {

    private static final List<String> AMOUNT_PARAMETER_NAMES = List.of("transactionAmount", "approvedLoanAmount", "principal", "amount");

    private static final List<String> CURRENCY_PARAMETER_NAMES = List.of("currencyCode", "currency");

    private final FromJsonHelper fromApiJsonHelper;

    public WorkflowCommandAmountContext extract(final CommandSource commandSource, final JsonCommand command) {
        final String json = command.json();
        if (json == null || json.isBlank()) {
            return WorkflowCommandAmountContext.empty();
        }
        final JsonElement element = this.fromApiJsonHelper.parse(json);
        if (!element.isJsonObject()) {
            return WorkflowCommandAmountContext.empty();
        }

        BigDecimal amount = null;
        for (final String parameterName : AMOUNT_PARAMETER_NAMES) {
            if (this.fromApiJsonHelper.parameterExists(parameterName, element)) {
                amount = this.fromApiJsonHelper.extractBigDecimalWithLocaleNamed(parameterName, element);
                break;
            }
        }

        String currencyCode = null;
        for (final String parameterName : CURRENCY_PARAMETER_NAMES) {
            if (this.fromApiJsonHelper.parameterExists(parameterName, element)) {
                currencyCode = this.fromApiJsonHelper.extractStringNamed(parameterName, element);
                break;
            }
        }

        return new WorkflowCommandAmountContext(amount, currencyCode);
    }
}
