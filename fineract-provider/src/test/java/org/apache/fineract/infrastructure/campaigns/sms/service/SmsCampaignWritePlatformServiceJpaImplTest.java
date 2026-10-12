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
package org.apache.fineract.infrastructure.campaigns.sms.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SmsCampaignWritePlatformServiceJpaImplTest {

    @Test
    void toCaseInsensitiveRowsFindsCamelCaseKeys() {
        final Map<String, Object> row = new HashMap<>();
        row.put("mobileno", "+256700000001");
        row.put("firstname", "Jane");
        row.put("id", 5);

        final Map<String, Object> converted = SmsCampaignWritePlatformServiceJpaImpl.toCaseInsensitiveRows(List.of(row)).get(0);

        assertEquals("+256700000001", converted.get("mobileNo"));
        assertEquals(5, converted.get("ID"));
    }

    @Test
    void templateUsesCaseInsensitiveRow() {
        final Map<String, Object> row = new HashMap<>();
        row.put("mobileno", "+256700000001");
        row.put("firstname", "Jane");
        final Map<String, Object> converted = SmsCampaignWritePlatformServiceJpaImpl.toCaseInsensitiveRows(List.of(row)).get(0);

        final Mustache mustache = new DefaultMustacheFactory().compile(new StringReader("Hi {{firstName}} {{mobileNo}}"), "SmsCampaign");
        final StringWriter writer = new StringWriter();
        mustache.execute(writer, converted);

        assertEquals("Hi Jane +256700000001", writer.toString());
    }
}
