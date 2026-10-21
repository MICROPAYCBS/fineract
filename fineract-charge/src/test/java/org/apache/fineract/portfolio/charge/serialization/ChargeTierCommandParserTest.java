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
package org.apache.fineract.portfolio.charge.serialization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.charge.domain.ChargeAppliesTo;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ChargeTierCommandParserTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();
    private ChargeTierCommandParser parser;
    private List<ApiParameterError> errors;
    private DataValidatorBuilder validator;

    @BeforeEach
    void setUp() {
        parser = new ChargeTierCommandParser(fromJsonHelper);
        errors = new ArrayList<>();
        validator = new DataValidatorBuilder(errors).resource("charge");
    }

    @Test
    void contiguousLoanTiersPassValidation() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amountRangeTo": 100000, "amount": 50 },
                    { "amountRangeFrom": 100000, "amountRangeTo": null, "amount": 100 }
                  ]
                }
                """);

        parser.validate(element, ChargeAppliesTo.LOAN.getValue(), validator);

        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void savingsTiersPassValidation() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amountRangeTo": 500, "amount": 5 },
                    { "amountRangeFrom": 500, "amount": 10 }
                  ]
                }
                """);

        parser.validate(element, ChargeAppliesTo.SAVINGS.getValue(), validator);

        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void clientChargesRejectTiers() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amount": 10 }
                  ]
                }
                """);

        parser.validate(element, ChargeAppliesTo.CLIENT.getValue(), validator);

        assertFalse(errors.isEmpty());
        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("not.supported.for.charge.applies.to")));
    }

    @Test
    void gapBetweenRangesFails() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amountRangeTo": 100, "amount": 5 },
                    { "amountRangeFrom": 200, "amount": 10 }
                  ]
                }
                """);

        parser.validate(element, ChargeAppliesTo.LOAN.getValue(), validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("must.equal.previous.to")));
    }

    @Test
    void mustStartAtZero() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "chargeTiers": [
                    { "amountRangeFrom": 10, "amount": 5 }
                  ]
                }
                """);

        parser.validate(element, ChargeAppliesTo.LOAN.getValue(), validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("must.start.at.zero")));
    }

    @Test
    void onlyLastTierMayBeOpenEnded() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amount": 5 },
                    { "amountRangeFrom": 100, "amount": 10 }
                  ]
                }
                """);

        parser.validate(element, ChargeAppliesTo.LOAN.getValue(), validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("only.last.tier.may.be.open.ended")));
    }

    @Test
    void hasChargeTiersDetectsNonEmptyArray() {
        assertTrue(parser.hasChargeTiers(fromJsonHelper.parse("""
                { "chargeTiers": [ { "amountRangeFrom": 0, "amount": 1 } ] }
                """)));
        assertFalse(parser.hasChargeTiers(fromJsonHelper.parse("""
                { "chargeTiers": [] }
                """)));
        assertFalse(parser.hasChargeTiers(fromJsonHelper.parse("{}")));
    }

    @Test
    void extractUseChargeTiersDefaultsFalse() {
        assertFalse(parser.extractUseChargeTiers(fromJsonHelper.parse("{}")));
        assertTrue(parser.extractUseChargeTiers(fromJsonHelper.parse("""
                { "useChargeTiers": true }
                """)));
    }

    @Test
    void supportsChargeTiersAllowList() {
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.LOAN, ChargeTimeType.DISBURSEMENT));
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.LOAN, ChargeTimeType.TRANCHE_DISBURSEMENT));
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.LOAN, ChargeTimeType.SPECIFIED_DUE_DATE));
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.LOAN, ChargeTimeType.INSTALMENT_FEE));
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.LOAN, ChargeTimeType.OVERDUE_INSTALLMENT));
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.SAVINGS, ChargeTimeType.WITHDRAWAL_FEE));
        assertTrue(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.SAVINGS, ChargeTimeType.SAVINGS_NOACTIVITY_FEE));

        assertFalse(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.SAVINGS, ChargeTimeType.SAVINGS_ACTIVATION));
        assertFalse(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.SAVINGS, ChargeTimeType.ANNUAL_FEE));
        assertFalse(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.SAVINGS, ChargeTimeType.MONTHLY_FEE));
        assertFalse(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.SAVINGS, ChargeTimeType.WEEKLY_FEE));
        assertFalse(ChargeTierCommandParser.supportsChargeTiers(ChargeAppliesTo.CLIENT, ChargeTimeType.SPECIFIED_DUE_DATE));
    }

    @Test
    void validateModeRejectsTiersWhenFlagFalse() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "useChargeTiers": false,
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amount": 10 }
                  ]
                }
                """);

        parser.validateMode(element, ChargeAppliesTo.LOAN.getValue(), ChargeTimeType.DISBURSEMENT.getValue(), false, false, validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("not.allowed.when.useChargeTiers.false")));
    }

    @Test
    void validateModeRequiresTiersWhenFlagTrue() {
        final JsonElement element = fromJsonHelper.parse("""
                { "locale": "en", "useChargeTiers": true }
                """);

        parser.validateMode(element, ChargeAppliesTo.LOAN.getValue(), ChargeTimeType.DISBURSEMENT.getValue(), true, true, validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("required.when.useChargeTiers")));
    }

    @Test
    void validateModeRejectsMinCapWhenTiered() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "useChargeTiers": true,
                  "minCap": 1,
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amountRangeTo": 100, "amount": 5 },
                    { "amountRangeFrom": 100, "amount": 10 }
                  ]
                }
                """);

        parser.validateMode(element, ChargeAppliesTo.LOAN.getValue(), ChargeTimeType.DISBURSEMENT.getValue(), true, true, validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("not.supported.when.useChargeTiers")));
    }

    @Test
    void validateModeRejectsSavingsActivation() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "useChargeTiers": true,
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amount": 10 }
                  ]
                }
                """);

        parser.validateMode(element, ChargeAppliesTo.SAVINGS.getValue(), ChargeTimeType.SAVINGS_ACTIVATION.getValue(), true, true,
                validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("not.supported.for.charge.time.type")));
    }

    @Test
    void validateModeAllowsWithdrawalFee() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "useChargeTiers": true,
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amountRangeTo": 100, "amount": 5 },
                    { "amountRangeFrom": 100, "amount": 10 }
                  ]
                }
                """);

        parser.validateMode(element, ChargeAppliesTo.SAVINGS.getValue(), ChargeTimeType.WITHDRAWAL_FEE.getValue(), true, true, validator);

        assertTrue(errors.isEmpty(), errors::toString);
    }

    @Test
    void validateModeRejectsAnnualFee() {
        final JsonElement element = fromJsonHelper.parse("""
                {
                  "locale": "en",
                  "useChargeTiers": true,
                  "chargeTiers": [
                    { "amountRangeFrom": 0, "amount": 10 }
                  ]
                }
                """);

        parser.validateMode(element, ChargeAppliesTo.SAVINGS.getValue(), ChargeTimeType.ANNUAL_FEE.getValue(), true, true, validator);

        assertTrue(errors.stream().anyMatch(e -> e.getUserMessageGlobalisationCode().contains("not.supported.for.charge.time.type")));
    }
}
