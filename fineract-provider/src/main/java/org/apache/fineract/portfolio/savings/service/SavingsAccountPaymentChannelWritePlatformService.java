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
package org.apache.fineract.portfolio.savings.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.charge.domain.Charge;
import org.apache.fineract.portfolio.charge.domain.ChargeCalculationType;
import org.apache.fineract.portfolio.charge.domain.ChargeTimeType;
import org.apache.fineract.portfolio.savings.data.SavingsAccountPaymentChannelData;
import org.apache.fineract.portfolio.savings.data.SavingsProductPaymentChannelData;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountAssembler;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChargeRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountPaymentChannelRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsPaymentChannelStatus;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelCharge;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsAccountPaymentChannelWritePlatformService {

    private final PlatformSecurityContext context;
    private final SavingsAccountAssembler savingAccountAssembler;
    private final SavingsAccountRepositoryWrapper savingAccountRepositoryWrapper;
    private final SavingsAccountChargeRepositoryWrapper savingsAccountChargeRepository;
    private final SavingsProductPaymentChannelRepository productChannelRepository;
    private final SavingsAccountPaymentChannelRepository accountChannelRepository;
    private final SavingsProductPaymentChannelReadPlatformService productChannelReadPlatformService;

    @Transactional(readOnly = true)
    public Collection<SavingsAccountPaymentChannelData> retrieveAccountChannels(final Long savingsAccountId) {
        this.context.authenticatedUser();
        final SavingsAccount account = this.savingAccountRepositoryWrapper.findOneWithNotFoundDetection(savingsAccountId);
        final Long productId = account.productId();
        final List<SavingsProductPaymentChannel> productChannels = this.productChannelRepository.findByProductId(productId);
        final List<SavingsAccountPaymentChannelData> result = new ArrayList<>();
        for (final SavingsProductPaymentChannel productChannel : productChannels) {
            if (!productChannel.isActive()) {
                continue;
            }
            final Optional<SavingsAccountPaymentChannel> activeSub = this.accountChannelRepository
                    .findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(savingsAccountId,
                            productChannel.getPaymentType().getId(), SavingsPaymentChannelStatus.ACTIVE.getValue());
            result.add(toAccountData(productChannel, activeSub.orElse(null)));
        }
        return result;
    }

    @Transactional
    public CommandProcessingResult subscribe(final Long savingsAccountId, final JsonCommand command) {
        this.context.authenticatedUser();
        final Long paymentTypeId = requirePaymentTypeId(command);
        final SavingsAccount account = this.savingAccountAssembler.assembleFrom(savingsAccountId, false);

        final SavingsProductPaymentChannel productChannel = this.productChannelRepository
                .findByProductIdAndPaymentTypeId(account.productId(), paymentTypeId)
                .orElseThrow(() -> validationException("paymentTypeId", paymentTypeId, "not.in.product.channel.catalog"));

        if (!productChannel.isActive()) {
            throw validationException("paymentTypeId", paymentTypeId, "channel.not.active");
        }
        if (!productChannel.isPremium()) {
            throw validationException("paymentTypeId", paymentTypeId, "subscription.only.for.premium.channels");
        }

        final Optional<SavingsAccountPaymentChannel> existing = this.accountChannelRepository
                .findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(savingsAccountId, paymentTypeId,
                        SavingsPaymentChannelStatus.ACTIVE.getValue());
        if (existing.isPresent()) {
            throw validationException("paymentTypeId", paymentTypeId, "already.subscribed");
        }

        final LocalDate today = DateUtils.getBusinessLocalDate();
        final SavingsAccountPaymentChannel subscription = SavingsAccountPaymentChannel.subscribe(account, productChannel, today);

        final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MM yyyy");
        for (final SavingsProductPaymentChannelCharge channelCharge : productChannel.getCharges()) {
            final Charge chargeDefinition = channelCharge.getCharge();
            ChargeTimeType chargeTime = null;
            if (chargeDefinition.getChargeTimeType() != null) {
                chargeTime = ChargeTimeType.fromInt(chargeDefinition.getChargeTimeType());
            }
            ChargeCalculationType chargeCalculation = null;
            if (chargeDefinition.getChargeCalculation() != null) {
                chargeCalculation = ChargeCalculationType.fromInt(chargeDefinition.getChargeCalculation());
            }
            final BigDecimal amount = channelCharge.getAmount() != null ? channelCharge.getAmount() : chargeDefinition.getAmount();
            final SavingsAccountCharge savingsAccountCharge = SavingsAccountCharge.createNewWithoutSavingsAccount(chargeDefinition, amount,
                    chargeTime, chargeCalculation, null, true, chargeDefinition.getFeeOnMonthDay(), chargeDefinition.feeInterval());
            savingsAccountCharge.update(account);
            account.addCharge(fmt, savingsAccountCharge, chargeDefinition);
            this.savingsAccountChargeRepository.save(savingsAccountCharge);
            subscription.addLinkedCharge(savingsAccountCharge);
        }

        this.accountChannelRepository.save(subscription);
        this.savingAccountRepositoryWrapper.saveAndFlush(account);

        return new CommandProcessingResultBuilder().withEntityId(subscription.getId()).withSavingsId(savingsAccountId)
                .withOfficeId(account.officeId()).withClientId(account.clientId()).withGroupId(account.groupId()).build();
    }

    @Transactional
    public CommandProcessingResult unsubscribe(final Long savingsAccountId, final JsonCommand command) {
        this.context.authenticatedUser();
        final Long paymentTypeId = requirePaymentTypeId(command);
        final SavingsAccount account = this.savingAccountAssembler.assembleFrom(savingsAccountId, false);

        final SavingsAccountPaymentChannel subscription = this.accountChannelRepository
                .findFirstBySavingsAccountIdAndPaymentTypeIdAndStatusOrderByIdDesc(savingsAccountId, paymentTypeId,
                        SavingsPaymentChannelStatus.ACTIVE.getValue())
                .orElseThrow(() -> validationException("paymentTypeId", paymentTypeId, "not.actively.subscribed"));

        final LocalDate today = DateUtils.getBusinessLocalDate();
        for (final var link : subscription.getLinkedCharges()) {
            final SavingsAccountCharge charge = link.getSavingsAccountCharge();
            if (charge == null || charge.isNotActive()) {
                continue;
            }
            if (charge.isRecurringFee()) {
                // Follow recurring-fee inactivation rules (due / overpaid checks skipped for channel ownership —
                // inactivate when not due)
                final LocalDate nextDueDate = charge.getNextDueDateFrom(today);
                if (charge.isChargeIsDue(nextDueDate)) {
                    throw validationException("savingsAccountChargeId", charge.getId(),
                            "inactivation.of.charge.not.allowed.when.charge.is.due");
                }
                account.inactivateCharge(charge, today);
            } else {
                // Non-recurring channel fees: mark inactive; paid history remains
                charge.inactiavateCharge(today);
            }
            this.savingsAccountChargeRepository.save(charge);
        }

        subscription.unsubscribe(today);
        this.accountChannelRepository.save(subscription);
        this.savingAccountRepositoryWrapper.saveAndFlush(account);

        return new CommandProcessingResultBuilder().withEntityId(subscription.getId()).withSavingsId(savingsAccountId)
                .withOfficeId(account.officeId()).withClientId(account.clientId()).withGroupId(account.groupId()).build();
    }

    private SavingsAccountPaymentChannelData toAccountData(final SavingsProductPaymentChannel productChannel,
            final SavingsAccountPaymentChannel subscription) {
        final SavingsProductPaymentChannelData productData = this.productChannelReadPlatformService.toData(productChannel);
        final boolean subscribed = subscription != null && subscription.isActive();
        final boolean allowedForDeposit = !productChannel.isPremium() || subscribed;
        EnumOptionData status = null;
        LocalDate subscribedOn = null;
        LocalDate unsubscribedOn = null;
        Long subscriptionId = null;
        if (subscription != null) {
            final SavingsPaymentChannelStatus channelStatus = SavingsPaymentChannelStatus.fromInt(subscription.getStatus());
            status = new EnumOptionData(channelStatus.getValue().longValue(), channelStatus.getCode(), channelStatus.name());
            subscribedOn = subscription.getSubscribedOnDate();
            unsubscribedOn = subscription.getUnsubscribedOnDate();
            subscriptionId = subscription.getId();
        }
        return SavingsAccountPaymentChannelData.builder().id(subscriptionId).paymentTypeId(productData.getPaymentTypeId())
                .paymentType(productData.getPaymentType()).isPremium(productData.isPremium()).isActive(productData.isActive())
                .name(productData.getName()).description(productData.getDescription()).subscriptionStatus(status)
                .subscribedOnDate(subscribedOn).unsubscribedOnDate(unsubscribedOn).allowedForDeposit(allowedForDeposit)
                .charges(productData.getCharges()).build();
    }

    private Long requirePaymentTypeId(final JsonCommand command) {
        final Long paymentTypeId = command.longValueOfParameterNamed("paymentTypeId");
        if (paymentTypeId == null) {
            throw validationException("paymentTypeId", null, "parameter.mandatory");
        }
        return paymentTypeId;
    }

    private PlatformApiDataValidationException validationException(final String param, final Object value, final String code) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder baseDataValidator = new DataValidatorBuilder(errors).resource("savingsaccount.paymentchannel");
        baseDataValidator.reset().parameter(param).value(value).failWithCode(code);
        return new PlatformApiDataValidationException(errors);
    }
}
