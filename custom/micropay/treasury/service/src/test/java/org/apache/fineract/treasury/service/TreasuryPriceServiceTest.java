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
package org.apache.fineract.treasury.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.businessdate.domain.BusinessDateType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenant;
import org.apache.fineract.infrastructure.core.domain.FineractPlatformTenantConnection;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.ThreadLocalContextUtil;
import org.apache.fineract.treasury.deal.PriceSource;
import org.apache.fineract.treasury.domain.TreasuryDeal;
import org.apache.fineract.treasury.domain.TreasuryPrice;
import org.apache.fineract.treasury.domain.TreasuryPriceRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryHoldingDataValidator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreasuryPriceServiceTest {

    private final TreasuryActor actor = mock(TreasuryActor.class);
    private final TreasuryPriceRepository repository = mock(TreasuryPriceRepository.class);
    private final TreasuryPriceService service = new TreasuryPriceService(new TreasuryHoldingDataValidator(new FromJsonHelper()),
            this.actor, this.repository);
    private final List<TreasuryPrice> saved = new ArrayList<>();

    @BeforeEach
    void setUp() {
        ThreadLocalContextUtil
                .setTenant(new FineractPlatformTenant(1L, "default", "Default", "UTC", mock(FineractPlatformTenantConnection.class)));
        ThreadLocalContextUtil.setBusinessDates(new HashMap<>(Map.of(BusinessDateType.BUSINESS_DATE, LocalDate.of(2026, 10, 31))));
        lenient().when(this.actor.userId(any())).thenReturn(5L);
        lenient().when(this.repository.saveAndFlush(any())).thenAnswer(call -> {
            final TreasuryPrice price = call.getArgument(0);
            if (price.getId() == null) {
                price.setId((long) this.saved.size() + 1);
                this.saved.add(price);
            }
            return price;
        });
        lenient().when(this.repository.findByInstrumentIdAndPriceDate(anyString(), any())).thenAnswer(call -> this.saved.stream()
                .filter(p -> p.getInstrumentId().equals(call.getArgument(0)) && p.getPriceDate().equals(call.getArgument(1))).findFirst());
    }

    @AfterEach
    void tearDown() {
        ThreadLocalContextUtil.reset();
    }

    @Test
    void aSecondPriceForTheSameDateReplacesTheFirst() {
        Map<String, Object> changes = this.service.create(JsonCommand.from("""
                {"prices": [{"instrumentId": "UG0000001234", "priceDate": "2026-10-30", "cleanPrice": 99},
                            {"instrumentId": "TB-2027-01-15", "priceDate": "2026-10-30", "yield": 10.5, "source": "BOU_AUCTION"}]}
                """)).getChanges();
        assertEquals(2, changes.get("created"));

        changes = this.service
                .create(JsonCommand.from("{\"instrumentId\": \"UG0000001234\", \"priceDate\": \"2026-10-30\", \"cleanPrice\": 99.1}"))
                .getChanges();
        assertEquals(0, changes.get("created"));
        assertEquals(1, changes.get("replaced"));
        assertEquals(2, this.saved.size());
        assertEquals(new BigDecimal("99.1"), this.saved.getFirst().getCleanPrice());
        assertEquals(PriceSource.MANUAL, this.saved.getFirst().getSource());
        assertEquals(5L, this.saved.getFirst().getEnteredBy());
    }

    @Test
    void futureDatesAndDuplicatesInABatchAreRefused() {
        assertCode("price.future.date", () -> this.service
                .create(JsonCommand.from("{\"instrumentId\": \"UG0000001234\", \"priceDate\": \"2026-11-01\", \"cleanPrice\": 99}")));
        assertCode("price.duplicate.in.batch", () -> this.service.create(JsonCommand.from("""
                {"prices": [{"instrumentId": "A", "priceDate": "2026-10-30", "cleanPrice": 99},
                            {"instrumentId": "A", "priceDate": "2026-10-30", "cleanPrice": 98}]}
                """)));
        assertEquals(0, this.saved.size());
    }

    @Test
    void aDealIsPricedByItsIsinElseItsReference() {
        final TreasuryDeal deal = new TreasuryDeal();
        deal.setExternalReference("TB-2027-01-15");
        assertEquals("TB-2027-01-15", TreasuryPriceService.instrumentId(deal));
        deal.setIsin("UG0000009999");
        assertEquals("UG0000009999", TreasuryPriceService.instrumentId(deal));
    }

    private static void assertCode(final String suffix, final org.junit.jupiter.api.function.Executable call) {
        assertEquals("error.msg.treasury." + suffix, assertThrows(TreasuryWorkflowException.class, call).getGlobalisationMessageCode());
    }
}
