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
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.configuration.domain.ConfigurationDomainService;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.portfolio.paymentdetail.domain.PaymentDetail;
import org.apache.fineract.portfolio.savings.data.SavingsAccountChannelLimitData;
import org.apache.fineract.portfolio.savings.data.SavingsChannelLimitValues;
import org.apache.fineract.portfolio.savings.domain.SavingsAccount;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimit;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitConsumption;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitConsumptionRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitUsage;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountChannelLimitUsageRepository;
import org.apache.fineract.portfolio.savings.domain.SavingsAccountRepositoryWrapper;
import org.apache.fineract.portfolio.savings.domain.SavingsChannelLimitDirection;
import org.apache.fineract.portfolio.savings.domain.SavingsChannelLimitPeriodType;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannel;
import org.apache.fineract.portfolio.savings.domain.SavingsProductPaymentChannelRepository;
import org.apache.fineract.useradministration.domain.AppUser;
import org.apache.fineract.useradministration.domain.SelfServiceUserClientMappingRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SavingsChannelLimitService {

    public static final String BYPASS_PERMISSION = "BYPASS_SAVINGS_CHANNEL_LIMIT";

    private static final String LIMIT_RESOURCE = "savingsaccount.channellimit";
    private static final String TRANSACTION_RESOURCE = "savingsaccount.transaction";

    private final PlatformSecurityContext context;
    private final ConfigurationDomainService configurationDomainService;
    private final SavingsAccountRepositoryWrapper savingsAccountRepository;
    private final SavingsProductPaymentChannelRepository productChannelRepository;
    private final SavingsAccountChannelLimitRepository limitRepository;
    private final SavingsAccountChannelLimitUsageRepository usageRepository;
    private final SavingsAccountChannelLimitConsumptionRepository consumptionRepository;
    private final SelfServiceUserClientMappingRepository selfServiceUserClientMappingRepository;

    @Transactional
    public SavingsChannelLimitHold authorize(final SavingsChannelLimitCommand command) {
        if (command == null || command.amount() == null || command.account() == null || command.transactionDate() == null) {
            return SavingsChannelLimitHold.none();
        }
        if (command.interestTransfer() || !command.regularTransaction()) {
            return SavingsChannelLimitHold.none();
        }
        final AppUser user = this.context.authenticatedUser();
        if (user != null && user.hasSpecificPermissionTo(BYPASS_PERMISSION)) {
            return SavingsChannelLimitHold.none();
        }
        final SavingsProductPaymentChannel channel = resolveChannel(command);
        if (channel == null) {
            return SavingsChannelLimitHold.none();
        }
        final Optional<SavingsAccountChannelLimit> customerLimit = this.limitRepository
                .findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(command.account().getId(), channel.getId(),
                        command.direction());
        if (customerLimit.isPresent() && customerLimit.get().applyPendingIfDue(DateUtils.getAuditOffsetDateTime())) {
            this.limitRepository.save(customerLimit.get());
        }
        final SavingsChannelLimitValues customerValues = customerLimit.map(SavingsAccountChannelLimit::liveValues)
                .orElse(SavingsChannelLimitValues.none());
        final SavingsChannelLimitValues effective = SavingsChannelLimitRules.effective(customerValues, channel.limitsFor(command.direction()));
        if (effective.isUnlimited()) {
            return SavingsChannelLimitHold.none();
        }

        final BigDecimal amount = command.amount();
        if (effective.maxPerTxn() != null && amount.compareTo(effective.maxPerTxn()) > 0) {
            throw limitExceeded("transactionAmount", amount, "exceeds.per.transaction");
        }

        final List<SavingsAccountChannelLimitUsage> touched = new ArrayList<>();
        if (effective.maxPerDay() != null || effective.maxCountPerDay() != null) {
            final SavingsAccountChannelLimitUsage day = lockUsage(command.account(), channel, command.direction(),
                    SavingsChannelLimitPeriodType.DAY, SavingsChannelLimitPeriodType.DAY.periodStart(command.transactionDate()));
            if (effective.maxPerDay() != null && day.getAmountUsed().add(amount).compareTo(effective.maxPerDay()) > 0) {
                throw limitExceeded("transactionAmount", amount, "exceeds.daily");
            }
            if (effective.maxCountPerDay() != null && day.getCountUsed() + 1 > effective.maxCountPerDay()) {
                throw limitExceeded("transactionCount", day.getCountUsed() + 1, "exceeds.daily.count");
            }
            touched.add(day);
        }
        if (effective.maxPerMonth() != null || effective.maxCountPerMonth() != null) {
            final SavingsAccountChannelLimitUsage month = lockUsage(command.account(), channel, command.direction(),
                    SavingsChannelLimitPeriodType.MONTH, SavingsChannelLimitPeriodType.MONTH.periodStart(command.transactionDate()));
            if (effective.maxPerMonth() != null && month.getAmountUsed().add(amount).compareTo(effective.maxPerMonth()) > 0) {
                throw limitExceeded("transactionAmount", amount, "exceeds.monthly");
            }
            if (effective.maxCountPerMonth() != null && month.getCountUsed() + 1 > effective.maxCountPerMonth()) {
                throw limitExceeded("transactionCount", month.getCountUsed() + 1, "exceeds.monthly.count");
            }
            touched.add(month);
        }
        for (final SavingsAccountChannelLimitUsage usage : touched) {
            usage.add(amount, 1);
        }
        return new SavingsChannelLimitHold(amount, touched);
    }

    @Transactional
    public void record(final SavingsChannelLimitHold hold, final Long savingsTransactionId) {
        if (hold == null || hold.isEmpty() || savingsTransactionId == null) {
            return;
        }
        for (final SavingsAccountChannelLimitUsage usage : hold.usages()) {
            this.consumptionRepository.save(SavingsAccountChannelLimitConsumption.of(savingsTransactionId, usage, hold.amount(), 1));
        }
    }

    @Transactional
    public void release(final Long savingsTransactionId) {
        if (savingsTransactionId == null) {
            return;
        }
        final List<SavingsAccountChannelLimitConsumption> rows = this.consumptionRepository
                .findBySavingsAccountTransactionId(savingsTransactionId);
        for (final SavingsAccountChannelLimitConsumption row : rows) {
            final Long usageId = row.getUsage() == null ? null : row.getUsage().getId();
            final SavingsAccountChannelLimitUsage usage = usageId == null ? row.getUsage()
                    : this.usageRepository.findOneForUpdate(usageId).orElse(row.getUsage());
            if (usage != null) {
                usage.release(row.getAmount(), row.getTransactionCount() == null ? 0 : row.getTransactionCount());
                this.usageRepository.save(usage);
            }
        }
        if (!rows.isEmpty()) {
            this.consumptionRepository.deleteAll(rows);
        }
    }

    @Transactional
    public List<SavingsAccountChannelLimitData> retrieve(final Long savingsAccountId, final Long productPaymentChannelId,
            final String directionCode) {
        final SavingsAccount account = this.savingsAccountRepository.findOneWithNotFoundDetection(savingsAccountId);
        final SavingsProductPaymentChannel channel = requireChannelOnAccount(account, productPaymentChannelId);
        final List<SavingsChannelLimitDirection> directions = new ArrayList<>();
        if (directionCode == null || directionCode.isBlank()) {
            directions.add(SavingsChannelLimitDirection.DEBIT);
            directions.add(SavingsChannelLimitDirection.CREDIT);
        } else {
            final SavingsChannelLimitDirection direction = SavingsChannelLimitDirection.fromCode(directionCode);
            if (direction == null) {
                throw validationException("direction", directionCode, "invalid");
            }
            directions.add(direction);
        }
        final LocalDate today = DateUtils.getBusinessLocalDate();
        final List<SavingsAccountChannelLimitData> result = new ArrayList<>();
        for (final SavingsChannelLimitDirection direction : directions) {
            result.add(toData(account, channel, direction, today));
        }
        return result;
    }

    @Transactional
    public CommandProcessingResult update(final Long savingsAccountId, final JsonCommand command) {
        final AppUser user = this.context.authenticatedUser();
        final Long paymentTypeId = command.longValueOfParameterNamed("paymentTypeId");
        if (paymentTypeId == null) {
            throw validationException("paymentTypeId", null, "parameter.mandatory");
        }
        final String directionCode = command.stringValueOfParameterNamed("direction");
        final SavingsChannelLimitDirection direction = SavingsChannelLimitDirection.fromCode(directionCode);
        if (direction == null) {
            throw validationException("direction", directionCode, "invalid");
        }
        final SavingsAccount account = this.savingsAccountRepository.findOneWithNotFoundDetection(savingsAccountId);
        final boolean selfService = user != null && user.getId() != null
                && this.selfServiceUserClientMappingRepository.existsByAppUser_Id(user.getId());
        if (selfService && (account.clientId() == null
                || !this.selfServiceUserClientMappingRepository.existsByAppUser_IdAndClient_Id(user.getId(), account.clientId()))) {
            throw validationException("savingsAccountId", savingsAccountId, "not.mapped.to.client");
        }
        final SavingsProductPaymentChannel channel = this.productChannelRepository
                .findByProductIdAndPaymentTypeId(account.productId(), paymentTypeId)
                .orElseThrow(() -> validationException("paymentTypeId", paymentTypeId, "not.in.product.channel.catalog"));
        final SavingsChannelLimitValues ceiling = channel.limitsFor(direction);
        final Optional<SavingsAccountChannelLimit> existing = this.limitRepository
                .findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(account.getId(), channel.getId(), direction);
        final OffsetDateTime now = DateUtils.getAuditOffsetDateTime();
        if (existing.isPresent() && existing.get().applyPendingIfDue(now)) {
            this.limitRepository.save(existing.get());
        }
        final SavingsChannelLimitValues current = existing.map(SavingsAccountChannelLimit::liveValues).orElse(SavingsChannelLimitValues.none());
        final SavingsChannelLimitValues proposed = merge(current, command);
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(LIMIT_RESOURCE);
        SavingsChannelLimitRules.validateNonNegative(proposed.maxPerTxn(), validator, "maxPerTxn");
        SavingsChannelLimitRules.validateNonNegative(proposed.maxPerDay(), validator, "maxPerDay");
        SavingsChannelLimitRules.validateNonNegative(proposed.maxPerMonth(), validator, "maxPerMonth");
        SavingsChannelLimitRules.validateNonNegative(proposed.maxCountPerDay(), validator, "maxCountPerDay");
        SavingsChannelLimitRules.validateNonNegative(proposed.maxCountPerMonth(), validator, "maxCountPerMonth");
        SavingsChannelLimitRules.validateOrdering(proposed, validator, "max");
        SavingsChannelLimitRules.validateWithinCeiling(proposed, ceiling, validator, "max");
        SavingsChannelLimitRules.validateOrdering(SavingsChannelLimitRules.effective(proposed, ceiling), validator, "max");
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }

        final Long userId = user == null ? null : user.getId();
        final SavingsAccountChannelLimit row = existing.orElseGet(
                () -> SavingsAccountChannelLimit.create(account, channel, direction, userId, now));
        final int coolingHours = this.configurationDomainService.retrieveChannelLimitIncreaseCoolingHours();
        if (selfService) {
            row.applySelfService(proposed, coolingHours, userId, now);
        } else {
            row.replaceImmediately(proposed, userId, now);
        }
        this.limitRepository.save(row);
        return new CommandProcessingResultBuilder().withEntityId(row.getId()).withSavingsId(savingsAccountId).withClientId(account.clientId())
                .withOfficeId(account.officeId()).withGroupId(account.groupId()).build();
    }

    private SavingsAccountChannelLimitData toData(final SavingsAccount account, final SavingsProductPaymentChannel channel,
            final SavingsChannelLimitDirection direction, final LocalDate today) {
        final Optional<SavingsAccountChannelLimit> customerLimit = this.limitRepository
                .findBySavingsAccountIdAndProductPaymentChannelIdAndDirection(account.getId(), channel.getId(), direction);
        if (customerLimit.isPresent() && customerLimit.get().applyPendingIfDue(DateUtils.getAuditOffsetDateTime())) {
            this.limitRepository.save(customerLimit.get());
        }
        final SavingsChannelLimitValues ceiling = channel.limitsFor(direction);
        final SavingsChannelLimitValues live = customerLimit.map(SavingsAccountChannelLimit::liveValues).orElse(SavingsChannelLimitValues.none());
        final SavingsChannelLimitValues pending = customerLimit.map(SavingsAccountChannelLimit::pendingValues).orElse(null);
        final SavingsChannelLimitValues effective = SavingsChannelLimitRules.effective(live, ceiling);
        final SavingsAccountChannelLimitUsage day = this.usageRepository
                .findBySavingsAccountIdAndProductPaymentChannelIdAndDirectionAndPeriodTypeAndPeriodStart(account.getId(), channel.getId(),
                        direction, SavingsChannelLimitPeriodType.DAY, SavingsChannelLimitPeriodType.DAY.periodStart(today))
                .orElse(null);
        final SavingsAccountChannelLimitUsage month = this.usageRepository
                .findBySavingsAccountIdAndProductPaymentChannelIdAndDirectionAndPeriodTypeAndPeriodStart(account.getId(), channel.getId(),
                        direction, SavingsChannelLimitPeriodType.MONTH, SavingsChannelLimitPeriodType.MONTH.periodStart(today))
                .orElse(null);
        final BigDecimal usedToday = day == null ? BigDecimal.ZERO : day.getAmountUsed();
        final int countToday = day == null ? 0 : day.getCountUsed();
        final BigDecimal usedMonth = month == null ? BigDecimal.ZERO : month.getAmountUsed();
        final int countMonth = month == null ? 0 : month.getCountUsed();
        final SavingsAccountChannelLimit row = customerLimit.orElse(null);
        return SavingsAccountChannelLimitData.builder().id(row == null ? null : row.getId()).savingsAccountId(account.getId())
                .productPaymentChannelId(channel.getId()).paymentTypeId(channel.getPaymentType().getId()).direction(direction.name())
                .ceilingPerTxn(ceiling.maxPerTxn()).ceilingPerDay(ceiling.maxPerDay()).ceilingPerMonth(ceiling.maxPerMonth())
                .ceilingCountPerDay(ceiling.maxCountPerDay()).ceilingCountPerMonth(ceiling.maxCountPerMonth()).maxPerTxn(live.maxPerTxn())
                .maxPerDay(live.maxPerDay()).maxPerMonth(live.maxPerMonth()).maxCountPerDay(live.maxCountPerDay())
                .maxCountPerMonth(live.maxCountPerMonth()).pendingMaxPerTxn(pending == null ? null : pending.maxPerTxn())
                .pendingMaxPerDay(pending == null ? null : pending.maxPerDay()).pendingMaxPerMonth(pending == null ? null : pending.maxPerMonth())
                .pendingMaxCountPerDay(pending == null ? null : pending.maxCountPerDay())
                .pendingMaxCountPerMonth(pending == null ? null : pending.maxCountPerMonth())
                .pendingEffectiveOn(row == null ? null : row.getPendingEffectiveOn()).effectivePerTxn(effective.maxPerTxn())
                .effectivePerDay(effective.maxPerDay()).effectivePerMonth(effective.maxPerMonth())
                .effectiveCountPerDay(effective.maxCountPerDay()).effectiveCountPerMonth(effective.maxCountPerMonth()).usedToday(usedToday)
                .countToday(countToday).usedThisMonth(usedMonth).countThisMonth(countMonth)
                .remainingToday(SavingsChannelLimitRules.remaining(effective.maxPerDay(), usedToday))
                .remainingThisMonth(SavingsChannelLimitRules.remaining(effective.maxPerMonth(), usedMonth))
                .remainingCountToday(SavingsChannelLimitRules.remaining(effective.maxCountPerDay(), countToday))
                .remainingCountThisMonth(SavingsChannelLimitRules.remaining(effective.maxCountPerMonth(), countMonth)).build();
    }

    private SavingsChannelLimitValues merge(final SavingsChannelLimitValues current, final JsonCommand command) {
        return new SavingsChannelLimitValues(mergeAmount(command, "maxPerTxn", current.maxPerTxn()),
                mergeAmount(command, "maxPerDay", current.maxPerDay()), mergeAmount(command, "maxPerMonth", current.maxPerMonth()),
                mergeCount(command, "maxCountPerDay", current.maxCountPerDay()),
                mergeCount(command, "maxCountPerMonth", current.maxCountPerMonth()));
    }

    private BigDecimal mergeAmount(final JsonCommand command, final String parameter, final BigDecimal current) {
        if (!command.parameterExists(parameter)) {
            return current;
        }
        return command.bigDecimalValueOfParameterNamed(parameter);
    }

    private Integer mergeCount(final JsonCommand command, final String parameter, final Integer current) {
        if (!command.parameterExists(parameter)) {
            return current;
        }
        return command.integerValueOfParameterNamed(parameter);
    }

    private SavingsProductPaymentChannel resolveChannel(final SavingsChannelLimitCommand command) {
        final PaymentDetail paymentDetail = command.paymentDetail();
        if (paymentDetail != null && paymentDetail.getPaymentType() != null && paymentDetail.getPaymentType().getId() != null) {
            return this.productChannelRepository
                    .findByProductIdAndPaymentTypeId(command.account().productId(), paymentDetail.getPaymentType().getId()).orElse(null);
        }
        if (!command.accountTransfer()) {
            return null;
        }
        final List<SavingsProductPaymentChannel> transferChannels = this.productChannelRepository
                .findByProductIdAndAccountTransferChannelTrueAndActiveTrue(command.account().productId());
        return transferChannels.isEmpty() ? null : transferChannels.get(0);
    }

    private SavingsAccountChannelLimitUsage lockUsage(final SavingsAccount account, final SavingsProductPaymentChannel channel,
            final SavingsChannelLimitDirection direction, final SavingsChannelLimitPeriodType periodType, final LocalDate periodStart) {
        final Optional<SavingsAccountChannelLimitUsage> existing = this.usageRepository.findForUpdate(account.getId(), channel.getId(),
                direction, periodType, periodStart);
        if (existing.isPresent()) {
            return existing.get();
        }
        final SavingsAccountChannelLimitUsage created = SavingsAccountChannelLimitUsage.start(account, channel, direction, periodType,
                periodStart);
        try {
            return this.usageRepository.saveAndFlush(created);
        } catch (final DataIntegrityViolationException ex) {
            return this.usageRepository.findForUpdate(account.getId(), channel.getId(), direction, periodType, periodStart)
                    .orElseThrow(() -> ex);
        }
    }

    private SavingsProductPaymentChannel requireChannelOnAccount(final SavingsAccount account, final Long productPaymentChannelId) {
        final SavingsProductPaymentChannel channel = this.productChannelRepository.findById(productPaymentChannelId)
                .orElseThrow(() -> validationException("productPaymentChannelId", productPaymentChannelId, "not.found"));
        if (channel.getProduct() == null || channel.getProduct().getId() == null
                || !channel.getProduct().getId().equals(account.productId())) {
            throw validationException("productPaymentChannelId", productPaymentChannelId, "not.in.product.channel.catalog");
        }
        return channel;
    }

    private static PlatformApiDataValidationException limitExceeded(final String parameter, final Object value, final String code) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(TRANSACTION_RESOURCE);
        validator.reset().parameter(parameter).value(value).failWithCode(code);
        return new PlatformApiDataValidationException(errors);
    }

    private static PlatformApiDataValidationException validationException(final String parameter, final Object value, final String code) {
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder validator = new DataValidatorBuilder(errors).resource(LIMIT_RESOURCE);
        validator.reset().parameter(parameter).value(value).failWithCode(code);
        return new PlatformApiDataValidationException(errors);
    }
}
