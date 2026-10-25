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
package org.apache.fineract.treasury.data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** What treasury forms need from the platform, and the signed-in user's own commands awaiting a checker. */
public final class TreasuryContextData {

    private TreasuryContextData() {}

    /**
     * Defaults for treasury forms: the business date (for users who may not read it from the platform) and the base
     * currency.
     */
    public record Template(LocalDate businessDate, String baseCurrencyCode) {
    }

    /** A treasury command the signed-in user made that awaits a checker. */
    public record PendingCommand(Long id, String actionName, String entityName, Long resourceId, Long subresourceId, String href,
            LocalDateTime madeOnDate, String commandAsJson) {
    }
}
