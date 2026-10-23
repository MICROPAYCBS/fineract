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

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.service.DateUtils;
import org.apache.fineract.treasury.data.TreasuryTenantSettingData;
import org.apache.fineract.treasury.domain.TreasuryTenantSetting;
import org.apache.fineract.treasury.domain.TreasuryTenantSettingRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.fx.NopCapitalBasis;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator;
import org.apache.fineract.treasury.serialization.TreasuryFxDataValidator.TenantSettingRequest;
import org.apache.fineract.treasury.setup.ClientType;
import org.apache.fineract.treasury.trust.EMoneyCategory;
import org.apache.fineract.treasury.trust.EMoneySourceType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The tenant's treasury settings (base currency, FX rate age, NOP capital basis) over the deployment defaults. */
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryTenantSettingService {

    static final String ENTITY = "tenant.setting";

    private final TreasuryTenantSettingRepository repository;
    private final TreasuryFxConfig fxConfig;
    private final TreasuryFxDataValidator validator;
    private final TreasuryReferenceDataLookup referenceDataLookup;
    private final TreasuryActor actor;
    private final JdbcTemplate jdbcTemplate;

    @Transactional(readOnly = true)
    public TreasuryTenantSettingData retrieve() {
        final TreasuryTenantSetting s = this.repository.findFirstByOrderByIdAsc().orElseGet(TreasuryTenantSetting::new);
        return new TreasuryTenantSettingData(this.fxConfig.baseCurrency(), s.getBaseCurrency(), this.fxConfig.defaultBaseCurrency(),
                this.fxConfig.maxRateAgeDays(), s.getFxMaxRateAgeDays(), this.fxConfig.defaultMaxRateAgeDays(),
                this.fxConfig.nopCapitalBasis().name(), s.getNopCapitalBasis() == null ? null : s.getNopCapitalBasis().name(),
                NopCapitalBasis.PRECEDING_QUARTER.name(), s.getLastModifiedBy(), s.getLastModifiedOnUtc(),
                s.getClientType() == null ? null : s.getClientType().name(), emoneySource(s).name(), ids(s.getEmoneyCustomerProducts()),
                ids(s.getEmoneyAgentProducts()), ids(s.getEmoneyOtherProducts()), s.getTrustInterestGlAccountId());
    }

    /** Where the tenant's e-money outstanding comes from; Fineract savings unless set. */
    public static EMoneySourceType emoneySource(final TreasuryTenantSetting s) {
        return s.getEmoneySource() == null ? EMoneySourceType.FINERACT_SAVINGS : s.getEmoneySource();
    }

    /** The e-money savings products of each category. */
    public static Map<EMoneyCategory, List<Long>> emoneyProducts(final TreasuryTenantSetting s) {
        final Map<EMoneyCategory, List<Long>> products = new EnumMap<>(EMoneyCategory.class);
        products.put(EMoneyCategory.CUSTOMER, ids(s.getEmoneyCustomerProducts()));
        products.put(EMoneyCategory.AGENT, ids(s.getEmoneyAgentProducts()));
        products.put(EMoneyCategory.OTHER, ids(s.getEmoneyOtherProducts()));
        return products;
    }

    /** The tenant's settings row, or an empty one. */
    @Transactional(readOnly = true)
    public TreasuryTenantSetting setting() {
        return this.repository.findFirstByOrderByIdAsc().orElseGet(TreasuryTenantSetting::new);
    }

    static List<Long> ids(final String list) {
        return list == null || list.isBlank() ? List.of()
                : Arrays.stream(list.split(",")).map(String::trim).filter(x -> !x.isEmpty()).map(Long::valueOf).toList();
    }

    private static String join(final List<Long> ids) {
        return ids == null || ids.isEmpty() ? null : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }

    /** The tenant's client type, if set. */
    @Transactional(readOnly = true)
    public Optional<ClientType> clientType() {
        return this.repository.findFirstByOrderByIdAsc().map(TreasuryTenantSetting::getClientType);
    }

    /** Sets the client type (apply-template); returns the setting row. */
    @Transactional
    public TreasuryTenantSetting setClientType(final ClientType type, final Long userId) {
        final TreasuryTenantSetting setting = this.repository.findFirstByOrderByIdAsc().orElseGet(TreasuryTenantSetting::new);
        setting.setClientType(type);
        setting.setLastModifiedBy(userId);
        setting.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
        return this.repository.saveAndFlush(setting);
    }

    /**
     * Sets what is sent; null puts a setting back to the deployment default. The base currency cannot change while FX
     * revaluations are posted in another one, or core capital is recorded in another one.
     */
    @Transactional
    public CommandProcessingResult update(final JsonCommand command) {
        final TenantSettingRequest request = this.validator.validateTenantSetting(command.json(),
                this.referenceDataLookup.organisationCurrencies());
        final TreasuryTenantSetting setting = this.repository.findFirstByOrderByIdAsc().orElseGet(TreasuryTenantSetting::new);
        final String before = this.fxConfig.baseCurrency();
        final Map<String, Object> changes = new LinkedHashMap<>();
        if (request.baseCurrencySent() && !Objects.equals(request.baseCurrency(), setting.getBaseCurrency())) {
            final String after = request.baseCurrency() == null ? this.fxConfig.defaultBaseCurrency() : request.baseCurrency();
            if (!after.equals(before)) {
                requireBaseCurrencyUnused(before, after);
            }
            setting.setBaseCurrency(request.baseCurrency());
            changes.put("baseCurrency", request.baseCurrency());
        }
        if (request.fxMaxRateAgeDaysSent() && !Objects.equals(request.fxMaxRateAgeDays(), setting.getFxMaxRateAgeDays())) {
            setting.setFxMaxRateAgeDays(request.fxMaxRateAgeDays());
            changes.put("fxMaxRateAgeDays", request.fxMaxRateAgeDays());
        }
        if (request.nopCapitalBasisSent() && request.nopCapitalBasis() != setting.getNopCapitalBasis()) {
            setting.setNopCapitalBasis(request.nopCapitalBasis());
            changes.put("nopCapitalBasis", request.nopCapitalBasis() == null ? null : request.nopCapitalBasis().name());
        }
        if (request.emoneySourceSent() && request.emoneySource() != setting.getEmoneySource()) {
            setting.setEmoneySource(request.emoneySource());
            changes.put("emoneySource", request.emoneySource() == null ? null : request.emoneySource().name());
        }
        for (final Map.Entry<EMoneyCategory, List<Long>> e : request.emoneyProducts().entrySet()) {
            final String joined = join(e.getValue());
            switch (e.getKey()) {
                case CUSTOMER -> setting.setEmoneyCustomerProducts(joined);
                case AGENT -> setting.setEmoneyAgentProducts(joined);
                case OTHER -> setting.setEmoneyOtherProducts(joined);
            }
            changes.put("emoney" + e.getKey().name().charAt(0) + e.getKey().name().substring(1).toLowerCase() + "ProductIds", e.getValue());
        }
        if (!request.emoneyProducts().isEmpty()) {
            requireSavingsProducts(request.emoneyProducts());
        }
        if (request.trustInterestGlSent() && !Objects.equals(request.trustInterestGlAccountId(), setting.getTrustInterestGlAccountId())) {
            if (request.trustInterestGlAccountId() != null
                    && this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM acc_gl_account WHERE id = ?", Integer.class,
                            request.trustInterestGlAccountId()) == 0) {
                throw new TreasuryWorkflowException(ENTITY, "gl.account.not.found",
                        "GL account " + request.trustInterestGlAccountId() + " does not exist", request.trustInterestGlAccountId());
            }
            setting.setTrustInterestGlAccountId(request.trustInterestGlAccountId());
            changes.put("trustInterestGlAccountId", request.trustInterestGlAccountId());
        }
        if (request.clientTypeSent() && request.clientType() != setting.getClientType()) {
            setting.setClientType(request.clientType());
            changes.put("clientType", request.clientType() == null ? null : request.clientType().name());
        }
        if (!changes.isEmpty()) {
            setting.setLastModifiedBy(this.actor.userId(command));
            setting.setLastModifiedOnUtc(DateUtils.getAuditOffsetDateTime());
            this.repository.saveAndFlush(setting);
        }
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(setting.getId()).with(changes).build();
    }

    private void requireSavingsProducts(final Map<EMoneyCategory, List<Long>> products) {
        for (final List<Long> ids : products.values()) {
            for (final Long id : ids) {
                if (this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM m_savings_product WHERE id = ?", Integer.class, id) == 0) {
                    throw new TreasuryWorkflowException(ENTITY, "savings.product.not.found", "Savings product " + id + " does not exist",
                            id);
                }
            }
        }
    }

    private void requireBaseCurrencyUnused(final String before, final String after) {
        final Integer posted = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM m_treasury_fx_revaluation WHERE status = 'POSTED' AND base_currency <> ?", Integer.class, after);
        final Integer capital = this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM m_treasury_core_capital WHERE currency_code <> ?",
                Integer.class, after);
        if (posted != null && posted > 0 || capital != null && capital > 0) {
            throw new TreasuryWorkflowException(ENTITY, "base.currency.in.use", "The base currency cannot change from " + before + " to "
                    + after + ": " + posted + " posted FX revaluations and " + capital + " core capital records are in another currency",
                    before, after);
        }
    }
}
