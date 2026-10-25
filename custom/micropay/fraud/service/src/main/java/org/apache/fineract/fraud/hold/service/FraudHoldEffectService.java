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
package org.apache.fineract.fraud.hold.service;

import com.google.gson.JsonObject;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.fraud.hold.data.FraudHoldEffectData;
import org.apache.fineract.fraud.hold.data.FraudHoldEffectType;
import org.apache.fineract.fraud.hold.data.FraudHoldEntityType;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.loanaccount.api.LoanApiConstants;
import org.apache.fineract.portfolio.loanaccount.service.LoanWritePlatformService;
import org.apache.fineract.portfolio.savings.SavingsApiConstants;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.springframework.stereotype.Service;

/**
 * Applies and reverts the changes a fraud hold makes to core accounts, using Fineract's own savings debit block and
 * loan fraud flag so that every existing check on those keeps working.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FraudHoldEffectService {

    private final FraudHoldRepository holdRepository;
    private final FraudHoldTargetRepository targetRepository;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final LoanWritePlatformService loanWritePlatformService;
    private final FromJsonHelper fromJsonHelper;

    public void applyEffects(Long holdId, FraudHoldEntityType entityType, Long entityId, String reason) {
        switch (entityType) {
            case SAVINGS_ACCOUNT -> blockSavingsDebits(holdId, entityId, reason);
            case CLIENT -> targetRepository.findActiveSavingsAccountIdsOfClient(entityId)
                    .forEach(savingsId -> blockSavingsDebits(holdId, savingsId, reason));
            case LOAN -> flagLoanAsFraud(holdId, entityId);
        }
    }

    /**
     * Reverts each change the released hold made, unless another active hold still needs it; in that case the change is
     * handed over to that hold so it is reverted when the last hold goes.
     */
    public void revertEffects(Long holdId) {
        final List<FraudHoldEffectData> effects = holdRepository.findEffects(holdId);
        for (FraudHoldEffectData effect : effects) {
            final Optional<Long> coveringHoldId = switch (effect.effectType()) {
                case SAVINGS_DEBIT_BLOCK -> holdRepository.findOtherActiveHoldCoveringSavings(effect.targetId(), holdId);
                case LOAN_FRAUD_FLAG -> holdRepository.findOtherActiveHoldCoveringLoan(effect.targetId(), holdId);
            };
            if (coveringHoldId.isPresent()) {
                holdRepository.reassignEffect(effect.id(), coveringHoldId.get());
                continue;
            }
            switch (effect.effectType()) {
                case SAVINGS_DEBIT_BLOCK -> unblockSavingsDebits(effect.targetId());
                case LOAN_FRAUD_FLAG -> unflagLoan(effect.targetId());
            }
            holdRepository.deleteEffect(effect.id());
        }
    }

    private void blockSavingsDebits(Long holdId, Long savingsId, String reason) {
        if (!targetRepository.isSavingsAccountActive(savingsId) || targetRepository.areSavingsDebitsBlocked(savingsId)) {
            // Nothing to change: inactive accounts cannot be debited and blocked ones stay blocked
            return;
        }
        final JsonObject json = new JsonObject();
        json.addProperty(SavingsApiConstants.reasonForBlockParamName, "Fraud hold " + holdId + ": " + reason);
        savingsAccountWritePlatformService.blockDebits(savingsId, JsonCommand.fromJsonElement(savingsId, json, fromJsonHelper));
        holdRepository.insertEffect(holdId, FraudHoldEffectType.SAVINGS_DEBIT_BLOCK, savingsId);
    }

    private void unblockSavingsDebits(Long savingsId) {
        if (!targetRepository.areSavingsDebitsBlocked(savingsId)) {
            log.info("Debits on savings account {} were already unblocked outside the fraud hold", savingsId);
            return;
        }
        savingsAccountWritePlatformService.unblockDebits(savingsId);
    }

    private void flagLoanAsFraud(Long holdId, Long loanId) {
        if (targetRepository.isLoanFlaggedAsFraud(loanId)) {
            return;
        }
        loanWritePlatformService.markLoanAsFraud(loanId, fraudCommand(loanId, true));
        holdRepository.insertEffect(holdId, FraudHoldEffectType.LOAN_FRAUD_FLAG, loanId);
    }

    private void unflagLoan(Long loanId) {
        if (!targetRepository.isLoanFlaggedAsFraud(loanId)) {
            log.info("Loan {} was already unflagged outside the fraud hold", loanId);
            return;
        }
        loanWritePlatformService.markLoanAsFraud(loanId, fraudCommand(loanId, false));
    }

    private JsonCommand fraudCommand(Long loanId, boolean fraud) {
        final JsonObject json = new JsonObject();
        json.addProperty(LoanApiConstants.FRAUD_ATTRIBUTE_NAME, fraud);
        return JsonCommand.fromJsonElement(loanId, json, fromJsonHelper);
    }
}
