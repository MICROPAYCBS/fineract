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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.exception.UnsupportedParameterException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.limits.LimitLevel;
import org.apache.fineract.treasury.limits.LimitMetric;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.FundingLineRequest;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.ScenarioRequest;
import org.apache.fineract.treasury.serialization.TreasuryStressDataValidator.TriggerRequest;
import org.apache.fineract.treasury.stress.CfpStage;
import org.apache.fineract.treasury.stress.FundingLineStatus;
import org.apache.fineract.treasury.stress.FundingSourceType;
import org.apache.fineract.treasury.stress.ScenarioType;
import org.apache.fineract.treasury.stress.Shock;
import org.apache.fineract.treasury.stress.ShockSide;
import org.apache.fineract.treasury.stress.ShockType;
import org.apache.fineract.treasury.stress.TriggerCondition;
import org.junit.jupiter.api.Test;

/** Every field of scenarios and their shocks, funding lines, triggers and activation steps. */
class TreasuryStressDataValidatorTest {

    private final TreasuryStressDataValidator validator = new TreasuryStressDataValidator(new FromJsonHelper());

    @Test
    void aScenarioWithEveryShockType() {
        final ScenarioRequest r = this.validator.validateScenario("""
                {"code": "run", "name": "Deposit run", "scenarioType": "idiosyncratic", "severity": "Severe", "effectiveDate": "2026-10-01",
                 "description": "Placeholder", "shocks": [
                  {"type": "add_outflow_pct_of_balance", "label": "Run", "source": "savings", "lineCode": "demand_savings", "productId": 4,
                   "bucketCode": "d2_8", "side": "outflow", "factor": 20},
                  {"type": "REMOVE_ROLLOVER", "label": "TDs", "source": "TERM_DEPOSITS"},
                  {"type": "MULTIPLY", "label": "Collections", "source": "LOANS", "side": "INFLOW", "factor": 70},
                  {"type": "HAIRCUT_LIQUID_ASSET", "label": "Bonds", "assetClass": "govt_bond", "factor": 15},
                  {"type": "FREEZE_LINE", "label": "Lines", "fundingSourceType": "uncommitted_line"},
                  {"type": "FREEZE_LINE", "label": "One line", "fundingLineId": 3}]}
                """, true);
        assertEquals("RUN", r.code());
        assertEquals("Deposit run", r.name());
        assertEquals(ScenarioType.IDIOSYNCRATIC, r.scenarioType());
        assertEquals("Severe", r.severity());
        assertEquals(LocalDate.of(2026, 10, 1), r.effectiveDate());
        assertEquals("Placeholder", r.description());
        final List<Shock> s = r.shocks();
        assertEquals(new Shock(ShockType.ADD_OUTFLOW_PCT_OF_BALANCE, "Run", LadderSourceCode.SAVINGS, "DEMAND_SAVINGS", 4L, "D2_8",
                ShockSide.OUTFLOW, BigDecimal.valueOf(20), null, null, null), s.get(0));
        assertEquals(ShockSide.BOTH, s.get(1).side(), "BOTH by default");
        assertEquals(new Shock(ShockType.HAIRCUT_LIQUID_ASSET, "Bonds", null, null, null, null, null, BigDecimal.valueOf(15), "GOVT_BOND",
                null, null), s.get(3));
        assertEquals(FundingSourceType.UNCOMMITTED_LINE, s.get(4).fundingSourceType());
        assertEquals(3L, s.get(5).fundingLineId());
        assertNull(r.copyFromId());
    }

    @Test
    void copiesAndUpdates() {
        final ScenarioRequest copy = this.validator.validateScenario("{\"copyFromId\": 7, \"severity\": \"Mild\"}", true);
        assertEquals(7L, copy.copyFromId());
        assertNull(copy.code(), "the source's code");
        assertNull(copy.shocks(), "the source's shocks");
        final ScenarioRequest update = this.validator.validateScenario("{\"name\": \"New\"}", false);
        assertTrue(update.has("name"));
        assertFalse(update.has("shocks"));
        assertEquals(List.of("code"), parameters(() -> this.validator.validateScenario("{\"code\": \"X\"}", false)));
        assertEquals(List.of("copyFromId"), parameters(() -> this.validator.validateScenario("{\"copyFromId\": 7}", false)));
    }

