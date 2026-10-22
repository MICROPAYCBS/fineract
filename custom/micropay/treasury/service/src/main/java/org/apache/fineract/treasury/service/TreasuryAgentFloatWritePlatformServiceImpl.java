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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MAX_FLOAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MIN_FLOAT_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_ACCOUNT_ID_PARAM;
import static org.apache.fineract.treasury.api.TreasuryApiConstants.TREASURY_FLOAT_REQUEST_RESOURCE_NAME;

import com.google.gson.JsonObject;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.portfolio.savings.service.SavingsAccountWritePlatformService;
import org.apache.fineract.treasury.domain.TreasuryAccount;
import org.apache.fineract.treasury.domain.TreasuryAccountRepository;
import org.apache.fineract.treasury.domain.TreasuryAgentFloat;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatRepository;
import org.apache.fineract.treasury.domain.TreasuryAgentFloatStatus;
import org.apache.fineract.treasury.domain.TreasuryFloatRequest;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestRepository;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestStatus;
import org.apache.fineract.treasury.domain.TreasuryFloatRequestType;
import org.apache.fineract.treasury.domain.TreasurySegregation;
import org.apache.fineract.treasury.exception.TreasuryAccountNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryAgentFloatNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryFloatRequestNotFoundException;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.ActionRequest;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.AgentFloatRequest;
import org.apache.fineract.treasury.serialization.TreasuryWorkflowDataValidator.FloatRequestRequest;
import org.apache.fineract.treasury.service.TreasuryReferenceDataLookup.SavingsAccountInfo;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agent floats and their top-ups and sweeps. Executing a request makes the upstream savings deposit (top-up) or
 * withdrawal (sweep) with the treasury float payment type, so the savings ledger and its accounting stay as upstream
 * keeps them; the product's payment-channel-to-fund-source mapping puts the other leg on the float's treasury account.
 */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryAgentFloatWritePlatformServiceImpl implements TreasuryAgentFloatWritePlatformService {

    private static final String FLOAT_ENTITY = "agent.float";
    private static final String REQUEST_ENTITY = "float.request";
    private static final String DATE_FORMAT = "yyyy-MM-dd";

    private final TreasuryActor actor;
    private final TreasuryWorkflowDataValidator validator;
    private final TreasuryAgentFloatRepository floatRepository;
    private final TreasuryFloatRequestRepository requestRepository;
    private final TreasuryAccountRepository accountRepository;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasurySettingService settingService;
    private final SavingsAccountWritePlatformService savingsAccountWritePlatformService;
    private final FromJsonHelper fromJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createAgentFloat(final JsonCommand command) {
        final AgentFloatRequest request = this.validator.validateAgentFloatForCreate(command.json());
        final SavingsAccountInfo savings = this.referenceDataLookup.findClientSavingsAccount(request.savingsAccountId())
                .orElseThrow(() -> new TreasuryWorkflowException(FLOAT_ENTITY, "savings.account.not.found",
                        "Savings account " + request.savingsAccountId() + " does not exist or has no client", request.savingsAccountId()));
        if (!Objects.equals(savings.clientId(), request.clientId())) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "savings.account.not.of.client",
                    "Savings account " + savings.accountNo() + " does not belong to client " + request.clientId(),
                    request.savingsAccountId(), request.clientId());
        }
        if (!this.floatRepository.findBySavingsAccountIdAndStatus(savings.id(), TreasuryAgentFloatStatus.ACTIVE).isEmpty()) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "savings.account.in.use",
                    "Savings account " + savings.accountNo() + " already holds an active agent float", savings.id());
        }
        final TreasuryAgentFloat agentFloat = new TreasuryAgentFloat();
        agentFloat.setClientId(request.clientId());
        agentFloat.setSavingsAccountId(savings.id());
        agentFloat.setTreasuryAccountId(request.treasuryAccountId());
        agentFloat.setMinFloat(request.minFloat());
        agentFloat.setMaxFloat(request.maxFloat());
        agentFloat.setStatus(TreasuryAgentFloatStatus.ACTIVE);
        validateLink(agentFloat, savings);
        this.floatRepository.saveAndFlush(agentFloat);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(agentFloat.getId())
                .withClientId(agentFloat.getClientId()).withSavingsId(agentFloat.getSavingsAccountId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateAgentFloat(final Long agentFloatId, final JsonCommand command) {
        final TreasuryAgentFloat agentFloat = activeFloat(agentFloatId);
        final AgentFloatRequest request = this.validator.validateAgentFloatForUpdate(command.json());
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.has(TREASURY_ACCOUNT_ID_PARAM) && !Objects.equals(agentFloat.getTreasuryAccountId(), request.treasuryAccountId())) {
            agentFloat.setTreasuryAccountId(request.treasuryAccountId());
            changes.put(TREASURY_ACCOUNT_ID_PARAM, request.treasuryAccountId());
        }
        if (request.has(MIN_FLOAT_PARAM) && amountChanged(agentFloat.getMinFloat(), request.minFloat())) {
            agentFloat.setMinFloat(request.minFloat());
            changes.put(MIN_FLOAT_PARAM, request.minFloat());
        }
        if (request.has(MAX_FLOAT_PARAM) && amountChanged(agentFloat.getMaxFloat(), request.maxFloat())) {
            agentFloat.setMaxFloat(request.maxFloat());
            changes.put(MAX_FLOAT_PARAM, request.maxFloat());
        }
        this.validator.validateFloatRange(agentFloat.getMinFloat(), agentFloat.getMaxFloat());
        if (changes.containsKey(TREASURY_ACCOUNT_ID_PARAM)) {
            validateLink(agentFloat, savingsOf(agentFloat));
        }
        if (!changes.isEmpty()) {
            this.floatRepository.saveAndFlush(agentFloat);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(agentFloatId)
                .withClientId(agentFloat.getClientId()).with(changes).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult closeAgentFloat(final Long agentFloatId, final JsonCommand command) {
        final TreasuryAgentFloat agentFloat = activeFloat(agentFloatId);
        final long open = this.requestRepository.countByAgentFloatIdAndStatusIn(agentFloatId,
                List.of(TreasuryFloatRequestStatus.REQUESTED, TreasuryFloatRequestStatus.APPROVED));
        if (open > 0) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "open.requests",
                    "Agent float " + agentFloatId + " has " + open + " open top-up or sweep requests; execute or cancel them first",
                    agentFloatId, open);
        }
        agentFloat.setStatus(TreasuryAgentFloatStatus.CLOSED);
        this.floatRepository.saveAndFlush(agentFloat);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(agentFloatId)
                .withClientId(agentFloat.getClientId()).with(Map.of("status", TreasuryAgentFloatStatus.CLOSED.name())).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult createFloatRequest(final Long agentFloatId, final JsonCommand command) {
        final FloatRequestRequest request = this.validator.validateFloatRequestForCreate(command.json());
        final TreasuryAgentFloat agentFloat = activeFloat(agentFloatId);
        final TreasuryFloatRequest floatRequest = new TreasuryFloatRequest();
        floatRequest.setAgentFloatId(agentFloat.getId());
        floatRequest.setRequestType(request.requestType());
        floatRequest.setAmount(request.amount());
        floatRequest.setNote(request.note());
        floatRequest.setStatus(TreasuryFloatRequestStatus.REQUESTED);
        floatRequest.setRequestedBy(this.actor.userId(command));
        this.requestRepository.saveAndFlush(floatRequest);
        return requestResult(command, floatRequest, agentFloat, Map.of("status", floatRequest.getStatus().name()));
    }

    @Override
    @Transactional
    public CommandProcessingResult approveFloatRequest(final Long requestId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_FLOAT_REQUEST_RESOURCE_NAME, command.json());
        final TreasuryFloatRequest floatRequest = findRequest(requestId);
        requireStep(floatRequest, floatRequest.getStatus().canApprove(), "approved");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(REQUEST_ENTITY, requestId, "requested", floatRequest.getRequestedBy(), "approve", userId);
        final TreasuryAgentFloat agentFloat = activeFloat(floatRequest.getAgentFloatId());
        floatRequest.setStatus(TreasuryFloatRequestStatus.APPROVED);
        floatRequest.setApprovedBy(userId);
        floatRequest.setApprovedOnDate(DateUtils.getBusinessLocalDate());
        note(floatRequest, action);
        this.requestRepository.saveAndFlush(floatRequest);
        return requestResult(command, floatRequest, agentFloat, Map.of("status", floatRequest.getStatus().name()));
    }

    @Override
    @Transactional
    public CommandProcessingResult executeFloatRequest(final Long requestId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_FLOAT_REQUEST_RESOURCE_NAME, command.json());
        final TreasuryFloatRequest floatRequest = findRequest(requestId);
        requireStep(floatRequest, floatRequest.getStatus().canExecute(), "executed");
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(REQUEST_ENTITY, requestId, "approved", floatRequest.getApprovedBy(), "execute", userId);
        final TreasuryAgentFloat agentFloat = activeFloat(floatRequest.getAgentFloatId());
        final SavingsAccountInfo savings = savingsOf(agentFloat);
        final Long paymentTypeId = validateLink(agentFloat, savings);
        final LocalDate date = action.transactionDate() == null ? DateUtils.getBusinessLocalDate() : action.transactionDate();
        if (DateUtils.isAfterBusinessDate(date)) {
            throw new TreasuryWorkflowException(REQUEST_ENTITY, "future.date",
                    "The date " + date + " is after the business date " + DateUtils.getBusinessLocalDate(), date);
        }

        final JsonCommand savingsCommand = savingsCommand(floatRequest, savings, paymentTypeId, date);
        final CommandProcessingResult savingsResult = floatRequest.getRequestType() == TreasuryFloatRequestType.TOPUP
                ? this.savingsAccountWritePlatformService.deposit(savings.id(), savingsCommand)
                : this.savingsAccountWritePlatformService.withdrawal(savings.id(), savingsCommand);

        floatRequest.setStatus(TreasuryFloatRequestStatus.EXECUTED);
        floatRequest.setExecutedBy(userId);
        floatRequest.setExecutedOnDate(date);
        floatRequest.setSavingsTransactionId(savingsResult.getResourceId());
        note(floatRequest, action);
        this.requestRepository.saveAndFlush(floatRequest);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put("status", floatRequest.getStatus().name());
        changes.put("savingsTransactionId", savingsResult.getResourceId());
        return requestResult(command, floatRequest, agentFloat, changes);
    }

    /**
     * Undoes an executed top-up or sweep with the upstream savings undo, which reverses the savings transaction and its
     * journal entries and applies upstream's own checks (account active, product allows changes, pivot date). The user
     * who executed it may not reverse it, and a reason is required.
     */
    @Override
    @Transactional
    public CommandProcessingResult reverseFloatRequest(final Long requestId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_FLOAT_REQUEST_RESOURCE_NAME, command.json());
        final TreasuryFloatRequest floatRequest = findRequest(requestId);
        requireStep(floatRequest, floatRequest.getStatus().canReverse(), "reversed");
        if (action.reason() == null) {
            throw new TreasuryWorkflowException(REQUEST_ENTITY, "reversal.reason.required",
                    "A reason is required to reverse float request " + requestId, requestId);
        }
        if (action.transactionDate() != null) {
            throw new TreasuryWorkflowException(REQUEST_ENTITY, "reversal.date.not.supported",
                    "A float request reversal undoes the savings transaction on its own date; leave out transactionDate", requestId);
        }
        final Long userId = this.actor.userId(command);
        TreasurySegregation.requireDifferentUser(REQUEST_ENTITY, requestId, "executed", floatRequest.getExecutedBy(), "reverse", userId);
        final TreasuryAgentFloat agentFloat = findFloat(floatRequest.getAgentFloatId());

        this.savingsAccountWritePlatformService.undoTransaction(agentFloat.getSavingsAccountId(), floatRequest.getSavingsTransactionId(),
                false);

        floatRequest.setStatus(TreasuryFloatRequestStatus.REVERSED);
        floatRequest.setReversedBy(userId);
        floatRequest.setReversedOnDate(DateUtils.getBusinessLocalDate());
        floatRequest.setReversalReason(action.reason());
        note(floatRequest, action);
        this.requestRepository.saveAndFlush(floatRequest);
        return requestResult(command, floatRequest, agentFloat, Map.of("status", floatRequest.getStatus().name()));
    }

    @Override
    @Transactional
    public CommandProcessingResult cancelFloatRequest(final Long requestId, final JsonCommand command) {
        final ActionRequest action = this.validator.validateAction(TREASURY_FLOAT_REQUEST_RESOURCE_NAME, command.json());
        final TreasuryFloatRequest floatRequest = findRequest(requestId);
        if (!floatRequest.getStatus().canCancel()) {
            throw new TreasuryWorkflowException(REQUEST_ENTITY, "cannot.cancel",
                    "Float request " + requestId + " is " + floatRequest.getStatus() + " and cannot be cancelled", requestId);
        }
        final TreasuryAgentFloat agentFloat = findFloat(floatRequest.getAgentFloatId());
        floatRequest.setStatus(TreasuryFloatRequestStatus.CANCELLED);
        floatRequest.setCancelledBy(this.actor.userId(command));
        floatRequest.setCancelledOnDate(DateUtils.getBusinessLocalDate());
        note(floatRequest, action);
        this.requestRepository.saveAndFlush(floatRequest);
        return requestResult(command, floatRequest, agentFloat, Map.of("status", floatRequest.getStatus().name()));
    }

    /**
     * The savings account must be active and in the treasury account's currency and office (savings journal entries are
     * posted in the client's office), and the product must send the float payment type to the treasury account's GL
     * account.
     *
     * @return the float payment type
     */
    Long validateLink(final TreasuryAgentFloat agentFloat, final SavingsAccountInfo savings) {
        final TreasuryAccount treasuryAccount = this.accountRepository.findById(agentFloat.getTreasuryAccountId())
                .orElseThrow(() -> new TreasuryAccountNotFoundException(agentFloat.getTreasuryAccountId()));
        if (!treasuryAccount.isActive()) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "treasury.account.closed",
                    "Treasury account " + treasuryAccount.getCode() + " is closed", treasuryAccount.getId());
        }
        if (!savings.isActive()) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "savings.account.not.active",
                    "Savings account " + savings.accountNo() + " is not active", savings.id());
        }
        if (!treasuryAccount.getCurrencyCode().equals(savings.currencyCode())) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "currency.mismatch",
                    "Savings account " + savings.accountNo() + " is in " + savings.currencyCode() + " but treasury account "
                            + treasuryAccount.getCode() + " is in " + treasuryAccount.getCurrencyCode(),
                    savings.id(), treasuryAccount.getId());
        }
        if (!Objects.equals(treasuryAccount.getOfficeId(), savings.officeId())) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "office.mismatch",
                    "Savings account " + savings.accountNo() + " posts in office " + savings.officeId() + " but treasury account "
                            + treasuryAccount.getCode() + " is in office " + treasuryAccount.getOfficeId(),
                    savings.id(), treasuryAccount.getId());
        }
        final Long paymentTypeId = this.settingService.requireFloatPaymentType(treasuryAccount.getCurrencyCode());
        final Long fundSourceGl = this.referenceDataLookup.savingsFundSourceGlAccount(savings.productId(), paymentTypeId).orElse(null);
        if (!Objects.equals(fundSourceGl, treasuryAccount.getGlAccountId())) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "fund.source.mismatch",
                    "The savings product " + savings.productId() + " sends payment type " + paymentTypeId + " to GL account " + fundSourceGl
                            + ", not to GL account " + treasuryAccount.getGlAccountId() + " of treasury account "
                            + treasuryAccount.getCode()
                            + "; map the payment type to that GL account in the product's payment channel to fund source mappings",
                    savings.productId(), paymentTypeId, fundSourceGl, treasuryAccount.getGlAccountId());
        }
        return paymentTypeId;
    }

    private JsonCommand savingsCommand(final TreasuryFloatRequest floatRequest, final SavingsAccountInfo savings, final Long paymentTypeId,
            final LocalDate date) {
        final JsonObject json = new JsonObject();
        json.addProperty("transactionDate", date.format(DateTimeFormatter.ofPattern(DATE_FORMAT)));
        json.addProperty("dateFormat", DATE_FORMAT);
        json.addProperty("locale", "en");
        json.addProperty("transactionAmount", floatRequest.getAmount());
        json.addProperty("paymentTypeId", paymentTypeId);
        json.addProperty("receiptNumber", "TR-FLT-" + floatRequest.getId());
        json.addProperty("note",
                "Treasury float " + floatRequest.getRequestType().name().toLowerCase() + " request " + floatRequest.getId());
        final String text = json.toString();
        return JsonCommand.from(text, this.fromJsonHelper.parse(text), this.fromJsonHelper, "SAVINGSACCOUNT", savings.id(), null, null,
                savings.clientId(), null, savings.id(), null, null, null, null, null, null, null);
    }

    private SavingsAccountInfo savingsOf(final TreasuryAgentFloat agentFloat) {
        return this.referenceDataLookup.findClientSavingsAccount(agentFloat.getSavingsAccountId())
                .orElseThrow(() -> new TreasuryWorkflowException(FLOAT_ENTITY, "savings.account.not.found",
                        "Savings account " + agentFloat.getSavingsAccountId() + " does not exist", agentFloat.getSavingsAccountId()));
    }

    private TreasuryAgentFloat findFloat(final Long agentFloatId) {
        return this.floatRepository.findById(agentFloatId).orElseThrow(() -> new TreasuryAgentFloatNotFoundException(agentFloatId));
    }

    private TreasuryAgentFloat activeFloat(final Long agentFloatId) {
        final TreasuryAgentFloat agentFloat = findFloat(agentFloatId);
        if (!agentFloat.isActive()) {
            throw new TreasuryWorkflowException(FLOAT_ENTITY, "closed", "Agent float " + agentFloatId + " is closed", agentFloatId);
        }
        return agentFloat;
    }

    private TreasuryFloatRequest findRequest(final Long requestId) {
        return this.requestRepository.findById(requestId).orElseThrow(() -> new TreasuryFloatRequestNotFoundException(requestId));
    }

    private static void requireStep(final TreasuryFloatRequest floatRequest, final boolean allowed, final String step) {
        if (!allowed) {
            throw new TreasuryWorkflowException(REQUEST_ENTITY, "invalid.status",
                    "Float request " + floatRequest.getId() + " is " + floatRequest.getStatus() + " and cannot be " + step,
                    floatRequest.getId(), floatRequest.getStatus().name());
        }
    }

    private static void note(final TreasuryFloatRequest floatRequest, final ActionRequest action) {
        if (action.note() != null) {
            floatRequest.setNote(action.note());
        }
    }

    private static boolean amountChanged(final BigDecimal current, final BigDecimal requested) {
        return current == null ? requested != null : requested == null || current.compareTo(requested) != 0;
    }

    private static CommandProcessingResult requestResult(final JsonCommand command, final TreasuryFloatRequest floatRequest,
            final TreasuryAgentFloat agentFloat, final Map<String, Object> changes) {
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(floatRequest.getId())
                .withSubEntityId(agentFloat.getId()).withClientId(agentFloat.getClientId()).withSavingsId(agentFloat.getSavingsAccountId())
                .with(new LinkedHashMap<>(changes)).build();
    }
}
