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
package org.apache.fineract.treasury.setup;

import java.util.List;

/**
 * The kind of institution a tenant is: it picks the regulatory rule set template, the regulatory limits and the default
 * roles that {@code apply-template} sets up.
 */
public enum ClientType {

    /** A payment service provider issuing e-money (NPS Act 2020); its template, BOU_NPS_2021, comes with P5.3. */
    PSP("BOU_NPS_2021", List.of()),
    /** A bank or credit institution: FI (Liquidity) Regulations 2023. */
    BANK("BOU_FI_2023", List.of("BANK_LAR_MIN", "BANK_LCR_MIN", "BANK_NSFR_MIN", "FX_AGGREGATE_NOP_MAX")),
    /** A microfinance deposit-taking institution: MDI Regulations 2004. */
    MDI("BOU_MDI_2004", List.of("LIQUID_ASSETS_TO_DEPOSITS_MIN", "FX_AGGREGATE_NOP_MAX")),
    /** A SACCO or Tier 4 microfinance institution: Tier 4 MFI and Money Lenders Act 2016, UMRA. */
    SACCO_MFI("UMRA_SACCO", List.of("SACCO_LIQUIDITY_MIN", "SACCO_CORE_CAPITAL_MIN", "SACCO_NON_EARNING_ASSETS_MAX",
            "SACCO_EXTERNAL_BORROWING_MAX", "SACCO_NON_GOVT_SECURITIES_MAX"));

    private final String ruleSetCode;
    private final List<String> regulatoryLimits;

    ClientType(final String ruleSetCode, final List<String> regulatoryLimits) {
        this.ruleSetCode = ruleSetCode;
        this.regulatoryLimits = regulatoryLimits;
    }

    public String ruleSetCode() {
        return this.ruleSetCode;
    }

    /** The codes of the limits the template sets up or relies on; active exactly when the template rule set is. */
    public List<String> regulatoryLimits() {
        return this.regulatoryLimits;
    }
}
