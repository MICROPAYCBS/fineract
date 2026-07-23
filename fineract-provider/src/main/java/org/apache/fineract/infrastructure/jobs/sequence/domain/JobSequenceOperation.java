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
package org.apache.fineract.infrastructure.jobs.sequence.domain;

import java.util.Set;

public enum JobSequenceOperation {
    ADVANCE_BUSINESS_DATE;

    private static final Set<String> ALLOWED = Set.of(ADVANCE_BUSINESS_DATE.name());

    public static JobSequenceOperation fromString(final String value) {
        return JobSequenceOperation.valueOf(value.trim().toUpperCase());
    }

    public static boolean isAllowed(final String value) {
        return value != null && ALLOWED.contains(value.trim().toUpperCase());
    }
}
