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
package org.apache.fineract.treasury.serialization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderItemDirection;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.liquidity.RunType;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.AssumptionSetRequest;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.LadderItemRequest;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RuleSetRequest;
import org.apache.fineract.treasury.serialization.TreasuryLiquidityDataValidator.RunRequest;
import org.junit.jupiter.api.Test;

class TreasuryLiquidityDataValidatorTest {

    private final TreasuryLiquidityDataValidator validator = new TreasuryLiquidityDataValidator(new FromJsonHelper());

    @Test
    void ruleSetReadsEveryField() {
        final RuleSetRequest request = this.validator.validateRuleSet(
                """
                        {"code": "bou_mdi_2004", "name": "MDI", "effectiveDate": "01/01/2004", "dateFormat": "dd/MM/yyyy", "description": "Regime",
                         "buckets": [{"code": "on", "label": "Overnight", "fromDay": 0, "toDay": 1}, {"code": "rest", "label": "Rest", "fromDay": 2}]}
                        """,
                true);
        assertEquals("BOU_MDI_2004", request.code());
        assertEquals("MDI", request.name());
        assertEquals(LocalDate.of(2004, 1, 1), request.effectiveDate());
        assertEquals("Regime", request.description());
        assertNull(request.isDefault());
        assertEquals(List.of(new Bucket(1, "ON", "Overnight", 0, 1), new Bucket(2, "REST", "Rest", 2, null)), request.buckets());

        final RuleSetRequest update = this.validator.validateRuleSet("{\"isDefault\": true}", false);
        assertEquals(Boolean.TRUE, update.isDefault());
        assertTrue(update.has("isDefault"));
    }

