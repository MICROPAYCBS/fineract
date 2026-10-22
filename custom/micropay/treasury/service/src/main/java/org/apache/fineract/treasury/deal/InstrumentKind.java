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
package org.apache.fineract.treasury.deal;

/**
 * What a deal type trades; it fixes the direction and the interest method.
 */
public enum InstrumentKind {

    MONEY_MARKET_PLACEMENT(DealDirection.ASSET, InterestMethod.SIMPLE), //
    FIXED_DEPOSIT_PLACED(DealDirection.ASSET, InterestMethod.SIMPLE), //
    TREASURY_BILL(DealDirection.ASSET, InterestMethod.DISCOUNT_YIELD), //
    GOVERNMENT_BOND(DealDirection.ASSET, InterestMethod.COUPON), //
    BORROWING(DealDirection.LIABILITY, InterestMethod.SIMPLE), //
    CREDIT_LINE_DRAWDOWN(DealDirection.LIABILITY, InterestMethod.SIMPLE); //

    private final DealDirection direction;
    private final InterestMethod interestMethod;

    InstrumentKind(final DealDirection direction, final InterestMethod interestMethod) {
        this.direction = direction;
        this.interestMethod = interestMethod;
    }

    public DealDirection direction() {
        return this.direction;
    }

    public InterestMethod interestMethod() {
        return this.interestMethod;
    }

    public boolean isAsset() {
        return this.direction == DealDirection.ASSET;
    }

    /** Only borrowings and drawdowns may repay principal in instalments. */
    public boolean mayAmortise() {
        return this.direction == DealDirection.LIABILITY;
    }
}
