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
package org.apache.fineract.portfolio.client.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import java.lang.reflect.Field;
import java.util.Map;
import org.apache.fineract.infrastructure.codes.domain.CodeValue;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.Test;

class ClientIdentifierUpdateTest {

    @Test
    void blankStatusIsRejectedBeforeTheEntityIsBuilt() {
        final CodeValue documentType = documentType();
        assertThrows(IllegalStateException.class, () -> ClientIdentifier.create(null, documentType, "CM123456", null, null));
        assertThrows(IllegalStateException.class, () -> ClientIdentifier.create(null, documentType, "CM123456", "", null));
    }

    @Test
    void updateUpperCasesStatusAndClearsActiveFlag() throws Exception {
        final ClientIdentifier identifier = ClientIdentifier.create(null, documentType(), "CM123456", "Active", null);
        assertEquals(200, field(identifier, "active"));

        final Map<String, Object> inactivated = identifier.update(command("{\"status\":\"Inactive\"}"));
        assertEquals(ClientIdentifierStatus.INACTIVE, inactivated.get("status"));
        assertNull(field(identifier, "active"));
        assertEquals(100, field(identifier, "status"));

        final ClientIdentifier stillActive = ClientIdentifier.create(null, documentType(), "CM123456", "active", null);
        final Map<String, Object> unchanged = stillActive.update(command("{\"status\":\"Active\"}"));
        assertFalse(unchanged.containsKey("status"));
        assertEquals(200, field(stillActive, "active"));
    }

    private static CodeValue documentType() {
        final CodeValue documentType = mock(CodeValue.class);
        when(documentType.getId()).thenReturn(27L);
        return documentType;
    }

    private static JsonCommand command(final String json) {
        final FromJsonHelper helper = new FromJsonHelper();
        final JsonElement parsed = helper.parse(json);
        return JsonCommand.from(json, parsed, helper, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private static Object field(final ClientIdentifier identifier, final String name) throws Exception {
        final Field field = ClientIdentifier.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(identifier);
    }
}
