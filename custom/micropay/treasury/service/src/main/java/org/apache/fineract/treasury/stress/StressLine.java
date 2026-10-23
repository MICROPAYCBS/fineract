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
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/** A ladder line before and after the shocks, signed, with the labels of the shocks that changed it. */
@Getter
public final class StressLine {

    private final int bucketSeq;
    private final String bucketCode;
    private final LadderSourceCode source;
    private final String lineCode;
    private final Long productId;
    private final String productName;
    private final BigDecimal baseline;
    private BigDecimal stressed;
    private final List<String> shocks = new ArrayList<>();

    public StressLine(final int bucketSeq, final String bucketCode, final LadderSourceCode source, final String lineCode,
            final Long productId, final String productName, final BigDecimal baseline) {
        this.bucketSeq = bucketSeq;
        this.bucketCode = bucketCode;
        this.source = source;
        this.lineCode = lineCode;
        this.productId = productId;
        this.productName = productName;
        this.baseline = baseline;
        this.stressed = baseline;
    }

    void change(final BigDecimal value, final String label) {
        if (value.compareTo(this.stressed) != 0 || this.baseline.signum() == 0) {
            this.stressed = value;
            if (!this.shocks.contains(label)) {
                this.shocks.add(label);
            }
        }
    }

    public boolean isShocked() {
        return !this.shocks.isEmpty();
    }

    public String shockLabels() {
        return String.join("; ", this.shocks);
    }
}
