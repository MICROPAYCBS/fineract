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
package org.apache.fineract.treasury.limits;

/**
 * What a limit covers. INSTITUTION and CURRENCY read the institution's runs and data (CURRENCY names its currency);
 * OFFICE an office's runs and data; COUNTERPARTY and ACCOUNT one counterparty or treasury account.
 */
public enum LimitScopeType {

    INSTITUTION, //
    CURRENCY, //
    OFFICE, //
    COUNTERPARTY, //
    ACCOUNT; //

    public boolean needsScopeId() {
        return this == OFFICE || this == COUNTERPARTY || this == ACCOUNT;
    }
}
