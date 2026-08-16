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
package org.apache.fineract.infrastructure.jobs.data;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JobDetailDataTest {

    @Test
    void constructorStoresDescription() {
        final JobDetailData data = new JobDetailData(6L, "Post Interest For Savings", "SA_PINT",
                "Credits earned interest to active savings balances.", null, null, "0 0 0 1/1 * ? *", true, false, null, null, null, null,
                null, null, null);

        assertEquals(6L, data.getJobId());
        assertEquals("Post Interest For Savings", data.getDisplayName());
        assertEquals("SA_PINT", data.getShortName());
        assertEquals("Credits earned interest to active savings balances.", data.getDescription());
    }

    @Test
    void constructorAllowsNullDescription() {
        final JobDetailData data = new JobDetailData(1L, "Loan COB", "LA_ECOB", null, null, null, "0 0 0 * * ?", false, false, null, null,
                null, null, null, null, null);

        assertNull(data.getDescription());
    }
}
