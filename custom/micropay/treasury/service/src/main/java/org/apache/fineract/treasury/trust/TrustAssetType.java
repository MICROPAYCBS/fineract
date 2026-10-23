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
package org.apache.fineract.treasury.trust;

/**
 * What a trust asset is. NPS Act 2020 s.60(1)-(2): e-money is backed 100% by unencumbered liquid assets, that is cash
 * withdrawable on demand, treasury bills and treasury bonds; a term deposit is held in trust but is not liquid.
 */
public enum TrustAssetType {

    DEMAND_DEPOSIT(true), //
    TERM_DEPOSIT(false), //
    TREASURY_BILL(true), //
    TREASURY_BOND(true); //

    private final boolean liquid;

    TrustAssetType(final boolean liquid) {
        this.liquid = liquid;
    }

    public boolean liquid() {
        return this.liquid;
    }

    /** Bills and bonds are claims on the government, not on an institution holding trust money. */
    public boolean atAnInstitution() {
        return this == DEMAND_DEPOSIT || this == TERM_DEPOSIT;
    }
}
