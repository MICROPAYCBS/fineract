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

import java.time.LocalDate;

/**
 * Which core capital the net open position is measured against. The Foreign Exchange Business Rules 2010, r.6(1): the
 * daily open position lies within 25% of core capital "as at the preceding quarter".
 */
public enum NopCapitalBasis {

    /** The core capital in effect at the end of the calendar quarter before the as-of date (the default). */
    PRECEDING_QUARTER,
    /** The latest core capital in effect on the as-of date. */
    LATEST;

    /** The date whose core capital in effect applies to the date. */
    public LocalDate capitalDate(final LocalDate asOf) {
        return this == LATEST ? asOf : precedingQuarterEnd(asOf);
    }

    /**
     * The last day of the calendar quarter before the one the date falls in: 2026-10-16 and 2026-12-31 give 2026-09-30.
     */
    public static LocalDate precedingQuarterEnd(final LocalDate date) {
        final int firstMonth = (date.getMonthValue() - 1) / 3 * 3 + 1;
        return date.withDayOfMonth(1).withMonth(firstMonth).minusDays(1);
    }
}
