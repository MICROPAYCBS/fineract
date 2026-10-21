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
package org.apache.fineract.infrastructure.dataqueries.data;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

class ReportParameterDataTest {

    @Test
    void displayLabelUsesParameterLabelNotReportSqlName() throws Exception {
        ReportParameterData data = new ReportParameterData(1L, 5L, "branch", "OfficeIdSelectOne", "Branch");
        assertEquals("Branch", readDisplayLabel(data));
    }

    @Test
    void displayLabelUsesAsOnDateLabelNotReportSqlName() throws Exception {
        ReportParameterData data = new ReportParameterData(1L, 1009L, "date", "asOnDate", "As On Date");
        assertEquals("As On Date", readDisplayLabel(data));
    }

    @Test
    void displayLabelUsesParameterLabelWhenReportOverrideMissing() throws Exception {
        ReportParameterData data = new ReportParameterData(1L, 2L, null, "endDateSelect", "End Date");
        assertEquals("End Date", readDisplayLabel(data));
    }

    @Test
    void displayLabelUsesBranchLabelFromDatabase() throws Exception {
        ReportParameterData data = new ReportParameterData(1L, 2L, null, "OfficeIdSelectOne", "Branch");
        assertEquals("Branch", readDisplayLabel(data));
    }

    @Test
    void displayLabelFormatsParameterNameWhenLabelMissing() throws Exception {
        ReportParameterData data = new ReportParameterData(1L, 2L, null, "OfficeIdSelectOne", null);
        assertEquals("Office", readDisplayLabel(data));
    }

    @Test
    void displayLabelIgnoresNaParameterLabel() throws Exception {
        ReportParameterData data = new ReportParameterData(1L, 2L, null, "endDateSelect", "n/a");
        assertEquals("End date", readDisplayLabel(data));
    }

    private static String readDisplayLabel(final ReportParameterData data) throws Exception {
        final Field field = ReportParameterData.class.getDeclaredField("displayLabel");
        field.setAccessible(true);
        return (String) field.get(data);
    }
}
