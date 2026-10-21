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
package org.apache.fineract.organisation.teller.domain;

public enum CashLegalTenderSourceType {

    CASHIER_TXN(1), SAVINGS_TXN(2);

    private final int id;

    CashLegalTenderSourceType(final int id) {
        this.id = id;
    }

    public int getId() {
        return this.id;
    }

    public static CashLegalTenderSourceType fromInt(final int id) {
        for (CashLegalTenderSourceType type : values()) {
            if (type.id == id) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown cash legal tender source type id: " + id);
    }
}