    @Test
    void scenarioRules() {
        assertEquals(List.of("code", "name", "scenarioType", "shocks"), parameters(() -> this.validator.validateScenario("{}", true)));
        assertEquals(List.of("shocks"), parameters(() -> scenario("")));
        assertEquals(List.of("shocks[0].type", "shocks[0].label"), parameters(() -> scenario("{}")));
        assertEquals(List.of("shocks[0].source"), parameters(() -> scenario("{\"type\": \"MULTIPLY\", \"label\": \"x\", \"factor\": 50}")));
        assertEquals(List.of("shocks[0].source"),
                parameters(() -> scenario("{\"type\": \"MULTIPLY\", \"label\": \"x\", \"source\": \"TOTAL\", \"factor\": 50}")));
        assertEquals(List.of("shocks[0].factor"),
                parameters(() -> scenario("{\"type\": \"MULTIPLY\", \"label\": \"x\", \"source\": \"LOANS\"}")));
        assertEquals(List.of("shocks[0].factor"),
                parameters(() -> scenario("{\"type\": \"MULTIPLY\", \"label\": \"x\", \"source\": \"LOANS\", \"factor\": 1001}")));
        assertEquals(List.of("shocks[0].factor"), parameters(
                () -> scenario("{\"type\": \"ADD_OUTFLOW_PCT_OF_BALANCE\", \"label\": \"x\", \"source\": \"SAVINGS\", \"factor\": 101}")));
        assertEquals(List.of("shocks[0].side"), parameters(() -> scenario(
                "{\"type\": \"ADD_OUTFLOW_PCT_OF_BALANCE\", \"label\": \"x\", \"source\": \"SAVINGS\", \"factor\": 10, \"side\": \"INFLOW\"}")));
        assertEquals(List.of("shocks[0].source"),
                parameters(() -> scenario("{\"type\": \"REMOVE_ROLLOVER\", \"label\": \"x\", \"source\": \"LOANS\"}")));
        assertEquals(List.of("shocks[0].factor"),
                parameters(() -> scenario("{\"type\": \"REMOVE_ROLLOVER\", \"label\": \"x\", \"source\": \"DEALS\", \"factor\": 10}")));
        assertEquals(List.of("shocks[0].bucketCode", "shocks[0].source", "shocks[0].assetClass"), parameters(() -> scenario(
                "{\"type\": \"HAIRCUT_LIQUID_ASSET\", \"label\": \"x\", \"source\": \"DEALS\", \"bucketCode\": \"ON\", \"factor\": 10}")));
        assertEquals(List.of("shocks[0].fundingSourceType"), parameters(() -> scenario("{\"type\": \"FREEZE_LINE\", \"label\": \"x\"}")));
        assertEquals(List.of("shocks[0].fundingSourceType"), parameters(() -> scenario(
                "{\"type\": \"FREEZE_LINE\", \"label\": \"x\", \"fundingSourceType\": \"REPO_BOU\", \"fundingLineId\": 2}")));
        assertEquals(List.of("shocks[0].assetClass"), parameters(() -> scenario(
                "{\"type\": \"MULTIPLY\", \"label\": \"x\", \"source\": \"LOANS\", \"factor\": 1, \"assetClass\": \"TBILL\"}")));
        assertThrows(UnsupportedParameterException.class, () -> scenario("{\"type\": \"MULTIPLY\", \"rate\": 5}"));
    }

    @Test
    void aFundingLine() {
        final FundingLineRequest r = this.validator.validateFundingLine("""
                {"code": "dfcu_line", "name": "DFCU committed line", "sourceType": "committed_line_received", "counterpartyId": 4,
                 "currencyCode": "ugx", "committedAmount": 500000000, "drawnAmount": 100000000, "daysToAccess": 3,
                 "expiryDate": "2027-06-30", "ownerUserId": 5, "ownerName": "Treasurer", "description": "Standby"}
                """, null);
        assertEquals(new FundingLineRequest("DFCU_LINE", "DFCU committed line", FundingSourceType.COMMITTED_LINE_RECEIVED, 4L, "UGX",
                BigDecimal.valueOf(500000000), BigDecimal.valueOf(100000000), null, 3, LocalDate.of(2027, 6, 30), 5L, "Treasurer",
                FundingLineStatus.ACTIVE, true, "Standby"), r);
        final FundingLineRequest drive = this.validator.validateFundingLine("""
                {"code": "drive", "name": "Deposit drive", "sourceType": "DEPOSIT_DRIVE", "currencyCode": "UGX", "committedAmount": 0,
                 "availableAmount": 20000000, "daysToAccess": 30, "status": "INACTIVE"}
                """, null);
        assertFalse(drive.includeInCapacity(), "only committed lines count by default");
        assertEquals(0, BigDecimal.ZERO.compareTo(drive.drawnAmount()));
        assertEquals(FundingLineStatus.INACTIVE, drive.status());
        assertEquals(List.of("code", "name", "sourceType", "currencyCode", "committedAmount", "daysToAccess"),
                parameters(() -> this.validator.validateFundingLine("{}", null)));
        assertEquals(List.of("drawnAmount"), parameters(() -> line("\"drawnAmount\": 600")));
        assertEquals(List.of("availableAmount"), parameters(() -> line("\"drawnAmount\": 100, \"availableAmount\": 450")));
        assertEquals(List.of("daysToAccess"), parameters(() -> line("\"daysToAccess\": 3651")));
        assertEquals(List.of("includeInCapacity"), parameters(() -> line("\"includeInCapacity\": \"yes\"")));
        final JsonObject current = JsonParser.parseString(lineJson("\"drawnAmount\": 100")).getAsJsonObject();
        final FundingLineRequest update = this.validator.validateFundingLine("{\"drawnAmount\": 200, \"includeInCapacity\": false}",
                current);
        assertEquals(0, BigDecimal.valueOf(200).compareTo(update.drawnAmount()));
        assertFalse(update.includeInCapacity());
        assertEquals(List.of("code"), parameters(() -> this.validator.validateFundingLine("{\"code\": \"OTHER\"}", current)));
    }

