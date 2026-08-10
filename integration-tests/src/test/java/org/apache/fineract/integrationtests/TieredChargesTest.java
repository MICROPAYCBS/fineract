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
package org.apache.fineract.integrationtests;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.client.models.GetLoansLoanIdResponse;
import org.apache.fineract.client.models.PostLoanProductsResponse;
import org.apache.fineract.client.models.PostLoansResponse;
import org.apache.fineract.integrationtests.common.ClientHelper;
import org.apache.fineract.integrationtests.common.CommonConstants;
import org.apache.fineract.integrationtests.common.Utils;
import org.apache.fineract.integrationtests.common.charges.ChargesHelper;
import org.apache.fineract.integrationtests.common.loans.LoanTransactionHelper;
import org.apache.fineract.integrationtests.common.savings.SavingsAccountHelper;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

@Slf4j
public class TieredChargesTest extends BaseLoanIntegrationTest {

    @Test
    public void createAndReadLoanTieredCharge() {
        final Integer chargeId = ChargesHelper.createCharges(requestSpec, responseSpec, ChargesHelper.getLoanDisbursementTieredFlatJSON());
        Assertions.assertNotNull(chargeId);

        final HashMap charge = ChargesHelper.getChargeById(requestSpec, responseSpec, chargeId);
        Assertions.assertEquals(Boolean.TRUE, charge.get("useChargeTiers"));
        Assertions.assertNotNull(charge.get("chargeTiers"));
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> tiers = (List<Map<String, Object>>) charge.get("chargeTiers");
        Assertions.assertEquals(2, tiers.size());
        Assertions.assertEquals(0, new BigDecimal(tiers.get(0).get("amountRangeFrom").toString()).compareTo(BigDecimal.ZERO));
        Assertions.assertEquals(0, new BigDecimal(tiers.get(0).get("amount").toString()).compareTo(new BigDecimal("50")));
        Assertions.assertEquals(0, new BigDecimal(tiers.get(1).get("amount").toString()).compareTo(new BigDecimal("100")));
    }

    @Test
    public void createAndReadSavingsTieredCharge() {
        final Integer chargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsWithdrawalTieredFlatJSON());
        Assertions.assertNotNull(chargeId);

        final HashMap charge = ChargesHelper.getChargeById(requestSpec, responseSpec, chargeId);
        Assertions.assertNotNull(charge.get("chargeTiers"));
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> tiers = (List<Map<String, Object>>) charge.get("chargeTiers");
        Assertions.assertEquals(2, tiers.size());
    }

    @Test
    public void loanDisbursementAppliesMatchingFlatTier() {
        runAt("01 June 2024", () -> {
            Long clientId = clientHelper.createClient(ClientHelper.defaultClientCreationRequest()).getClientId();

            String chargePayload = ChargesHelper.getLoanDisbursementTieredFlatJSON().replace("\"currencyCode\":\"USD\"",
                    "\"currencyCode\":\"EUR\"");
            Integer chargeId = ChargesHelper.createCharges(requestSpec, responseSpec, chargePayload);

            final PostLoanProductsResponse loanProductsResponse = loanProductHelper.createLoanProduct(create4IProgressive());
            PostLoansResponse postLoansResponse = loanTransactionHelper.applyLoan(applyLP2ProgressiveLoanRequest(clientId,
                    loanProductsResponse.getResourceId(), "01 June 2024", 150000.0, 10.0, 4, null));
            Long loanId = postLoansResponse.getLoanId();

            String addChargePayload = LoanTransactionHelper.getDisbursementChargesForLoanAsJSON(chargeId.toString(), "0");
            loanTransactionHelper.addChargeForLoan(loanId.intValue(), addChargePayload, responseSpec);

            loanTransactionHelper.approveLoan(loanId, approveLoanRequest(150000.0, "01 June 2024"));
            disburseLoan(loanId, BigDecimal.valueOf(150000.0), "01 June 2024");

            final GetLoansLoanIdResponse loanDetails = loanTransactionHelper.getLoanDetails(loanId);
            Double totalFeeChargesCharged = Utils.getDoubleValue(loanDetails.getRepaymentSchedule().getTotalFeeChargesCharged());
            // base 150000 → open tier flat 100 (lookup, not progressive)
            Assertions.assertEquals(100.0, totalFeeChargesCharged, "Tiered disbursement fee should use open-ended band amount.");
        });
    }

    @Test
    public void savingsWithdrawalAppliesMatchingFlatTier() {
        final SavingsAccountHelper savingsHelper = new SavingsAccountHelper(requestSpec, responseSpec);
        final Integer clientId = ClientHelper.createClient(requestSpec, responseSpec);
        Assertions.assertNotNull(clientId);

        final Integer savingsId = SavingsAccountHelper.openSavingsAccount(requestSpec, responseSpec, clientId, "200000");
        Assertions.assertNotNull(savingsId);

        final Integer chargeId = ChargesHelper.createCharges(requestSpec, responseSpec,
                ChargesHelper.getSavingsWithdrawalTieredFlatJSON());
        Assertions.assertNotNull(chargeId);

        savingsHelper.addChargesForSavings(savingsId, chargeId, false, BigDecimal.ZERO);

        savingsHelper.withdrawalFromSavingsAccount(savingsId, "150000", SavingsAccountHelper.TRANSACTION_DATE,
                CommonConstants.RESPONSE_RESOURCE_ID);

        // Opening 200000 − withdraw 150000 − tier fee 100 = 49800
        final HashMap summary = savingsHelper.getSavingsSummary(savingsId);
        Assertions.assertEquals(Float.parseFloat("49800"), summary.get("accountBalance"),
                "Verifying balance after tiered withdrawal fee");
    }
}
