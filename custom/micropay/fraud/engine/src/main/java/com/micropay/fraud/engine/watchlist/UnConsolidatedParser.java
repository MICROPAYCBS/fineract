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
 * The UN Security Council Consolidated List ({@code consolidated.xml}): {@code INDIVIDUAL} and {@code ENTITY} records.
 * Aliases the UN rates as low quality are left out.
 */
public final class UnConsolidatedParser implements ListParser {

    @Override
    public List<WatchlistEntry> parse(String source, InputStream in) throws IOException {
        final List<WatchlistEntry> entries = new ArrayList<>();
        XmlRecords.read(in, Set.of("INDIVIDUAL", "ENTITY"), node -> {
            final boolean person = "INDIVIDUAL".equals(node.name());
            final String reference = node.text("REFERENCE_NUMBER");
            final String uid = reference != null ? reference : node.text("DATAID");
            final String name = XmlNode.join(node.text("FIRST_NAME"), node.text("SECOND_NAME"), node.text("THIRD_NAME"),
                    node.text("FOURTH_NAME"));
            if (uid == null || name == null) {
                return;
            }
            final List<String> aliases = new ArrayList<>();
            for (XmlNode alias : node.children(person ? "INDIVIDUAL_ALIAS" : "ENTITY_ALIAS")) {
                final String aliasName = alias.text("ALIAS_NAME");
                if (aliasName != null && !"Low".equalsIgnoreCase(alias.text("QUALITY"))) {
                    aliases.add(aliasName);
                }
            }
            final List<String> birthDates = new ArrayList<>();
            for (XmlNode dob : node.children("INDIVIDUAL_DATE_OF_BIRTH")) {
                for (String field : List.of("DATE", "YEAR", "FROM_YEAR", "TO_YEAR")) {
                    birthDates.addAll(BirthDates.normalise(dob.text(field)));
                }
            }
            final List<String> programs = node.children("UN_LIST_TYPE").stream().map(XmlNode::trimmedText).filter(Objects::nonNull)
                    .toList();
            entries.add(new WatchlistEntry(source, uid, person ? PartyType.PERSON : PartyType.ENTITY, name, aliases,
                    birthDates.stream().distinct().toList(), programs));
        });
        return entries;
    }
}
