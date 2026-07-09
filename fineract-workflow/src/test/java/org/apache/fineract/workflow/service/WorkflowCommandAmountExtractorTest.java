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
package org.apache.fineract.workflow.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.apache.fineract.commands.domain.CommandSource;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class WorkflowCommandAmountExtractorTest {

    private WorkflowCommandAmountExtractor extractor;

    @BeforeEach
    void setUp() {
        this.extractor = new WorkflowCommandAmountExtractor(new FromJsonHelper());
    }

    @Test
    void extractsDisburseAmountAndCurrency() {
        final CommandSource commandSource = CommandSource.builder().actionName("DISBURSE").entityName("LOAN").build();
        final JsonCommand command = JsonCommand.from("{\"transactionAmount\":2500000,\"currencyCode\":\"UGX\"}");

        final WorkflowCommandAmountContext context = this.extractor.extract(commandSource, command);

        assertEquals(new BigDecimal("2500000"), context.getAmount());
        assertEquals("UGX", context.getCurrencyCode());
    }

    @Test
    void returnsEmptyContextForBlankJson() {
        final CommandSource commandSource = CommandSource.builder().actionName("APPROVE").entityName("LOAN").build();
        final JsonCommand command = JsonCommand.from("");

        final WorkflowCommandAmountContext context = this.extractor.extract(commandSource, command);

        assertNull(context.getAmount());
        assertNull(context.getCurrencyCode());
    }
}
