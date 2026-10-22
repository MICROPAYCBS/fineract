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
package org.apache.fineract.treasury.liquidity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class RatioCalculatorTest {

    @Test
    void eligibleAfterExclusionsHaircutWeightAndSign() {
        // a bond holding of 300,000 with 75,000 excluded, 10% haircut: 202,500
        assertEquals(new BigDecimal("202500.00"),
                RatioCalculator.eligible(new BigDecimal("300000"), new BigDecimal("75000"), BigDecimal.TEN, BigDecimal.valueOf(100), 1, 2));
        // deposits at a 90% stable funding factor
        assertEquals(new BigDecimal("900000.00"),
                RatioCalculator.eligible(new BigDecimal("1000000"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(90), 1, 2));
        assertEquals(new BigDecimal("-500.00"),
                RatioCalculator.eligible(new BigDecimal("500"), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.valueOf(100), -1, 2));
    }

    @Test
    void ratioInPercentAndNoneWithoutADenominator() {
        assertEquals(new BigDecimal("20.2500"), RatioCalculator.ratio(new BigDecimal("202500"), new BigDecimal("1000000")));
        assertEquals(new BigDecimal("33.3333"), RatioCalculator.ratio(BigDecimal.ONE, BigDecimal.valueOf(3)));
        assertNull(RatioCalculator.ratio(BigDecimal.TEN, BigDecimal.ZERO));
    }

    @Test
    void statusAgainstLimitAndWarning() {
        final BigDecimal min = BigDecimal.valueOf(15);
        final BigDecimal warning = BigDecimal.valueOf(17);
        assertEquals(RatioStatus.OK, RatioCalculator.status(RatioDirection.MIN, new BigDecimal("17"), min, warning));
        assertEquals(RatioStatus.WARNING, RatioCalculator.status(RatioDirection.MIN, new BigDecimal("16.9999"), min, warning));
        assertEquals(RatioStatus.WARNING, RatioCalculator.status(RatioDirection.MIN, new BigDecimal("15"), min, warning));
        assertEquals(RatioStatus.BREACH, RatioCalculator.status(RatioDirection.MIN, new BigDecimal("14.9999"), min, warning));
        assertEquals(RatioStatus.OK, RatioCalculator.status(RatioDirection.MIN, new BigDecimal("14.99"), BigDecimal.TEN, null));
        final BigDecimal max = BigDecimal.valueOf(80);
        final BigDecimal maxWarning = BigDecimal.valueOf(75);
        assertEquals(RatioStatus.OK, RatioCalculator.status(RatioDirection.MAX, new BigDecimal("75"), max, maxWarning));
        assertEquals(RatioStatus.WARNING, RatioCalculator.status(RatioDirection.MAX, new BigDecimal("75.01"), max, maxWarning));
        assertEquals(RatioStatus.BREACH, RatioCalculator.status(RatioDirection.MAX, new BigDecimal("80.01"), max, maxWarning));
        assertEquals(RatioStatus.BREACH, RatioCalculator.status(RatioDirection.MIN, null, min, warning), "no denominator");
    }

    @Test
    void netOutflowsCapInflowsAt75Percent() {
        assertEquals(0, new BigDecimal("40").compareTo(RatioCalculator.netOutflows(new BigDecimal("60"), new BigDecimal("100"))));
        assertEquals(0, new BigDecimal("25").compareTo(RatioCalculator.netOutflows(new BigDecimal("90"), new BigDecimal("100"))),
                "inflows count up to 75 of the 100 out");
    }

    @Test
    void holdingsBeyondTheCutOffAreOutAndPledgesComeOffPerNominal() {
        // a 1bn bill carried at 981,948,293.02 with 92 days left, cut-off 91: excluded whole
        RatioCalculator.HoldingValue bill = RatioCalculator.holding(new BigDecimal("981948293.02"), new BigDecimal("1000000000"),
                BigDecimal.ZERO, 92, 91, 2);
        assertEquals(new BigDecimal("981948293.02"), bill.excludedMaturity());
        bill = RatioCalculator.holding(new BigDecimal("981948293.02"), new BigDecimal("1000000000"), BigDecimal.ZERO, 91, 91, 2);
        assertEquals(BigDecimal.ZERO, bill.excludedMaturity());
        // a 100m bond carried at 99,743,895.03 with 40m of its face pledged: 40% of its value excluded
        final RatioCalculator.HoldingValue bond = RatioCalculator.holding(new BigDecimal("99743895.03"), new BigDecimal("100000000"),
                new BigDecimal("40000000"), 1600, null, 2);
        assertEquals(new BigDecimal("39897558.01"), bond.excludedPledged());
        assertEquals(new BigDecimal("99743895.03"),
                RatioCalculator.holding(new BigDecimal("99743895.03"), new BigDecimal("100000000"), new BigDecimal("150000000"), 1, null, 2)
                        .excludedPledged(),
                "never more than the holding");
    }

    @Test
    void theReturnLayoutAndItsCsvAreReproducible() {
        final ReturnBuilder.Ratio ratio = new ReturnBuilder.Ratio("LIQUID_ASSETS_TO_DEPOSITS", "Liquid assets to total deposits",
                "Liquid assets", "Deposit liabilities", new BigDecimal("202500.00"), new BigDecimal("1000000.00"),
                new BigDecimal("20.2500"), new BigDecimal("15"), "OK",
                List.of(new ReturnBuilder.Component("Cash in vaults", new BigDecimal("2500.00")),
                        new ReturnBuilder.Component("Bonds, after haircut", new BigDecimal("200000.00"))),
                List.of(new ReturnBuilder.Component("Savings", new BigDecimal("1000000.00"))));
        final List<ReturnBuilder.Line> lines = ReturnBuilder.lines(List.of(ratio));
        assertEquals(
                List.of("LIQUID_ASSETS_TO_DEPOSITS.A1", "LIQUID_ASSETS_TO_DEPOSITS.A2", "LIQUID_ASSETS_TO_DEPOSITS.A.TOTAL",
                        "LIQUID_ASSETS_TO_DEPOSITS.B1", "LIQUID_ASSETS_TO_DEPOSITS.B.TOTAL", "LIQUID_ASSETS_TO_DEPOSITS.C1",
                        "LIQUID_ASSETS_TO_DEPOSITS.C2", "LIQUID_ASSETS_TO_DEPOSITS.C3"),
                lines.stream().map(ReturnBuilder.Line::lineCode).toList());
        final List<String[]> header = List.<String[]>of(new String[] { "Run", "42" }, new String[] { "As of", "2026-10-16" });
        final String csv = ReturnBuilder.csv(header, lines, 2);
        assertEquals(csv, ReturnBuilder.csv(header, ReturnBuilder.lines(List.of(ratio)), 2));
        assertEquals("""
                "#","Run","42"\r
                "#","As of","2026-10-16"\r
                Section,Line,Label,Amount\r
                "A","LIQUID_ASSETS_TO_DEPOSITS.A1","Cash in vaults",2500.00\r
                "A","LIQUID_ASSETS_TO_DEPOSITS.A2","Bonds, after haircut",200000.00\r
                "A","LIQUID_ASSETS_TO_DEPOSITS.A.TOTAL","Total liquid assets",202500.00\r
                "B","LIQUID_ASSETS_TO_DEPOSITS.B1","Savings",1000000.00\r
                "B","LIQUID_ASSETS_TO_DEPOSITS.B.TOTAL","Total deposit liabilities",1000000.00\r
                "C","LIQUID_ASSETS_TO_DEPOSITS.C1","Liquid assets to total deposits (%)",20.25\r
                "C","LIQUID_ASSETS_TO_DEPOSITS.C2","Limit (%)",15.00\r
                "C","LIQUID_ASSETS_TO_DEPOSITS.C3","Status","OK"\r
                """, csv);
    }
}
