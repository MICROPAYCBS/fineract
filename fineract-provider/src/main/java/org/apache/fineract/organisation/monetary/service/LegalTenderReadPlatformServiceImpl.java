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

import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.monetary.data.CurrencyLegalTenderData;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTender;
import org.apache.fineract.organisation.teller.domain.CurrencyLegalTenderRepository;
import org.apache.fineract.organisation.teller.exception.LegalTenderNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LegalTenderReadPlatformServiceImpl implements LegalTenderReadPlatformService {

    private static final String RESOURCE_NAME = "LEGAL_TENDER";

    private final PlatformSecurityContext context;
    private final CurrencyLegalTenderRepository legalTenderRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CurrencyLegalTenderData> retrieveAll(final String currencyCode, final boolean includeInactive) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final List<CurrencyLegalTender> tenders = includeInactive
                ? this.legalTenderRepository.findByCurrencyCodeOrderByDisplayOrderAsc(currencyCode)
                : this.legalTenderRepository.findByCurrencyCodeAndIsActiveTrueOrderByDisplayOrderAsc(currencyCode);
        return tenders.stream().map(LegalTenderMapper::toData).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CurrencyLegalTenderData retrieveOne(final String currencyCode, final Long legalTenderId) {
        this.context.authenticatedUser().validateHasReadPermission(RESOURCE_NAME);
        final CurrencyLegalTender legalTender = this.legalTenderRepository.findByIdAndCurrencyCode(legalTenderId, currencyCode)
                .orElseThrow(() -> new LegalTenderNotFoundException(legalTenderId));
        return LegalTenderMapper.toData(legalTender);
    }
}
