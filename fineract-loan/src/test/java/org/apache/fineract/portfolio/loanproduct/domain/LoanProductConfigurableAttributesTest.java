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
package org.apache.fineract.portfolio.loanproduct.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.gson.JsonParser;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;
import org.junit.jupiter.api.Test;

class LoanProductConfigurableAttributesTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @Test
    void readFlagUsesFallbackWhenKeyIsMissingOrNull() {
        assertThat(LoanProductConfigurableAttributes.readFlag(null, "interestType", Boolean.TRUE)).isTrue();
        final var overrides = JsonParser.parseString("{\"interestType\":false,\"amortizationType\":null}").getAsJsonObject();
        assertThat(LoanProductConfigurableAttributes.readFlag(overrides, LoanProductConstants.interestTypeParamName, Boolean.TRUE))
                .isFalse();
        assertThat(LoanProductConfigurableAttributes.readFlag(overrides, LoanProductConstants.amortizationTypeParamName, Boolean.TRUE))
                .isTrue();
        assertThat(LoanProductConfigurableAttributes.readFlag(overrides, LoanProductConstants.repaymentEveryParamName, Boolean.FALSE))
                .isFalse();
    }

    @Test
    void createFromDefaultsMissingFlagsToTrue() {
        final String json = "{\"allowAttributeOverrides\":{\"interestType\":false}}";
        final JsonCommand command = JsonCommand.from(json, JsonParser.parseString(json), this.fromJsonHelper, "LOANPRODUCT", null, null,
                null, null, null, null, null, null, null, null, null, null, null);

        final LoanProductConfigurableAttributes attributes = LoanProductConfigurableAttributes.createFrom(command);

        assertThat(attributes.getInterestMethodBoolean()).isFalse();
        assertThat(attributes.getAmortizationBoolean()).isTrue();
        assertThat(attributes.getRepaymentEveryBoolean()).isTrue();
        assertThat(attributes.getGraceOnArrearsAgingBoolean()).isTrue();
    }
}