    @Test
    void triggersOfEachCondition() {
        final TriggerRequest survival = this.validator.validateTrigger("""
                {"code": "short_survival", "name": "Survival under 30 days", "conditionType": "survival_below", "survivalDays": 30,
                 "scenarioCode": "combined", "stage": "alert", "notifyRoleId": 3, "description": "ALCO",
                 "actions": [{"description": "Draw the DFCU line", "ownerUserId": 5, "fundingLineId": 2},
                             {"description": "Call ALCO", "ownerRoleId": 3}]}
                """, null);
        assertEquals(new TriggerRequest("SHORT_SURVIVAL", "Survival under 30 days", TriggerCondition.SURVIVAL_BELOW, null, null, null,
                BigDecimal.valueOf(30), "COMBINED", CfpStage.ALERT, 3L, "ALCO",
                List.of(new TreasuryStressDataValidator.ActionSpec("Draw the DFCU line", null, 5L, 2L),
                        new TreasuryStressDataValidator.ActionSpec("Call ALCO", 3L, null, null))),
                survival);
        final TriggerRequest breach = this.validator.validateTrigger(
                "{\"code\": \"B\", \"name\": \"B\", \"conditionType\": \"LIMIT_BREACH\", \"metric\": \"SURVIVAL_DAYS\", \"stage\": \"WATCH\"}",
                null);
        assertEquals(LimitMetric.SURVIVAL_DAYS, breach.metric());
        assertEquals(LimitLevel.WARNING, breach.level(), "WARNING by default");
        assertTrue(breach.actions().isEmpty());
        assertEquals(List.of("code", "name", "conditionType", "stage"), parameters(() -> this.validator.validateTrigger("{}", null)));
        assertEquals(List.of("limitId"), parameters(() -> trigger("LIMIT_BREACH", "")));
        assertEquals(List.of("limitId"), parameters(() -> trigger("LIMIT_BREACH", ", \"limitId\": 4, \"metric\": \"RATIO\"")));
        assertEquals(List.of("survivalDays"), parameters(() -> trigger("SURVIVAL_BELOW", ", \"survivalDays\": 0")));
        assertEquals(List.of("level", "limitId"),
                parameters(() -> trigger("SURVIVAL_BELOW", ", \"survivalDays\": 5, \"limitId\": 4, \"level\": \"HARD\"")));
        assertEquals(List.of("scenarioCode", "survivalDays"),
                parameters(() -> trigger("MANUAL", ", \"survivalDays\": 5, \"scenarioCode\": \"X\"")));
        assertEquals(List.of("actions[0].description", "actions[0].ownerRoleId"),
                parameters(() -> trigger("MANUAL", ", \"actions\": [{\"ownerRoleId\": 0}]")));
        assertEquals(TriggerCondition.MANUAL, trigger("MANUAL", "").conditionType());
    }

    @Test
    void activationSteps() {
        assertEquals(new TreasuryStressDataValidator.ActionStep(4L, "Drawn"),
                this.validator.validateActivationStep("{\"actionId\": 4, \"comment\": \"Drawn\"}", true));
        assertNull(this.validator.validateActivationStep("{\"actionId\": 4}", true).comment());
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateActivationStep("{}", true));
        assertEquals("Over", this.validator.validateActivationStep("{\"comment\": \"Over\"}", false).comment());
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateActivationStep("{}", false));
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateActivationStep("{\"actionId\": 1, \"comment\": \"x\"}", false));
        assertEquals("n", this.validator.validateNote("{\"note\": \"n\"}"));
    }

    private ScenarioRequest scenario(final String shock) {
        return this.validator.validateScenario(
                "{\"code\": \"S\", \"name\": \"S\", \"scenarioType\": \"MARKET_WIDE\", \"shocks\": [" + shock + "]}", true);
    }

    private static String lineJson(final String extra) {
        return "{\"code\": \"L\", \"name\": \"L\", \"sourceType\": \"COMMITTED_LINE_RECEIVED\", \"currencyCode\": \"UGX\", "
                + "\"committedAmount\": 500, \"daysToAccess\": 2, " + extra + "}";
    }

    private FundingLineRequest line(final String extra) {
        return this.validator.validateFundingLine(lineJson(extra), null);
    }

    private TriggerRequest trigger(final String condition, final String extra) {
        return this.validator.validateTrigger(
                "{\"code\": \"T\", \"name\": \"T\", \"stage\": \"CRISIS\", \"conditionType\": \"" + condition + "\"" + extra + "}", null);
    }

    private static List<String> parameters(final org.junit.jupiter.api.function.Executable call) {
        return assertThrows(PlatformApiDataValidationException.class, call).getErrors().stream().map(ApiParameterError::getParameterName)
                .distinct().toList();
    }
}
