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
import java.util.ArrayList;
import java.util.List;
import org.apache.fineract.treasury.liquidity.AssumptionType;
import org.apache.fineract.treasury.liquidity.Assumptions;
import org.apache.fineract.treasury.liquidity.LadderContext;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/**
 * A balance spread over buckets by the {@code REPRICING_DIST} rows of its source and product (or the general ones):
 * each row's rate of the balance reprices in its bucket; what the rows leave goes to the {@code rest} rate type, in the
 * first bucket unless it is NON_SENSITIVE.
 */
public final class RepricingDistribution {

    private RepricingDistribution() {}

    public static List<RepricingPosition> spread(final LadderContext context, final LadderSourceCode source, final String lineCode,
            final Long productId, final String productName, final RepricingSide side, final RateType rateType, final RateType rest,
            final BigDecimal balance) {
        final List<RepricingPosition> positions = new ArrayList<>();
        BigDecimal left = balance;
        for (final Assumptions.Row row : context.assumptions().rows(AssumptionType.REPRICING_DIST, source, productId)) {
            if (context.buckets().stream().noneMatch(b -> b.code().equals(row.bucketCode()))) {
                context.warnings().add("REPRICING_DIST bucket " + row.bucketCode() + " is not a bucket of the rule set");
                continue;
            }
            final BigDecimal share = context.percent(balance, row.rate());
            positions.add(new RepricingPosition(source, lineCode, productId, productName, side, rateType, null, row.bucketCode(), share));
            left = left.subtract(share);
        }
        if (left.signum() != 0) {
            positions.add(new RepricingPosition(source, lineCode, productId, productName, side, rest, null,
                    rest.isSensitive() ? context.first().code() : null, left));
        }
        return positions;
    }
}
