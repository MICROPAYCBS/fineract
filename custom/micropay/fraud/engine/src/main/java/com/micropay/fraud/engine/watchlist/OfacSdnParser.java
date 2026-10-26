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
 * The US Treasury OFAC Specially Designated Nationals list in its classic XML format ({@code SDN.XML}): one
 * {@code sdnEntry} per listing. Weak aliases are left out, as OFAC advises.
 */
public final class OfacSdnParser implements ListParser {

    @Override
    public List<WatchlistEntry> parse(String source, InputStream in) throws IOException {
        final List<WatchlistEntry> entries = new ArrayList<>();
        XmlRecords.read(in, Set.of("sdnEntry"), node -> {
            final String uid = node.text("uid");
            final String name = XmlNode.join(node.text("firstName"), node.text("lastName"));
            if (uid == null || name == null) {
                return;
            }
            final List<String> aliases = new ArrayList<>();
            for (XmlNode aka : node.descendants("aka")) {
                final String alias = XmlNode.join(aka.text("firstName"), aka.text("lastName"));
                if (alias != null && !"weak".equalsIgnoreCase(aka.text("category"))) {
                    aliases.add(alias);
                }
            }
            final List<String> birthDates = new ArrayList<>();
            for (XmlNode dob : node.descendants("dateOfBirth")) {
                birthDates.addAll(BirthDates.normalise(dob.trimmedText()));
            }
            final List<String> programs = node.descendants("program").stream().map(XmlNode::trimmedText).filter(Objects::nonNull).toList();
            entries.add(new WatchlistEntry(source, uid, partyType(node.text("sdnType")), name, aliases, birthDates, programs));
        });
        return entries;
    }

    private static PartyType partyType(String sdnType) {
        if ("Individual".equalsIgnoreCase(sdnType)) {
            return PartyType.PERSON;
        }
        return "Entity".equalsIgnoreCase(sdnType) ? PartyType.ENTITY : PartyType.OTHER;
    }
}
