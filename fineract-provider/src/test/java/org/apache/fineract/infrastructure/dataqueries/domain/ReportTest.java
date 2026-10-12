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
package org.apache.fineract.infrastructure.dataqueries.domain;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.junit.jupiter.api.Test;

class ReportTest {

    private static final List<String> TYPES = List.of("Table", "Chart", "SMS");

    @Test
    void smsReportWithSqlAndNonTriggeredSubTypeIsValid() {
        assertDoesNotThrow(() -> report("SMS", "NonTriggered", "SELECT 1"));
    }

    @Test
    void smsReportRequiresASubType() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> report("SMS", null, "SELECT 1"));
        assertTrue(ex.getErrors().stream().anyMatch(error -> "reportSubType".equals(error.getParameterName())));
    }

    @Test
    void smsReportRejectsChartSubType() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> report("SMS", "Bar", "SELECT 1"));
        assertTrue(ex.getErrors().stream().anyMatch(error -> "reportSubType".equals(error.getParameterName())));
    }

    @Test
    void smsReportRequiresSql() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> report("SMS", "Triggered", null));
        assertTrue(ex.getErrors().stream().anyMatch(error -> "reportSql".equals(error.getParameterName())));
    }

    @Test
    void tableReportStillRejectsASubType() {
        final PlatformApiDataValidationException ex = assertThrows(PlatformApiDataValidationException.class,
                () -> report("Table", "Triggered", "SELECT 1"));
        assertTrue(ex.getErrors().stream().anyMatch(error -> "reportSubType".equals(error.getParameterName())));
    }

    private static Report report(final String type, final String subType, final String sql) {
        return new Report("Clients due", type, subType, "Client", null, true, sql, TYPES);
    }
}
