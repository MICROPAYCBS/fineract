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

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

/**
 * Streams a large list file and hands each record element (for example every {@code sdnEntry}) to a callback as a small
 * tree, so the whole document is never held in memory. DTDs and external entities are refused.
 */
public final class XmlRecords {

    private XmlRecords() {}

    public static void read(InputStream in, Set<String> recordNames, Consumer<XmlNode> callback) throws IOException {
        final XMLInputFactory factory = XMLInputFactory.newFactory();
        factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, true);
        XMLStreamReader reader = null;
        try {
            reader = factory.createXMLStreamReader(in);
            final Deque<Builder> stack = new ArrayDeque<>();
            while (reader.hasNext()) {
                final int event = reader.next();
                if (event == XMLStreamConstants.START_ELEMENT) {
                    final String name = reader.getLocalName();
                    if (!stack.isEmpty() || recordNames.contains(name)) {
                        final Map<String, String> attributes = new LinkedHashMap<>();
                        for (int i = 0; i < reader.getAttributeCount(); i++) {
                            attributes.put(reader.getAttributeLocalName(i), reader.getAttributeValue(i));
                        }
                        stack.push(new Builder(name, attributes));
                    }
                } else if ((event == XMLStreamConstants.CHARACTERS || event == XMLStreamConstants.CDATA) && !stack.isEmpty()) {
                    stack.peek().text.append(reader.getText());
                } else if (event == XMLStreamConstants.END_ELEMENT && !stack.isEmpty()) {
                    final XmlNode node = stack.pop().build();
                    if (stack.isEmpty()) {
                        callback.accept(node);
                    } else {
                        stack.peek().children.add(node);
                    }
                }
            }
        } catch (XMLStreamException e) {
            throw new IOException("Malformed list file: " + e.getMessage(), e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (XMLStreamException e) {
                    // Closing a reader of an already failed stream; nothing left to release
                }
            }
        }
    }

    private static final class Builder {

        private final String name;
        private final Map<String, String> attributes;
        private final StringBuilder text = new StringBuilder();
        private final List<XmlNode> children = new ArrayList<>();

        Builder(String name, Map<String, String> attributes) {
            this.name = name;
            this.attributes = attributes;
        }

        XmlNode build() {
            return new XmlNode(name, Map.copyOf(attributes), text.toString(), List.copyOf(children));
        }
    }
}
