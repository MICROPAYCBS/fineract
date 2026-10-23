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
package org.apache.fineract.treasury.setup;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * The client-type templates, JSON resources in {@code org/apache/fineract/treasury/templates}: per rule set code the
 * rule set in the shape of {@code POST /treasury/rule-sets}, and per client type its regulatory limits in the shape of
 * {@code POST /treasury/limits}.
 */
public final class TreasuryTemplates {

    private static final String ROOT = "org/apache/fineract/treasury/templates/";

    private TreasuryTemplates() {}

    /** The rule set template of the client type, empty when it has none yet. */
    public static Optional<String> ruleSet(final ClientType type) {
        return read(type.ruleSetCode() + ".json");
    }

    /** The limit templates of the client type, each a JSON object. */
    public static List<String> limits(final ClientType type) {
        final List<String> limits = new ArrayList<>();
        read("limits-" + type.name() + ".json").ifPresent(json -> {
            final JsonArray array = JsonParser.parseString(json).getAsJsonArray();
            for (final JsonElement element : array) {
                limits.add(element.toString());
            }
        });
        return limits;
    }

    private static Optional<String> read(final String name) {
        try (InputStream in = TreasuryTemplates.class.getClassLoader().getResourceAsStream(ROOT + name)) {
            return in == null ? Optional.empty() : Optional.of(new String(in.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read the treasury template " + name, e);
        }
    }
}
