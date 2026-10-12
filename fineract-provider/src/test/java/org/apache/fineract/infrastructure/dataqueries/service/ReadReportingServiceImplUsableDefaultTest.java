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
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDate;
import java.util.HashMap;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReadReportingServiceImplUsableDefaultTest {

    @AfterEach
    void resetBusinessDate() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void usableDefaultResolvesTodayAndRejectsPlaceholders() {
        final HashMap<BusinessDateType, LocalDate> dates = new HashMap<>();
        dates.put(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 11));
        ThreadLocalContextUtil.setBusinessDates(dates);

        assertEquals("2026-10-11", ReadReportingServiceImpl.usableDefault("today", "date"));
        assertEquals("2026-10-11", ReadReportingServiceImpl.usableDefault(" TODAY ", "DATE"));
        assertNull(ReadReportingServiceImpl.usableDefault("n/a", "number"));
        assertNull(ReadReportingServiceImpl.usableDefault("", "number"));
        assertNull(ReadReportingServiceImpl.usableDefault("   ", "date"));
        assertNull(ReadReportingServiceImpl.usableDefault(null, "date"));
        assertEquals("-1", ReadReportingServiceImpl.usableDefault("-1", "number"));
        assertEquals("today", ReadReportingServiceImpl.usableDefault("today", "string"));
    }
}
