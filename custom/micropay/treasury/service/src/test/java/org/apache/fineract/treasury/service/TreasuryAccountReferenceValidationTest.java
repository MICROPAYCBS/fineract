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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAccountStatus;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatRepository;
import org.apache.fineract.treasury.domain.TreasuryTransferRepository;
import org.apache.fineract.treasury.exception.TreasuryAccountException;
import org.apache.fineract.treasury.serialization.TreasuryAccountDataValidator;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.GlAccountInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TreasuryAccountReferenceValidationTest {

    private static final long GL_ACCOUNT_ID = 12L;
    private static final int LIABILITY = 2;
    private static final int HEADER = 2;

    private final TreasuryAccountRepository repository = mock(TreasuryAccountRepository.class);
    private final TreasuryReferenceDataLookup lookup = mock(TreasuryReferenceDataLookup.class);
    private final TreasuryAccountWritePlatformServiceImpl service = new TreasuryAccountWritePlatformServiceImpl(
            new TreasuryAccountDataValidator(new FromJsonHelper()), this.repository, this.lookup, mock(TreasuryTransferRepository.class),
            mock(TreasuryAgentFloatRepository.class));

    @BeforeEach
    void validReferences() {
        when(this.lookup.findGlAccount(GL_ACCOUNT_ID)).thenReturn(Optional.of(glAccount(TreasuryReferenceDataLookup.GL_CLASSIFICATION_ASSET,
                TreasuryReferenceDataLookup.GL_USAGE_DETAIL, false)));
        when(this.lookup.officeExists(anyLong())).thenReturn(true);
        when(this.lookup.isOrganisationCurrency(anyString())).thenReturn(true);
        when(this.repository.findByGlAccountIdAndOfficeIdAndCurrencyCodeAndStatus(anyLong(), anyLong(), anyString(), any()))
                .thenReturn(List.of());
    }

    @Test
    void assetDetailAccountInAnOrganisationCurrencyIsAccepted() {
        assertDoesNotThrow(() -> this.service.validateReferences(account()));
    }

    @Test
    void missingGlAccountIsRejected() {
        when(this.lookup.findGlAccount(GL_ACCOUNT_ID)).thenReturn(Optional.empty());

        assertRejected("gl.account.not.found");
    }

    @Test
    void nonAssetGlAccountIsRejected() {
        when(this.lookup.findGlAccount(GL_ACCOUNT_ID))
                .thenReturn(Optional.of(glAccount(LIABILITY, TreasuryReferenceDataLookup.GL_USAGE_DETAIL, false)));

        assertRejected("gl.account.not.asset.detail");
    }

    @Test
    void headerGlAccountIsRejected() {
        when(this.lookup.findGlAccount(GL_ACCOUNT_ID))
                .thenReturn(Optional.of(glAccount(TreasuryReferenceDataLookup.GL_CLASSIFICATION_ASSET, HEADER, false)));

        assertRejected("gl.account.not.asset.detail");
    }

    @Test
    void disabledGlAccountIsRejected() {
        when(this.lookup.findGlAccount(GL_ACCOUNT_ID)).thenReturn(Optional.of(
                glAccount(TreasuryReferenceDataLookup.GL_CLASSIFICATION_ASSET, TreasuryReferenceDataLookup.GL_USAGE_DETAIL, true)));

        assertRejected("gl.account.disabled");
    }

    @Test
    void unknownOfficeIsRejected() {
        when(this.lookup.officeExists(anyLong())).thenReturn(false);

        assertRejected("office.not.found");
    }

    @Test
    void currencyOutsideTheOrganisationIsRejected() {
        when(this.lookup.isOrganisationCurrency("UGX")).thenReturn(false);

        assertRejected("currency.not.organisation.currency");
    }

    @Test
    void glAccountUsedByAnotherActiveAccountIsRejected() {
        final TreasuryAccount other = account();
        other.setId(99L);
        when(this.repository.findByGlAccountIdAndOfficeIdAndCurrencyCodeAndStatus(GL_ACCOUNT_ID, 1L, "UGX", TreasuryAccountStatus.ACTIVE))
                .thenReturn(List.of(other));

        assertRejected("gl.account.in.use");
    }

    @Test
    void accountDoesNotClashWithItself() {
        final TreasuryAccount account = account();
        account.setId(7L);
        when(this.repository.findByGlAccountIdAndOfficeIdAndCurrencyCodeAndStatus(GL_ACCOUNT_ID, 1L, "UGX", TreasuryAccountStatus.ACTIVE))
                .thenReturn(List.of(account));

        assertDoesNotThrow(() -> this.service.validateReferences(account));
    }

    private void assertRejected(final String codeSuffix) {
        final TreasuryAccountException exception = assertThrows(TreasuryAccountException.class,
                () -> this.service.validateReferences(account()));
        assertEquals("error.msg.treasury.account." + codeSuffix, exception.getGlobalisationMessageCode());
    }

    private static TreasuryAccount account() {
        final TreasuryAccount account = new TreasuryAccount();
        account.setCode("STANBIC-UGX");
        account.setGlAccountId(GL_ACCOUNT_ID);
        account.setOfficeId(1L);
        account.setCurrencyCode("UGX");
        account.setStatus(TreasuryAccountStatus.ACTIVE);
        return account;
    }

    private static GlAccountInfo glAccount(final int classification, final int usage, final boolean disabled) {
        return new GlAccountInfo(GL_ACCOUNT_ID, classification, usage, disabled);
    }
}
