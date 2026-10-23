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
package org.apache.fineract.treasury.stress;

import java.math.BigDecimal;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/**
 * A shock of a scenario. Lines are matched by ladder source and, when set, line code, product and bucket (all buckets
 * otherwise), and side.
 *
 * @param factor
 *            percent: the multiplier (MULTIPLY), the share of the balance (ADD_OUTFLOW_PCT_OF_BALANCE), the further
 *            haircut (HAIRCUT_LIQUID_ASSET)
 */
public record Shock(ShockType type, String label, LadderSourceCode source, String lineCode, Long productId, String bucketCode,
        ShockSide side, BigDecimal factor, String assetClass, FundingSourceType fundingSourceType, Long fundingLineId) {

    boolean matches(final LadderLine line, final BigDecimal signed, final boolean anyBucket) {
        return line.source() == this.source && (this.lineCode == null || this.lineCode.equals(line.lineCode()))
                && (this.productId == null || this.productId.equals(line.productId()))
                && (anyBucket || this.bucketCode == null || this.bucketCode.equals(line.bucketCode()))
                && (this.side == null ? ShockSide.BOTH : this.side).matches(signed);
    }
}
