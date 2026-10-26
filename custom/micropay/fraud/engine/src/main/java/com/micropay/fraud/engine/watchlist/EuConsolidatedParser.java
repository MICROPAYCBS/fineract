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
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * The EU consolidated financial sanctions list (XML 1.1): one {@code sanctionEntity} per listing, with names, birth
 * dates and regulations held in attributes. The first name alias is the primary name; aliases marked
 * {@code strong="false"} are left out.
 */
public final class EuConsolidatedParser implements ListParser {

    @Override
    public List<WatchlistEntry> parse(String source, InputStream in) throws IOException {
        final List<WatchlistEntry> entries = new ArrayList<>();
        XmlRecords.read(in, Set.of("sanctionEntity"), node -> {
            final String reference = node.attr("euReferenceNumber");
            final String uid = reference != null ? reference : node.attr("logicalId");
            final List<String> names = new ArrayList<>();
            for (XmlNode alias : node.children("nameAlias")) {
                String aliasName = alias.attr("wholeName");
                if (aliasName == null) {
                    aliasName = XmlNode.join(alias.attr("firstName"), alias.attr("middleName"), alias.attr("lastName"));
                }
                if (aliasName != null && (names.isEmpty() || !"false".equalsIgnoreCase(alias.attr("strong")))) {
                    names.add(aliasName);
                }
            }
            if (uid == null || names.isEmpty()) {
                return;
            }
            final List<String> birthDates = new ArrayList<>();
            for (XmlNode dob : node.children("birthdate")) {
                final List<String> full = BirthDates.normalise(dob.attr("birthdate"));
                birthDates.addAll(full.isEmpty() ? BirthDates.normalise(dob.attr("year")) : full);
            }
            final List<String> programs = node.children("regulation").stream().map(r -> r.attr("programme")).filter(Objects::nonNull)
                    .distinct().toList();
            entries.add(new WatchlistEntry(source, uid, partyType(node.child("subjectType")), names.get(0), names.subList(1, names.size()),
                    birthDates.stream().distinct().toList(), programs));
        });
        return entries;
    }

    private static PartyType partyType(XmlNode subjectType) {
        final String code = subjectType == null ? null : subjectType.attr("code");
        if ("person".equalsIgnoreCase(code)) {
            return PartyType.PERSON;
        }
        return "enterprise".equalsIgnoreCase(code) ? PartyType.ENTITY : PartyType.OTHER;
    }
}
