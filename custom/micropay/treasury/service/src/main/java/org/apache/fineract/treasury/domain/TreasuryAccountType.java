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
package org.apache.fineract.treasury.domain;

/**
 * Where the cash of a treasury account sits.
 */
public enum TreasuryAccountType {

    BANK, //
    CENTRAL_BANK, //
    MOBILE_WALLET, //
    VAULT, //
    AGENT_FLOAT_POOL, //
    /** Money held in trust for e-money holders (NPS Act 2020 s.47, s.60); not the institution's own liquidity. */
    TRUST; //
}
