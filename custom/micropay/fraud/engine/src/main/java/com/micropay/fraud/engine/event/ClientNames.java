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

import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Builds a client's screening name and party type the same way from Kafka events and from Fineract's client API.
 */
public final class ClientNames {

    /** Fineract's legal form ids (LegalForm enum). */
    static final long LEGAL_FORM_PERSON = 1L;
    static final long LEGAL_FORM_ENTITY = 2L;

    private ClientNames() {}

    public static String fullName(String firstname, String middlename, String lastname, String fullname, String displayName) {
        final String person = Stream.of(firstname, middlename, lastname).filter(Objects::nonNull).map(String::trim)
                .filter(s -> !s.isEmpty()).collect(Collectors.joining(" "));
        if (!person.isEmpty()) {
            return person;
        }
        if (fullname != null && !fullname.isBlank()) {
            return fullname.trim();
        }
        return displayName == null || displayName.isBlank() ? null : displayName.trim();
    }

    public static PartyType partyType(Long legalFormId) {
        if (legalFormId == null) {
            return PartyType.UNKNOWN;
        }
        if (legalFormId == LEGAL_FORM_PERSON) {
            return PartyType.PERSON;
        }
        return legalFormId == LEGAL_FORM_ENTITY ? PartyType.ENTITY : PartyType.UNKNOWN;
    }
}
