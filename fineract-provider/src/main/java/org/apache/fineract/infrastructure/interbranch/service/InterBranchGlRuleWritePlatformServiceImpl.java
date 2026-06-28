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
package org.apache.fineract.infrastructure.interbranch.service;

import com.google.gson.JsonObject;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.apache.fineract.accounting.glaccount.domain.GLAccount;
import org.apache.fineract.accounting.glaccount.domain.GLAccountRepositoryWrapper;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.infrastructure.interbranch.domain.InterBranchGlRule;
import org.apache.fineract.infrastructure.interbranch.domain.InterBranchGlRuleRepository;
import org.apache.fineract.infrastructure.interbranch.exception.InterBranchGlRuleNotFoundException;
import org.apache.fineract.infrastructure.interbranch.serialization.InterBranchGlRuleCommandFromApiJsonDeserializer;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.organisation.office.domain.Office;
import org.apache.fineract.organisation.office.domain.OfficeRepositoryWrapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterBranchGlRuleWritePlatformServiceImpl implements InterBranchGlRuleWritePlatformService {

    private final PlatformSecurityContext context;
    private final InterBranchGlRuleRepository interBranchGlRuleRepository;
    private final OfficeRepositoryWrapper officeRepository;
    private final GLAccountRepositoryWrapper glAccountRepository;
    private final InterBranchGlRuleCommandFromApiJsonDeserializer apiJsonDeserializer;
    private final FromJsonHelper fromApiJsonHelper;

    @Override
    @Transactional
    public CommandProcessingResult createInterBranchGlRule(final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForCreate(command.json());

        final JsonObject json = command.parsedJson().getAsJsonObject();
        final InterBranchGlRule rule = mapFromJson(new InterBranchGlRule(), json, true);
        validateRuleOffices(rule);
        validateDefaultRuleUniqueness(rule, null);
        this.interBranchGlRuleRepository.saveAndFlush(rule);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(rule.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult updateInterBranchGlRule(final Long ruleId, final JsonCommand command) {
        this.context.authenticatedUser();
        this.apiJsonDeserializer.validateForUpdate(ruleId, command.json());

        final InterBranchGlRule rule = findWithNotFoundDetection(ruleId);
        final JsonObject json = command.parsedJson().getAsJsonObject();
        mapFromJson(rule, json, false);
        validateRuleOffices(rule);
        validateDefaultRuleUniqueness(rule, ruleId);
        this.interBranchGlRuleRepository.saveAndFlush(rule);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(rule.getId()).build();
    }

    @Override
    @Transactional
    public CommandProcessingResult deleteInterBranchGlRule(final Long ruleId, final JsonCommand command) {
        this.context.authenticatedUser();
        final InterBranchGlRule rule = findWithNotFoundDetection(ruleId);
        this.interBranchGlRuleRepository.delete(rule);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(ruleId).build();
    }

    @Override
    public InterBranchGlRule findWithNotFoundDetection(final Long ruleId) {
        return this.interBranchGlRuleRepository.findById(ruleId).orElseThrow(() -> new InterBranchGlRuleNotFoundException(ruleId));
    }

    private InterBranchGlRule mapFromJson(final InterBranchGlRule rule, final JsonObject json, final boolean create) {
        if (create || json.has(InterBranchGlRuleCommandFromApiJsonDeserializer.LEFT_OFFICE_ID)) {
            final Long leftOfficeId = this.fromApiJsonHelper.extractLongNamed(InterBranchGlRuleCommandFromApiJsonDeserializer.LEFT_OFFICE_ID,
                    json);
            rule.setLeftOffice(resolveOffice(leftOfficeId));
        }
        if (create || json.has(InterBranchGlRuleCommandFromApiJsonDeserializer.RIGHT_OFFICE_ID)) {
            final Long rightOfficeId = this.fromApiJsonHelper
                    .extractLongNamed(InterBranchGlRuleCommandFromApiJsonDeserializer.RIGHT_OFFICE_ID, json);
            rule.setRightOffice(resolveOffice(rightOfficeId));
        }
        if (create || json.has(InterBranchGlRuleCommandFromApiJsonDeserializer.GL_ACCOUNT_ID)) {
            final Long glAccountId = this.fromApiJsonHelper.extractLongNamed(InterBranchGlRuleCommandFromApiJsonDeserializer.GL_ACCOUNT_ID,
                    json);
            final GLAccount glAccount = this.glAccountRepository.findOneWithNotFoundDetection(glAccountId);
            rule.setGlAccount(glAccount);
        }
        if (json.has(InterBranchGlRuleCommandFromApiJsonDeserializer.CURRENCY_CODE)) {
            rule.setCurrencyCode(this.fromApiJsonHelper.extractStringNamed(InterBranchGlRuleCommandFromApiJsonDeserializer.CURRENCY_CODE,
                    json));
        }
        if (create || json.has(InterBranchGlRuleCommandFromApiJsonDeserializer.STATUS)) {
            final String status = this.fromApiJsonHelper.extractStringNamed(InterBranchGlRuleCommandFromApiJsonDeserializer.STATUS, json);
            if (StringUtils.isNotBlank(status)) {
                rule.setStatus(status);
            }
        }
        return rule;
    }

    private Office resolveOffice(final Long officeId) {
        if (officeId == null) {
            return null;
        }
        return this.officeRepository.findOneWithNotFoundDetection(officeId);
    }

    private void validateRuleOffices(final InterBranchGlRule rule) {
        if (rule.getLeftOffice() != null && rule.getRightOffice() != null
                && Objects.equals(rule.getLeftOffice().getId(), rule.getRightOffice().getId())) {
            throw new PlatformDataIntegrityException("error.msg.inter.branch.gl.rule.same.office",
                    "Left and right office must differ when both are specified.");
        }
    }

    private void validateDefaultRuleUniqueness(final InterBranchGlRule rule, final Long excludeId) {
        if (rule.getLeftOffice() == null && rule.getRightOffice() == null) {
            final boolean exists = excludeId == null ? this.interBranchGlRuleRepository.existsDefaultRule()
                    : this.interBranchGlRuleRepository.existsDefaultRuleExcludingId(excludeId);
            if (exists) {
                throw new PlatformDataIntegrityException("error.msg.inter.branch.gl.rule.default.duplicate",
                        "A default inter-branch GL rule already exists.");
            }
        }
    }
}
