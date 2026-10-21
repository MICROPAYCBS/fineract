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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.junit.jupiter.api.Test;

class ClientStatusDraftTest {

    @Test
    void draftStatusMapsFromIntAndString() {
        assertEquals(ClientStatus.DRAFT, ClientStatus.fromInt(50));
        assertEquals(ClientStatus.DRAFT, ClientStatus.fromString("DRAFT"));
        assertTrue(ClientStatus.DRAFT.isDraft());
        assertFalse(ClientStatus.DRAFT.isPending());
        assertFalse(ClientStatus.PENDING.isDraft());
        assertTrue(ClientStatus.PENDING.isPending());
    }

    @Test
    void draftEnumerationExposesDraftLabel() {
        EnumOptionData option = ClientEnumerations.status(ClientStatus.DRAFT);
        assertEquals(50L, option.getId());
        assertEquals("clientStatusType.draft", option.getCode());
        assertEquals("Draft", option.getValue());
    }

    @Test
    void pendingEnumerationUnchanged() {
        EnumOptionData option = ClientEnumerations.status(ClientStatus.PENDING);
        assertEquals(100L, option.getId());
        assertEquals("clientStatusType.pending", option.getCode());
        assertEquals("Pending", option.getValue());
    }
}
