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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * One XML element of a list record, read by {@link XmlRecords}. Names are local names, so the parsers do not depend on
 * the namespace a publisher uses this year.
 */
public record XmlNode(String name, Map<String, String> attributes, String text, List<XmlNode> children) {

    public XmlNode child(String childName) {
        return children.stream().filter(c -> c.name.equals(childName)).findFirst().orElse(null);
    }

    public List<XmlNode> children(String childName) {
        return children.stream().filter(c -> c.name.equals(childName)).toList();
    }

    /**
     * All descendants with this name, at any depth.
     */
    public List<XmlNode> descendants(String descendantName) {
        final List<XmlNode> found = new ArrayList<>();
        for (XmlNode c : children) {
            if (c.name.equals(descendantName)) {
                found.add(c);
            }
            found.addAll(c.descendants(descendantName));
        }
        return found;
    }

    /**
     * The trimmed text of the first child with this name, or null when it is missing or empty.
     */
    public String text(String childName) {
        final XmlNode c = child(childName);
        return c == null ? null : c.trimmedText();
    }

    public String trimmedText() {
        return text == null || text.isBlank() ? null : text.trim();
    }

    public String attr(String attributeName) {
        final String value = attributes.get(attributeName);
        return value == null || value.isBlank() ? null : value.trim();
    }

    static String join(String... parts) {
        final StringBuilder sb = new StringBuilder();
        for (String part : parts) {
            if (Objects.nonNull(part) && !part.isBlank()) {
                if (!sb.isEmpty()) {
                    sb.append(' ');
                }
                sb.append(part.trim());
            }
        }
        return sb.isEmpty() ? null : sb.toString();
    }
}
