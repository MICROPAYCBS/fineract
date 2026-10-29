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
package org.apache.fineract.transactionbatch.domain;

import java.util.Arrays;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** Stored and derived batch statuses. SUBMITTED is never stored. */
@Getter
@RequiredArgsConstructor
public enum TransactionBatchStatus {

    INACTIVE(0, "INACTIVE"), //
    ACTIVE(100, "ACTIVE"), //
    REJECTED(200, "REJECTED"), //
    POSTED(300, "POSTED"), //
    PARTIALLY_POSTED(400, "PARTIALLY_POSTED"), //
    SUBMITTED(-1, "SUBMITTED");

    private final Integer value;
    private final String code;

    public boolean isInactive() {
        return this == INACTIVE;
    }

    public boolean isActive() {
        return this == ACTIVE;
    }

    public boolean isTerminalSuccess() {
        return this == POSTED || this == PARTIALLY_POSTED;
    }

    public static TransactionBatchStatus fromInt(final Integer value) {
        return Arrays.stream(values()).filter(status -> status.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown batch status " + value));
    }

    public static TransactionBatchStatus fromCode(final String code) {
        return Arrays.stream(values()).filter(status -> status.code.equalsIgnoreCase(code)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown batch status " + code));
    }
}
