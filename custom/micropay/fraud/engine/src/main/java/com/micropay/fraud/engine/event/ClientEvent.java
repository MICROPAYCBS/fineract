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
package com.micropay.fraud.engine.event;

import java.time.LocalDate;

/**
 * A client being created or activated. Kept so rules can tell how new a client is, and screened against the watchlists.
 *
 * @param fullName
 *            the name to screen: first, middle and last name for a person, the full name for an entity
 */
public record ClientEvent(String tenantId, String eventKey, String eventType, Long clientId, LocalDate activationDate, Long officeId,
        String displayName, String fullName, LocalDate dateOfBirth, PartyType partyType) implements FineractEvent {
}
