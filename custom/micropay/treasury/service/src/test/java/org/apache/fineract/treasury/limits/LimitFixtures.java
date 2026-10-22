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
package org.apache.fineract.treasury.limits;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryRun;
import org.apache.fineract.treasury.liquidity.RunType;

/** Limits and contexts for the limit tests. */
public final class LimitFixtures {

    public static final LocalDate AS_OF = LocalDate.of(2026, 10, 16);

    private LimitFixtures() {}

    public static TreasuryLimit limit(final long id, final LimitMetric metric, final LimitComparison comparison, final String warning,
            final String hard, final LimitUnit unit) {
        final TreasuryLimit limit = new TreasuryLimit();
        limit.setId(id);
        limit.setCode("L" + id);
        limit.setName("Limit " + id);
        limit.setMetric(metric);
        limit.setScopeType(LimitScopeType.INSTITUTION);
        limit.setComparison(comparison);
        limit.setWarningLevel(warning == null ? null : new BigDecimal(warning));
        limit.setHardLevel(hard == null ? null : new BigDecimal(hard));
        limit.setUnit(unit);
        limit.setStatus(LimitStatus.ACTIVE);
        limit.setLimitVersion(1);
        return limit;
    }

    public static TreasuryLimit own(final long id, final LimitMetric metric) {
        final TreasuryLimit limit = limit(id, metric, null, null, null, metric.ownUnit());
        limit.setUseOwnLevels(true);
        return limit;
    }

    public static TreasuryRun run(final long id, final RunType type, final Long officeId) {
        final TreasuryRun run = new TreasuryRun();
        run.setId(id);
        run.setRunType(type);
        run.setAsOfDate(AS_OF);
        run.setCurrencyCode("UGX");
        run.setOfficeId(officeId);
        return run;
    }

    public static EvaluationContext context(final TreasuryRun ladder, final TreasuryRun ratios) {
        return new EvaluationContext(AS_OF, "UGX", null, ladder, ratios, 2);
    }
}
