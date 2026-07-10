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
package org.apache.fineract.integrationtests.accounting;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Calendar;
import org.apache.fineract.accounting.glaccount.domain.GLAccountType;
import org.apache.fineract.accounting.glaccount.domain.GLAccountUsage;
import org.apache.fineract.client.models.PostGLAccountsRequest;
import org.apache.fineract.client.models.PostGLAccountsResponse;
import org.apache.fineract.client.util.CallFailedRuntimeException;
import org.apache.fineract.infrastructure.configuration.api.GlobalConfigurationConstants;
import org.apache.fineract.integrationtests.BaseLoanIntegrationTest;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.accounting.AccountHelper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class StructuredGlAccountCodeIntegrationTest extends BaseLoanIntegrationTest {

    @BeforeEach
    void enableStructuredGlCodes() {
        globalConfigurationHelper.manageConfigurations(GlobalConfigurationConstants.ENFORCE_STRUCTURED_GL_CODES, true);
    }

    @AfterEach
    void disableStructuredGlCodes() {
        globalConfigurationHelper.manageConfigurations(GlobalConfigurationConstants.ENFORCE_STRUCTURED_GL_CODES, false);
    }

    @Test
    void rejectsInvalidGlCodeWhenStructuredEnforcementEnabled() {
        String uniqueName = Utils.uniqueRandomStringGenerator("GL_INVALID" + Calendar.getInstance().getTimeInMillis(), 5);
        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> AccountHelper.createGLAccount(new PostGLAccountsRequest().type(GLAccountType.INCOME.getValue()).glCode("5100")
                        .manualEntriesAllowed(true).usage(1).description(uniqueName).name(uniqueName)));
        assertTrue(exception.getMessage().contains("error.msg.glaccount.glcode.invalid.format")
                || exception.getMessage().contains("error.msg.glaccount.glcode.category.mismatch"));
    }

    @Test
    void acceptsValidStructuredGlCodeWhenEnforcementEnabled() {
        String uniqueName = Utils.uniqueRandomStringGenerator("GL_VALID" + Calendar.getInstance().getTimeInMillis(), 5);
        String glCode = "4" + String.format("%05d", Calendar.getInstance().getTimeInMillis() % 100000);
        final PostGLAccountsResponse newAccount = AccountHelper.createGLAccount(new PostGLAccountsRequest()
                .type(GLAccountType.INCOME.getValue()).glCode(glCode).manualEntriesAllowed(true).usage(1).description(uniqueName)
                .name(uniqueName).tagId(4L));
        Assertions.assertNotNull(newAccount.getResourceId());
        AccountHelper.deleteGLAccount(newAccount.getResourceId());
    }

    @Test
    void acceptsChildGlCodeMatchingHeaderStem() {
        String headerName = Utils.uniqueRandomStringGenerator("GL_HDR" + Calendar.getInstance().getTimeInMillis(), 5);
        String childName = Utils.uniqueRandomStringGenerator("GL_CHD" + Calendar.getInstance().getTimeInMillis(), 5);
        String headerGlCode = "110000";

        final PostGLAccountsResponse header = AccountHelper.createGLAccount(new PostGLAccountsRequest()
                .type(GLAccountType.ASSET.getValue()).glCode(headerGlCode).manualEntriesAllowed(false)
                .usage(GLAccountUsage.HEADER.getValue()).description(headerName).name(headerName));

        final PostGLAccountsResponse child = AccountHelper.createGLAccount(new PostGLAccountsRequest()
                .type(GLAccountType.ASSET.getValue()).glCode("110001").manualEntriesAllowed(true).usage(GLAccountUsage.DETAIL.getValue())
                .parentId(header.getResourceId()).description(childName).name(childName));

        Assertions.assertNotNull(child.getResourceId());
        AccountHelper.deleteGLAccount(child.getResourceId());
        AccountHelper.deleteGLAccount(header.getResourceId());
    }

    @Test
    void rejectsChildGlCodeNotMatchingHeaderStem() {
        String headerName = Utils.uniqueRandomStringGenerator("GL_HDR" + Calendar.getInstance().getTimeInMillis(), 5);
        String childName = Utils.uniqueRandomStringGenerator("GL_CHD" + Calendar.getInstance().getTimeInMillis(), 5);
        String headerGlCode = "110000";

        final PostGLAccountsResponse header = AccountHelper.createGLAccount(new PostGLAccountsRequest()
                .type(GLAccountType.ASSET.getValue()).glCode(headerGlCode).manualEntriesAllowed(false)
                .usage(GLAccountUsage.HEADER.getValue()).description(headerName).name(headerName));

        CallFailedRuntimeException exception = assertThrows(CallFailedRuntimeException.class,
                () -> AccountHelper.createGLAccount(new PostGLAccountsRequest().type(GLAccountType.ASSET.getValue()).glCode("120001")
                        .manualEntriesAllowed(true).usage(GLAccountUsage.DETAIL.getValue()).parentId(header.getResourceId())
                        .description(childName).name(childName)));
        assertTrue(exception.getMessage().contains("error.msg.glaccount.glcode.header.mismatch"));

        AccountHelper.deleteGLAccount(header.getResourceId());
    }
}
