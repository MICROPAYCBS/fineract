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
package org.apache.fineract.treasury.stress;

/** Where contingency funding comes from. */
public enum FundingSourceType {

    COMMITTED_LINE_RECEIVED, //
    UNCOMMITTED_LINE, //
    REPO_BOU, //
    ASSET_SALE, //
    SHAREHOLDER, //
    DEPOSIT_DRIVE, //
    OTHER; //

    /**
     * Whether a line of the type counts as counterbalancing capacity in stress unless set otherwise: only committed
     * lines received do; the others are in the plan's total but not relied on.
     */
    public boolean countsAsCapacityByDefault() {
        return this == COMMITTED_LINE_RECEIVED;
    }
}
