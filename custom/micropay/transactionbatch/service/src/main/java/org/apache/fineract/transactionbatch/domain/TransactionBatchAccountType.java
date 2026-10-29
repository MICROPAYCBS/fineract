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
public enum TransactionBatchAccountType {

    GL_ACCOUNT(1, "GL"), //
    SAVINGS_ACCOUNT(2, "CUSTOMER");

    private final Integer value;
    private final String code;

    public boolean isGl() {
        return this == GL_ACCOUNT;
    }

    public boolean isSavings() {
        return this == SAVINGS_ACCOUNT;
    }

    public static TransactionBatchAccountType fromInt(final Integer value) {
        return Arrays.stream(values()).filter(type -> type.value.equals(value)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Unknown account type " + value));
    }

    public static TransactionBatchAccountType fromCode(final String code) {
        if (code == null) {
            throw new IllegalArgumentException("Account type is required");
        }
        final String normalised = code.trim();
        if ("GL".equalsIgnoreCase(normalised) || "GL_ACCOUNT".equalsIgnoreCase(normalised) || "GL ACCOUNT".equalsIgnoreCase(normalised)) {
            return GL_ACCOUNT;
        }
        if ("CUSTOMER".equalsIgnoreCase(normalised) || "SAVINGS".equalsIgnoreCase(normalised)
                || "SAVINGS_ACCOUNT".equalsIgnoreCase(normalised) || "CUSTOMER ACCOUNT".equalsIgnoreCase(normalised)) {
            return SAVINGS_ACCOUNT;
        }
        throw new IllegalArgumentException("Unknown account type " + code);
    }
}
