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
package com.micropay.fraud.engine.watchlist;

import com.micropay.fraud.engine.event.PartyType;
import java.util.ArrayList;
import java.util.List;

/**
 * One listed person, organisation, vessel or aircraft.
 *
 * @param uid
 *            the entry's id within its list, so an alert can point back to it
 * @param aliases
 *            other names the entry is known by, without weak aliases
 * @param birthDates
 *            as {@code yyyy-MM-dd} when the list gives the full date, otherwise {@code yyyy}
 * @param programs
 *            the sanctions programmes or list types the entry is listed under
 */
public record WatchlistEntry(String source, String uid, PartyType partyType, String primaryName, List<String> aliases,
        List<String> birthDates, List<String> programs) {

    public WatchlistEntry {
        aliases = List.copyOf(aliases);
        birthDates = List.copyOf(birthDates);
        programs = List.copyOf(programs);
    }

    public List<String> names() {
        final List<String> names = new ArrayList<>(aliases.size() + 1);
        names.add(primaryName);
        names.addAll(aliases);
        return names;
    }
}
