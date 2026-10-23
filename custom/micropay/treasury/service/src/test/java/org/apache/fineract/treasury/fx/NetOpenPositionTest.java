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
package org.apache.fineract.treasury.fx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** The shorthand method: the greater of the sum of the longs and the sum of the shorts. */
class NetOpenPositionTest {

    @Test
    void theGreaterOfLongsAndShorts() {
        final NetOpenPosition.Aggregate a = NetOpenPosition.aggregate(
                Arrays.asList(new BigDecimal("100"), new BigDecimal("50"), new BigDecimal("-120"), null, BigDecimal.ZERO),
                new BigDecimal("1000"));
        assertEquals(0, new BigDecimal("150").compareTo(a.longs()));
        assertEquals(0, new BigDecimal("120").compareTo(a.shorts()));
        assertEquals(0, new BigDecimal("150").compareTo(a.nop()));
        assertEquals(new BigDecimal("15.0000"), a.percent());
        final NetOpenPosition.Aggregate shorts = NetOpenPosition.aggregate(Arrays.asList(new BigDecimal("10"), new BigDecimal("-300")),
                new BigDecimal("1000"));
        assertEquals(new BigDecimal("30.0000"), shorts.percent(), "the shorts are greater");
    }

    @Test
    void aSingleCurrencyLongOrShort() {
        assertEquals(new BigDecimal("12.0000"), NetOpenPosition.single(new BigDecimal("-120"), new BigDecimal("1000")));
        assertNull(NetOpenPosition.single(null, new BigDecimal("1000")), "no rate");
        assertNull(NetOpenPosition.single(BigDecimal.TEN, null), "no core capital");
        assertNull(NetOpenPosition.aggregate(Arrays.asList(BigDecimal.TEN), BigDecimal.ZERO).percent());
    }
}
