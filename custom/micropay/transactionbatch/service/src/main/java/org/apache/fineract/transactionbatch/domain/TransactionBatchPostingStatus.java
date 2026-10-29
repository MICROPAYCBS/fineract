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

@Getter
@RequiredArgsConstructor
public enum TransactionBatchPostingStatus {

    POSTED(100, "POSTED"), //
    SUSPENDED(200, "SUSPENDED"), //
    REPAIRED(300, "REPAIRED");

    private final Integer value;
    private final String code;

    public boolean isSuspended() {
        return this == SUSPENDED;
    }

    public static TransactionBatchPostingStatus fromInt(final Integer value) {
        return Arrays.stream(values()).filter(status -> status.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown posting status " + value));
    }
}