    @Test
    void ruleSetNeedsCodeNameDateAndContiguousBuckets() {
        assertEquals(List.of("code", "name", "effectiveDate", "buckets"), parameters(() -> this.validator.validateRuleSet("{}", true)));
        assertEquals(List.of("buckets"), parameters(() -> this.validator.validateRuleSet(
                "{\"code\": \"X\", \"name\": \"X\", \"effectiveDate\": \"2026-01-01\", \"buckets\": [{\"code\": \"A\", \"label\": \"A\", \"fromDay\": 1}]}",
                true)));
        assertEquals(List.of("buckets[0].code", "buckets[0].label", "buckets[0].fromDay"), parameters(() -> this.validator.validateRuleSet(
                "{\"code\": \"X\", \"name\": \"X\", \"effectiveDate\": \"2026-01-01\", \"buckets\": [{\"code\": \"a b\", \"fromDay\": -1}]}",
                true)));
        assertEquals(List.of("isDefault"), parameters(() -> this.validator.validateRuleSet(
                "{\"code\": \"X\", \"name\": \"X\", \"effectiveDate\": \"2026-01-01\", \"isDefault\": true, \"buckets\": [{\"code\": \"A\", \"label\": \"A\", \"fromDay\": 0}]}",
                true)));
        assertEquals(List.of("code"), parameters(() -> this.validator.validateRuleSet("{\"code\": \"Y\"}", false)));
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateRuleSet("{\"ratio\": 15}", false));
    }

    @Test
    void assumptionSetReadsEveryRowField() {
        final AssumptionSetRequest request = this.validator.validateAssumptionSet("""
                {"code": "default", "name": "Base", "effectiveDate": "2026-10-01", "description": "Q4", "assumptions": [
                  {"type": "run_off", "source": "savings", "productId": 3, "bucketCode": "on", "rate": 5},
                  {"type": "ARREARS_HAIRCUT", "source": "LOANS", "minDays": 31, "maxDays": 90, "rate": 50},
                  {"type": "FLOAT_DEMAND", "source": "AGENT_FLOAT", "minDays": 30, "rate": 120}]}
                """, true);
        assertEquals("DEFAULT", request.code());
        assertEquals("Q4", request.description());
        assertEquals(
                List.of(new Assumptions.Row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, 3L, "ON", null, null, new BigDecimal("5")),
                        new Assumptions.Row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 31, 90,
                                new BigDecimal("50")),
                        new Assumptions.Row(AssumptionType.FLOAT_DEMAND, LadderSourceCode.AGENT_FLOAT, null, null, 30, null,
                                new BigDecimal("120"))),
                request.assumptions());
    }

    @Test
    void assumptionRowsAreCheckedByType() {
        assertEquals(List.of("assumptions[0].source"),
                rowError("{\"type\": \"RUN_OFF\", \"source\": \"LOANS\", \"bucketCode\": \"ON\", \"rate\": 5}"));
        assertEquals(List.of("assumptions[0].bucketCode"), rowError("{\"type\": \"RUN_OFF\", \"source\": \"SAVINGS\", \"rate\": 5}"));
        assertEquals(List.of("assumptions[0].bucketCode"),
                rowError("{\"type\": \"ROLLOVER\", \"source\": \"DEALS\", \"bucketCode\": \"ON\", \"rate\": 5}"));
        assertEquals(List.of("assumptions[0].minDays"), rowError("{\"type\": \"ARREARS_HAIRCUT\", \"source\": \"LOANS\", \"rate\": 5}"));
        assertEquals(List.of("assumptions[0].maxDays"),
                rowError("{\"type\": \"ARREARS_HAIRCUT\", \"source\": \"LOANS\", \"minDays\": 30, \"maxDays\": 10, \"rate\": 5}"));
        assertEquals(List.of("assumptions[0].minDays"),
                rowError("{\"type\": \"FLOAT_DEMAND\", \"source\": \"AGENT_FLOAT\", \"minDays\": 400, \"rate\": 5}"));
        assertEquals(List.of("assumptions[0].rate"), rowError("{\"type\": \"PREPAYMENT\", \"source\": \"LOANS\", \"rate\": 101}"));
        assertEquals(List.of("assumptions[0].minDays"),
                rowError("{\"type\": \"PREPAYMENT\", \"source\": \"LOANS\", \"minDays\": 1, \"rate\": 1}"));
        assertEquals(List.of("assumptions[0].type", "assumptions[0].source", "assumptions[0].rate"),
                rowError("{\"type\": \"GUESS\", \"source\": \"MOON\"}"));
    }

    @Test
    void assumptionRowsAreCheckedTogether() {
        assertTrue(TreasuryLiquidityDataValidator
                .checkAssumptions(List.of(
                        new Assumptions.Row(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, null, null, null, null, BigDecimal.ONE),
                        new Assumptions.Row(AssumptionType.ROLLOVER, LadderSourceCode.DEALS, null, null, null, null, BigDecimal.TEN)))
                .contains("two"));
        assertTrue(TreasuryLiquidityDataValidator
                .checkAssumptions(List.of(
                        new Assumptions.Row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 1, 30, BigDecimal.ONE),
                        new Assumptions.Row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 30, null, BigDecimal.TEN)))
                .contains("overlap"));
        assertTrue(TreasuryLiquidityDataValidator.checkAssumptions(List.of(
                new Assumptions.Row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, 1L, "ON", null, null, new BigDecimal("60")),
                new Assumptions.Row(AssumptionType.RUN_OFF, LadderSourceCode.SAVINGS, 1L, "D2_8", null, null, new BigDecimal("41"))))
                .contains("above 100%"));
        assertNull(TreasuryLiquidityDataValidator.checkAssumptions(
                List.of(new Assumptions.Row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 1, 30, BigDecimal.ONE),
                        new Assumptions.Row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, null, null, 31, null, BigDecimal.TEN),
                        new Assumptions.Row(AssumptionType.ARREARS_HAIRCUT, LadderSourceCode.LOANS, 5L, null, 1, null, BigDecimal.TEN))));
        assertEquals(List.of("assumptions"), parameters(() -> this.validator.validateAssumptionSet(
                "{\"assumptions\": [{\"type\": \"DRAWDOWN\", \"source\": \"COMMITMENTS\", \"rate\": 1}, {\"type\": \"DRAWDOWN\", \"source\": \"COMMITMENTS\", \"rate\": 2}]}",
                false)));
    }

    @Test
    void ladderItemReadsEveryFieldAndIsOneOffOrMonthly() {
        final LadderItemRequest item = this.validator.validateLadderItem("""
                {"name": "Salaries", "direction": "outflow", "amount": 85000000, "currencyCode": "ugx", "officeId": 1, "dayOfMonth": 25,
                 "activeFrom": "2026-01-01", "activeTo": "2026-12-31"}
                """, true);
        assertEquals("Salaries", item.name());
        assertEquals(LadderItemDirection.OUTFLOW, item.direction());
        assertEquals(0, new BigDecimal("85000000").compareTo(item.amount()));
        assertEquals("UGX", item.currencyCode());
        assertEquals(1L, item.officeId());
        assertEquals(25, item.dayOfMonth());
        assertNull(item.oneOffDate());
        assertEquals(LocalDate.of(2026, 1, 1), item.activeFrom());
        assertEquals(LocalDate.of(2026, 12, 31), item.activeTo());
        assertEquals(LocalDate.of(2026, 11, 15), this.validator.validateLadderItem(
                "{\"name\": \"Tax\", \"direction\": \"OUTFLOW\", \"amount\": 1, \"currencyCode\": \"UGX\", \"oneOffDate\": \"2026-11-15\", \"activeFrom\": \"2026-01-01\"}",
                true).oneOffDate());

        assertEquals(List.of("oneOffDate"), parameters(() -> this.validator.validateLadderItem(
                "{\"name\": \"X\", \"direction\": \"INFLOW\", \"amount\": 1, \"currencyCode\": \"UGX\", \"activeFrom\": \"2026-01-01\"}",
                true)));
        assertEquals(List.of("dayOfMonth", "activeTo"), parameters(() -> this.validator.validateLadderItem(
                "{\"name\": \"X\", \"direction\": \"INFLOW\", \"amount\": 1, \"currencyCode\": \"UGX\", \"dayOfMonth\": 32, \"activeFrom\": \"2026-01-01\", \"activeTo\": \"2025-01-01\"}",
                true)));
        assertEquals(List.of("name", "direction", "amount", "currencyCode", "activeFrom", "oneOffDate"),
                parameters(() -> this.validator.validateLadderItem("{}", true)));
    }

    @Test
    void runReadsItsFieldsAndDefaultsToALadder() {
        final RunRequest run = this.validator.validateRun(
                "{\"type\": \"ladder\", \"asOfDate\": \"2026-10-16\", \"currencyCode\": \"ugx\", \"officeId\": 2, \"ruleSetId\": 3, \"assumptionSetId\": 4}");
        assertEquals(new RunRequest(RunType.LADDER, LocalDate.of(2026, 10, 16), "UGX", 2L, 3L, 4L, null, null), run);
        assertEquals(RunType.LADDER, this.validator.validateRun("{\"currencyCode\": \"UGX\"}").type());
        assertEquals(List.of("type", "currencyCode"), parameters(() -> this.validator.validateRun("{\"type\": \"FORECAST\"}")));
        assertEquals(new RunRequest(RunType.STRESS, null, "UGX", null, null, null, 15L, 2L),
                this.validator.validateRun("{\"type\": \"stress\", \"currencyCode\": \"UGX\", \"sourceRunId\": 15, \"scenarioId\": 2}"));
        assertEquals(List.of("scenarioId"),
                parameters(() -> this.validator.validateRun("{\"type\": \"STRESS\", \"currencyCode\": \"UGX\"}")));
        assertEquals(List.of("scenarioId"), parameters(() -> this.validator.validateRun("{\"currencyCode\": \"UGX\", \"scenarioId\": 2}")));
        assertNull(this.validator.validateRun("{\"type\": \"STRESS\", \"sourceRunId\": 15, \"scenarioId\": 2}").currencyCode(),
                "the source run's currency");
        assertThrows(UnsupportedParameterException.class, () -> this.validator.validateActivate("{\"force\": true}"));
    }

    private List<String> rowError(final String row) {
        return parameters(() -> this.validator.validateAssumptionSet("{\"assumptions\": [" + row + "]}", false));
    }

    private static List<String> parameters(final org.junit.jupiter.api.function.Executable call) {
        return assertThrows(PlatformApiDataValidationException.class, call).getErrors().stream().map(ApiParameterError::getParameterName)
                .distinct().toList();
    }
}
