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
package org.apache.fineract.portfolio.savings.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.junit.jupiter.api.Test;

/**
 * Verifies savings product validity date ordering matches loan product rules: closeDate must not be before startDate.
 */
class SavingsProductDateExceptionTest {

    @Test
    void closeDateBeforeStartDate_isRejected() {
        final LocalDate startDate = LocalDate.of(2024, 12, 31);
        final LocalDate closeDate = LocalDate.of(2024, 1, 1);

        final SavingsProductDateException exception = assertThrows(SavingsProductDateException.class,
                () -> validateInputDates(startDate, closeDate));
        assertEquals("error.msg.savings.product.close.date.cannot.be.before.start.date", exception.getGlobalisationMessageCode());
    }

    @Test
    void closeDateOnOrAfterStartDate_isAllowed() {
        validateInputDates(LocalDate.of(2024, 1, 1), LocalDate.of(2024, 12, 31));
        validateInputDates(LocalDate.of(2024, 6, 15), LocalDate.of(2024, 6, 15));
    }

    @Test
    void nullDates_areAllowed() {
        validateInputDates(null, null);
        validateInputDates(LocalDate.of(2024, 1, 1), null);
        validateInputDates(null, LocalDate.of(2024, 12, 31));
    }

    private void validateInputDates(final LocalDate startDate, final LocalDate closeDate) {
        if (closeDate != null && DateUtils.isBefore(closeDate, startDate)) {
            throw new SavingsProductDateException(startDate == null ? null : startDate.toString(), closeDate.toString());
        }
    }
}
