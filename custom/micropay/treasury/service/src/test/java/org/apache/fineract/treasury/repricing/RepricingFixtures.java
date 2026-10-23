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
package org.apache.fineract.treasury.repricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.Bucket;
import org.apache.fineract.treasury.liquidity.LadderContext;

/** The MDI placeholder buckets and contexts for the repricing tests. */
final class RepricingFixtures {

    static final LocalDate AS_OF = LocalDate.of(2026, 10, 16);
    static final List<Bucket> BUCKETS = List.of(new Bucket(1, "ON", "Overnight", 0, 1), new Bucket(2, "D2_8", "2 to 8 days", 2, 8),
            new Bucket(3, "D9_1M", "9 days to 1 month", 9, 30), new Bucket(4, "M1_3", "1 to 3 months", 31, 90),
            new Bucket(5, "M3_6", "3 to 6 months", 91, 180), new Bucket(6, "M6_12", "6 to 12 months", 181, 365),
            new Bucket(7, "Y1_PLUS", "Over 1 year", 366, null));

    private RepricingFixtures() {}

    static LadderContext context(final List<Assumptions.Row> rows) {
        return new LadderContext(AS_OF, "UGX", null, BUCKETS, new Assumptions(rows), 2);
    }

    static BigDecimal d(final String value) {
        return new BigDecimal(value);
    }
}
