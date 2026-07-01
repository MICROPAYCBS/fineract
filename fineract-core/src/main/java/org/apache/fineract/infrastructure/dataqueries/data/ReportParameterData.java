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

import org.apache.commons.lang3.StringUtils;

/* used to show list of parameters used by a report and also for getting a list of parameters available (the reportParameterName is left null */
public final class ReportParameterData {

    @SuppressWarnings("unused")
    private final Long id;
    @SuppressWarnings("unused")
    private final Long parameterId;
    @SuppressWarnings("unused")
    private final String parameterName;
    @SuppressWarnings("unused")
    private final String reportParameterName;
    @SuppressWarnings("unused")
    private final String parameterLabel;
    @SuppressWarnings("unused")
    private final String displayLabel;

    public ReportParameterData(final Long id, final Long parameterId, final String reportParameterName, final String parameterName,
            final String parameterLabel) {
        this.id = id;
        this.parameterId = parameterId;
        this.parameterName = parameterName;
        this.reportParameterName = reportParameterName;
        this.parameterLabel = parameterLabel;
        this.displayLabel = resolveDisplayLabel(reportParameterName, parameterLabel, parameterName);
    }

    private static String resolveDisplayLabel(final String reportParameterName, final String parameterLabel, final String parameterName) {
        if (StringUtils.isNotBlank(parameterLabel) && !"n/a".equalsIgnoreCase(parameterLabel.trim())) {
            return parameterLabel.trim();
        }
        return formatParameterName(parameterName);
    }

    private static String formatParameterName(final String parameterName) {
        if (StringUtils.isBlank(parameterName)) {
            return "";
        }
        String formatted = parameterName.replaceAll("Select(One|All)$", "").replaceAll("Id$", "");
        formatted = formatted.replaceAll("([a-z])([A-Z])", "$1 $2");
        formatted = formatted.replace('_', ' ').trim();
        if (formatted.isEmpty()) {
            return parameterName;
        }
        return StringUtils.capitalize(formatted);
    }
}
