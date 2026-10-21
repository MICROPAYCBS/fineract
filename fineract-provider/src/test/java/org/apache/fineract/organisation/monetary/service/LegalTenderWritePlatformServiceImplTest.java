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
package org.apache.fineract.organisation.monetary.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.gson.JsonElement;
import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.organisation.monetary.domain.ApplicationCurrencyRepositoryWrapper;
import org.apache.fineract.organisation.monetary.serialization.LegalTenderCommandFromApiJsonDeserializer;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTender;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTenderRepository;
import org.apache.fineract.organisation.teller.domain.LegalTenderType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LegalTenderWritePlatformServiceImplTest {

    @Mock
    private CurrencyLegalTenderRepository legalTenderRepository;

    @Mock
    private ApplicationCurrencyRepositoryWrapper applicationCurrencyRepository;

    @Test
    void duplicateValueIsRejectedWithoutAPermissionCheck() {
        final FromJsonHelper fromJsonHelper = new FromJsonHelper();
        final LegalTenderWritePlatformServiceImpl service = new LegalTenderWritePlatformServiceImpl(this.legalTenderRepository,
                this.applicationCurrencyRepository, new LegalTenderCommandFromApiJsonDeserializer(fromJsonHelper), fromJsonHelper);
        final String json = """
                {"value":100,"tenderType":"NOTE","label":"$100 note","displayOrder":1}
                """;
        final JsonElement parsed = fromJsonHelper.parse(json);
        final JsonCommand command = JsonCommand.from(json, parsed, fromJsonHelper, "LEGAL_TENDER", null, null, null, null, null, null, null,
                null, null, null, null, null, null);
        when(this.legalTenderRepository.existsByCurrencyCodeAndValueAndTenderType(eq("USD"), any(BigDecimal.class),
                eq(LegalTenderType.NOTE.getId()))).thenReturn(true);

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> service.createLegalTender("USD", command));

        assertEquals("error.msg.legal.tender.duplicate", exception.getGlobalisationMessageCode());
        verify(this.legalTenderRepository, never()).saveAndFlush(any(CurrencyLegalTender.class));
    }
}
