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
package org.apache.fineract.infrastructure.jobs.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import java.util.Map;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.jobs.api.SchedulerJobApiConstants;
import org.junit.jupiter.api.Test;

class ScheduledJobDetailTest {

    private final FromJsonHelper fromJsonHelper = new FromJsonHelper();

    @Test
    void updateChangesDescription() {
        final ScheduledJobDetail job = new ScheduledJobDetail().setDescription("Original description");

        final Map<String, Object> changes = job.update(command("{\"description\":\"Credits earned interest to savings balances.\"}"));

        assertEquals("Credits earned interest to savings balances.", job.getDescription());
        assertEquals("Credits earned interest to savings balances.", changes.get(SchedulerJobApiConstants.descriptionParamName));
    }

    @Test
    void updateClearsDescriptionWhenEmpty() {
        final ScheduledJobDetail job = new ScheduledJobDetail().setDescription("Original description");

        final Map<String, Object> changes = job.update(command("{\"description\":\"\"}"));

        assertNull(job.getDescription());
        assertTrue(changes.containsKey(SchedulerJobApiConstants.descriptionParamName));
    }

    @Test
    void updateIgnoresDescriptionWhenOmitted() {
        final ScheduledJobDetail job = new ScheduledJobDetail().setJobDisplayName("Post Interest For Savings")
                .setDescription("Keep this text");

        final Map<String, Object> changes = job.update(command("{\"displayName\":\"Post Interest For Savings\"}"));

        assertEquals("Keep this text", job.getDescription());
        assertFalse(changes.containsKey(SchedulerJobApiConstants.descriptionParamName));
    }

    private JsonCommand command(final String json) {
        return JsonCommand.from(json, JsonParser.parseString(json), fromJsonHelper, null, 1L, 2L, 3L, 4L, null, null, null, null, null, null,
                null, null, null);
    }
}
