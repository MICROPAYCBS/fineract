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
package org.apache.fineract.treasury.serialization;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Set;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.domain.TreasuryStatementFormatType;
import org.apache.fineract.treasury.serialization.TreasuryStatementDataValidator.FormatRequest;
import org.junit.jupiter.api.Test;

class TreasuryStatementDataValidatorTest {

    private final TreasuryStatementDataValidator validator = new TreasuryStatementDataValidator(new FromJsonHelper());

    @Test
    void formatNeedsNoLocale() {
        final FormatRequest request = this.validator
                .validateFormat(
                        "{\"code\": \"stanbic_csv\", \"name\": \"Stanbic\", \"formatType\": \"csv\","
                                + " \"headerRows\": 2, \"dateFormat\": \"dd/MM/yyyy\", \"dateColumn\": \"Date\", \"amountColumn\": \"4\"}",
                        true);

        assertEquals("STANBIC_CSV", request.code());
        assertEquals(TreasuryStatementFormatType.CSV, request.formatType());
        assertEquals(2, request.headerRows());
    }

    @Test
    void formatCodeAndTypeCannotChange() {
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator.validateFormat("{\"code\": \"OTHER\"}", false));
    }

    @Test
    void badDatePatternIsRefused() {
        assertThrows(PlatformApiDataValidationException.class, () -> this.validator
                .validateFormat("{\"code\": \"X\", \"name\": \"X\", \"formatType\": \"CSV\", \"dateFormat\": \"dd/MM/yyyyp\"}", true));
    }

    @Test
    void importDecodesTheFile() {
        final String content = Base64.getEncoder().encodeToString("a,b".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals("a,b".getBytes(StandardCharsets.UTF_8), this.validator
                .validateImport("{\"treasuryAccountId\": 3, \"formatCode\": \"mt940\", \"content\": \"" + content + "\"}").content());
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateImport("{\"treasuryAccountId\": 3, \"formatCode\": \"MT940\", \"content\": \"%%%\"}"));
    }

    @Test
    void matchNeedsBothSidesWithoutDuplicates() {
        assertEquals(Set.of(1L, 2L), this.validator.validateMatch("{\"lineIds\": [1, 2], \"journalEntryIds\": [9]}").lineIds());
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateMatch("{\"lineIds\": [1, 1], \"journalEntryIds\": [9]}"));
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateMatch("{\"lineIds\": [1], \"journalEntryIds\": []}"));
    }

    @Test
    void explanationDateOnlyWithAnAdjustingEntry() {
        assertThrows(PlatformApiDataValidationException.class,
                () -> this.validator.validateExplain("{\"reasonCode\": \"TIMING\", \"transactionDate\": \"2026-10-16\"}"));
        assertEquals(77L, this.validator.validateExplain("{\"reasonCode\": \"BANK_CHARGE\", \"glAccountId\": 77}").glAccountId());
    }
}
