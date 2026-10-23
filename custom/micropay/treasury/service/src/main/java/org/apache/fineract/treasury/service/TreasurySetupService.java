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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.data.ApiParameterError;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResult;
import org.apache.fineract.infrastructure.core.data.CommandProcessingResultBuilder;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.treasury.data.TreasurySetupData;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.domain.TreasuryLimitRepository;
import org.apache.fineract.treasury.domain.TreasuryRuleSet;
import org.apache.fineract.treasury.domain.TreasuryRuleSetRepository;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;
import org.apache.fineract.treasury.limits.LimitStatus;
import org.apache.fineract.treasury.liquidity.ConfigStatus;
import org.apache.fineract.treasury.setup.ClientType;
import org.apache.fineract.treasury.setup.TreasuryRoles;
import org.apache.fineract.treasury.setup.TreasuryRuleSetActivationHook;
import org.apache.fineract.treasury.setup.TreasuryTemplates;
import org.apache.fineract.useradministration.domain.Permission;
import org.apache.fineract.useradministration.domain.PermissionRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Onboarding a tenant by client type: {@code apply-template} sets the client type, puts the type's regulatory rule set
 * in place (the default when active, else an editable draft to map to the chart of accounts), creates its regulatory
 * limits, the default treasury roles and, optionally, maker-checker on every treasury write. The regulatory limits are
 * active exactly when the template rule set is: at once when it already is, else when it is activated. Every step is
 * skipped when done already, so applying again changes nothing.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasurySetupService implements TreasuryRuleSetActivationHook {

    static final String ENTITY = "setup";
    static final String CLIENT_TYPE_PARAM = "clientType";
    static final String MAKER_CHECKER_PARAM = "enableMakerChecker";

    private final TreasuryTenantSettingService tenantSettingService;
    private final TreasuryRuleSetService ruleSetService;
    private final TreasuryRuleSetRepository ruleSetRepository;
    private final TreasuryLimitService limitService;
    private final TreasuryLimitRepository limitRepository;
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final TreasuryActor actor;
    private final FromJsonHelper fromJsonHelper;
    private final JdbcTemplate jdbcTemplate;

    @Transactional
    public CommandProcessingResult applyTemplate(final JsonCommand command) {
        final JsonObject body = JsonParser.parseString(command.json()).getAsJsonObject();
        this.fromJsonHelper.checkForUnsupportedParameters(body, java.util.Set.of(CLIENT_TYPE_PARAM, MAKER_CHECKER_PARAM));
        final ClientType type = clientType(body);
        final boolean makerChecker = !body.has(MAKER_CHECKER_PARAM) || body.get(MAKER_CHECKER_PARAM).isJsonNull()
                || body.get(MAKER_CHECKER_PARAM).getAsBoolean();
        final String template = TreasuryTemplates.ruleSet(type).orElseThrow(() -> new TreasuryWorkflowException(ENTITY,
                "template.not.available", "There is no rule set template for " + type + " (" + type.ruleSetCode() + ") yet", type.name()));
        final Long userId = this.actor.userId(command);
        final Map<String, Object> changes = new LinkedHashMap<>();
        changes.put(CLIENT_TYPE_PARAM, type.name());
        this.tenantSettingService.setClientType(type, userId);

        // the rule set: the default when active, else a draft holding the template
        final List<TreasuryRuleSet> versions = this.ruleSetRepository.findByCodeOrderByVersionAsc(type.ruleSetCode());
        final Optional<TreasuryRuleSet> active = versions.stream().filter(v -> v.getStatus() == ConfigStatus.ACTIVE).findFirst();
        final Optional<TreasuryRuleSet> draft = versions.stream().filter(v -> v.getStatus() == ConfigStatus.DRAFT).findFirst();
        final String ruleSetAction;
        final Long ruleSetId;
        if (active.isPresent()) {
            ruleSetId = active.get().getId();
            ruleSetAction = active.get().isDefaultSet() ? "ALREADY_DEFAULT" : "MADE_DEFAULT";
            this.ruleSetService.makeDefault(ruleSetId);
        } else if (draft.isPresent()) {
            ruleSetId = draft.get().getId();
            if (draft.get().getCreatedBy() == null && draft.get().getLastEditedBy() == null) {
                // the seeded draft, never edited: completed from the template
                final JsonObject update = JsonParser.parseString(template).getAsJsonObject();
                update.remove("code");
                this.ruleSetService.update(ruleSetId, sub(command, update.toString()));
                ruleSetAction = "DRAFT_COMPLETED_FROM_TEMPLATE";
            } else {
                ruleSetAction = "DRAFT_KEPT";
            }
        } else {
            ruleSetId = this.ruleSetService.create(sub(command, template)).getResourceId();
            ruleSetAction = "DRAFT_CREATED";
        }
        final Map<String, Object> ruleSet = new LinkedHashMap<>();
        ruleSet.put("id", ruleSetId);
        ruleSet.put("code", type.ruleSetCode());
        ruleSet.put("action", ruleSetAction);
        changes.put("ruleSet", ruleSet);

        // the regulatory limits, inactive unless the rule set is active
        final List<String> created = new ArrayList<>();
        for (final String limit : TreasuryTemplates.limits(type)) {
            final String code = JsonParser.parseString(limit).getAsJsonObject().get("code").getAsString();
            if (this.limitRepository.findByCode(code).isEmpty()) {
                this.limitService.create(sub(command, limit));
                created.add(code);
            }
        }
        changes.put("limitsCreated", created);
        changes.put("limitsActivated", active.isPresent() ? activateRegulatoryLimits(type) : List.of());

        // the default roles, and maker-checker on the treasury writes
        changes.put("rolesCreated", createRoles(type));
        if (type == ClientType.PSP) {
            changes.put("limitsNotifying", notifyTrustOfficer(command, type));
        }
        changes.put("makerCheckerEnabled", makerChecker ? enableMakerChecker() : 0);
        return new CommandProcessingResultBuilder().withCommandId(command.commandId()).withEntityId(ruleSetId).with(changes).build();
    }

    /**
     * The template rule set of the tenant's client type, activated: the default, and the type's regulatory limits
     * active.
     */
    @Override
    public void activated(final Long ruleSetId, final String code) {
        final Optional<ClientType> type = this.tenantSettingService.clientType();
        if (type.isPresent() && type.get().ruleSetCode().equals(code)) {
            this.ruleSetService.makeDefault(ruleSetId);
            final List<String> activated = activateRegulatoryLimits(type.get());
            log.info("Treasury template rule set {} activated for {}: now the default; limits activated {}", code, type.get(), activated);
        }
    }

    @Transactional(readOnly = true)
    public List<TreasurySetupData.Template> templates() {
        final List<TreasurySetupData.Template> templates = new ArrayList<>();
        for (final ClientType type : ClientType.values()) {
            final Optional<String> json = TreasuryTemplates.ruleSet(type);
            final JsonObject o = json.map(j -> JsonParser.parseString(j).getAsJsonObject()).orElse(new JsonObject());
            templates.add(new TreasurySetupData.Template(type.name(), type.ruleSetCode(), text(o, "name"), text(o, "returnFrequency"),
                    text(o, "description"), json.isPresent(), type.regulatoryLimits()));
        }
        return templates;
    }

    /** Onboarding progress: what is set up and what is still to do. */
    @Transactional(readOnly = true)
    public TreasurySetupData checklist() {
        final Optional<ClientType> type = this.tenantSettingService.clientType();
        final List<TreasurySetupData.Item> items = new ArrayList<>();
        items.add(item("CLIENT_TYPE", "Client type set (apply-template)", type.isPresent(), type.map(Enum::name).orElse("not set")));
        if (type.isPresent()) {
            final String code = type.get().ruleSetCode();
            final List<TreasuryRuleSet> versions = this.ruleSetRepository.findByCodeOrderByVersionAsc(code);
            final Optional<TreasuryRuleSet> active = versions.stream().filter(v -> v.getStatus() == ConfigStatus.ACTIVE).findFirst();
            items.add(item("TEMPLATE_RULE_SET", "Template rule set " + code + " in place", !versions.isEmpty(), versions.isEmpty()
                    ? "missing: apply the template"
                    : versions.getLast().getCode() + " v" + versions.getLast().getVersion() + " " + versions.getLast().getStatus()));
            final Long mappingSet = active.map(TreasuryRuleSet::getId).orElse(versions.isEmpty() ? null : versions.getLast().getId());
            final List<String> unmapped = mappingSet == null ? List.of() : unmappedComponents(mappingSet);
            items.add(item("GL_MAPPING", "GL components mapped and the rule set activated", active.isPresent() && unmapped.isEmpty(),
                    (active.isPresent() ? "active" : "not active: map the GL components, then another user activates it")
                            + (unmapped.isEmpty() ? "" : "; components matching no GL account: " + String.join(", ", unmapped))));
            items.add(item("DEFAULT_RULE_SET", "The template rule set is the default",
                    active.map(TreasuryRuleSet::isDefaultSet).orElse(false), defaultRuleSet()));
            final List<String> limits = type.get().regulatoryLimits();
            final long activeLimits = limits.stream().map(this.limitRepository::findByCode)
                    .filter(l -> l.isPresent() && l.get().getStatus() == LimitStatus.ACTIVE).count();
            items.add(limits.isEmpty() ? na("REGULATORY_LIMITS", "Regulatory limits active", "none for " + type.get() + " yet")
                    : item("REGULATORY_LIMITS", "Regulatory limits active", activeLimits == limits.size(),
                            activeLimits + " of " + limits.size() + " active (" + String.join(", ", limits) + ")"));
            final long roles = TreasuryRoles.ROLES.stream().filter(r -> !r.pspOnly() || type.get() == ClientType.PSP)
                    .filter(r -> roleNamed(r.name()).isPresent()).count();
            final long wanted = TreasuryRoles.ROLES.stream().filter(r -> !r.pspOnly() || type.get() == ClientType.PSP).count();
            items.add(item("ROLES", "Default treasury roles", roles == wanted, roles + " of " + wanted));
        }
        items.add(count("TREASURY_ACCOUNTS", "Treasury accounts", "SELECT COUNT(1) FROM m_treasury_account WHERE status = 'ACTIVE'"));
        items.add(count("CURRENCY_SETTINGS", "Currency settings (cash in transit GL)",
                "SELECT COUNT(1) FROM m_treasury_setting WHERE cash_in_transit_gl_account_id IS NOT NULL"));
        items.add(count("COUNTERPARTIES", "Counterparties", "SELECT COUNT(1) FROM m_treasury_counterparty WHERE status = 'ACTIVE'"));
        items.add(count("DEAL_TYPES", "Deal types", "SELECT COUNT(1) FROM m_treasury_deal_type"));
        items.add(count("ASSUMPTIONS_ACTIVE", "An active assumption set",
                "SELECT COUNT(1) FROM m_treasury_assumption_set WHERE status = 'ACTIVE'"));
        items.add(count("CORE_CAPITAL", "Core capital recorded", "SELECT COUNT(1) FROM m_treasury_core_capital"));
        final Integer currencies = this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM m_organisation_currency", Integer.class);
        items.add(currencies != null && currencies > 1
                ? count("FX_REVALUATION", "FX revaluation configured",
                        "SELECT COUNT(1) FROM m_treasury_fx_reval_config WHERE active = true")
                : na("FX_REVALUATION", "FX revaluation configured", "one currency"));
        final Integer writes = this.jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM m_permission WHERE grouping IN ('treasury', 'treasury_risk', 'treasury_fx', 'treasury_trust')"
                        + " AND action_name <> 'READ' AND action_name NOT LIKE '%_CHECKER'",
                Integer.class);
        final Integer checked = this.jdbcTemplate
                .queryForObject(
                        "SELECT COUNT(1) FROM m_permission WHERE grouping IN ('treasury', 'treasury_risk', 'treasury_fx', 'treasury_trust')"
                                + " AND action_name <> 'READ' AND action_name NOT LIKE '%_CHECKER' AND can_maker_checker = true",
                        Integer.class);
        items.add(item("MAKER_CHECKER", "Maker-checker on the treasury writes", checked != null && checked.equals(writes),
                checked + " of " + writes));
        final Integer jobs = this.jdbcTemplate.queryForObject("SELECT COUNT(1) FROM job WHERE short_name LIKE 'TRS%'", Integer.class);
        final Integer activeJobs = this.jdbcTemplate
                .queryForObject("SELECT COUNT(1) FROM job WHERE short_name LIKE 'TRS%' AND is_active = true", Integer.class);
        items.add(item("JOBS_ACTIVE", "Treasury jobs active", activeJobs != null && activeJobs.equals(jobs), activeJobs + " of " + jobs));
        final int done = (int) items.stream().filter(i -> "DONE".equals(i.status())).count();
        final int total = (int) items.stream().filter(i -> !"NOT_APPLICABLE".equals(i.status())).count();
        return new TreasurySetupData(type.map(Enum::name).orElse(null), type.map(ClientType::ruleSetCode).orElse(null), done, total, items);
    }

    // ---------- steps ----------

    private List<String> activateRegulatoryLimits(final ClientType type) {
        final List<String> activated = new ArrayList<>();
        for (final String code : type.regulatoryLimits()) {
            final Optional<TreasuryLimit> limit = this.limitRepository.findByCode(code);
            if (limit.isPresent() && limit.get().getStatus() != LimitStatus.ACTIVE) {
                this.limitService.activate(limit.get().getId(), JsonCommand.from("{}"));
                activated.add(code);
            }
        }
        return activated;
    }

    private List<String> createRoles(final ClientType type) {
        final List<TreasuryRoles.Permission> permissions = this.permissionRepository.findAll().stream()
                .map(p -> new TreasuryRoles.Permission(p.getGrouping(), p.getCode(), entity(p.getCode()), action(p.getCode()))).toList();
        final List<String> created = new ArrayList<>();
        for (final TreasuryRoles.Role spec : TreasuryRoles.ROLES) {
            if (spec.pspOnly() && type != ClientType.PSP || roleNamed(spec.name()).isPresent()) {
                continue;
            }
            final Role role = new Role(spec.name(), spec.description());
            for (final TreasuryRoles.Permission p : permissions) {
                if (TreasuryRoles.grants(spec, p)) {
                    role.updatePermission(this.permissionRepository.findOneByCode(p.code()), true);
                }
            }
            this.roleRepository.saveAndFlush(role);
            created.add(spec.name());
        }
        return created;
    }

    /** The trust limits without a role to notify notify the Trust and reconciliation officer (reg 24: at once). */
    private List<String> notifyTrustOfficer(final JsonCommand command, final ClientType type) {
        final Optional<Role> officer = roleNamed("Trust and reconciliation officer");
        final List<String> updated = new ArrayList<>();
        if (officer.isEmpty()) {
            return updated;
        }
        for (final String code : type.regulatoryLimits()) {
            final Optional<TreasuryLimit> limit = this.limitRepository.findByCode(code);
            if (limit.isPresent() && limit.get().getNotifyRoleId() == null) {
                this.limitService.update(limit.get().getId(), sub(command, "{\"notifyRoleId\": " + officer.get().getId() + "}"));
                updated.add(code);
            }
        }
        return updated;
    }

    private int enableMakerChecker() {
        int enabled = 0;
        for (final Permission p : this.permissionRepository.findAll()) {
            final String action = action(p.getCode());
            if (TreasuryRoles.GROUPINGS.contains(p.getGrouping()) && !"READ".equals(action) && !action.endsWith("_CHECKER")
                    && !p.hasMakerCheckerEnabled()) {
                p.enableMakerChecker(true);
                this.permissionRepository.save(p);
                enabled++;
            }
        }
        this.permissionRepository.flush();
        return enabled;
    }

    // ---------- helpers ----------

    private ClientType clientType(final JsonObject body) {
        final JsonElement value = body.get(CLIENT_TYPE_PARAM);
        final String text = value == null || value.isJsonNull() ? null : value.getAsString().trim().toUpperCase();
        final List<ApiParameterError> errors = new ArrayList<>();
        final DataValidatorBuilder v = new DataValidatorBuilder(errors).resource("TREASURYSETUP");
        v.reset().parameter(CLIENT_TYPE_PARAM).value(text).notBlank()
                .isOneOfTheseStringValues(java.util.Arrays.stream(ClientType.values()).map(Enum::name).toList());
        if (!errors.isEmpty()) {
            throw new PlatformApiDataValidationException(errors);
        }
        return ClientType.valueOf(text);
    }

    /** A step of the apply as a command of its own, credited to the maker of the apply. */
    private JsonCommand sub(final JsonCommand command, final String json) {
        return JsonCommand.fromExistingCommand(command.commandId(), json, this.fromJsonHelper.parse(json), this.fromJsonHelper, null, null,
                null, null, null, null, null, null, null);
    }

    private Optional<Role> roleNamed(final String name) {
        return this.roleRepository.findAll().stream().filter(r -> name.equals(r.getName())).findFirst();
    }

    private List<String> unmappedComponents(final Long ruleSetId) {
        return this.jdbcTemplate.queryForList("""
                SELECT DISTINCT c.label FROM m_treasury_ratio_component c JOIN m_treasury_ratio_definition r ON r.id = c.ratio_id
                WHERE r.rule_set_id = ? AND c.component_type = 'GL_ACCOUNT' AND NOT EXISTS (
                    SELECT 1 FROM acc_gl_account g WHERE g.id = c.gl_account_id
                       OR (c.gl_code_prefix IS NOT NULL AND g.gl_code LIKE CONCAT(c.gl_code_prefix, '%'))
                       OR (c.gl_code_from IS NOT NULL AND g.gl_code BETWEEN c.gl_code_from AND c.gl_code_to))
                ORDER BY c.label
                """, String.class, ruleSetId);
    }

    private String defaultRuleSet() {
        return this.ruleSetRepository.findAll().stream().filter(TreasuryRuleSet::isDefaultSet).findFirst()
                .map(s -> s.getCode() + " v" + s.getVersion()).orElse("none");
    }

    private TreasurySetupData.Item count(final String code, final String label, final String sql) {
        final Integer n = this.jdbcTemplate.queryForObject(sql, Integer.class);
        return item(code, label, n != null && n > 0, String.valueOf(n));
    }

    private static TreasurySetupData.Item item(final String code, final String label, final boolean done, final String detail) {
        return new TreasurySetupData.Item(code, label, done ? "DONE" : "TODO", detail);
    }

    private static TreasurySetupData.Item na(final String code, final String label, final String detail) {
        return new TreasurySetupData.Item(code, label, "NOT_APPLICABLE", detail);
    }

    private static String text(final JsonObject o, final String name) {
        return o.has(name) && !o.get(name).isJsonNull() ? o.get(name).getAsString() : null;
    }

    /** The action of a permission code such as {@code APPROVE_TREASURYDEAL_CHECKER}: {@code APPROVE_CHECKER}. */
    static String action(final String code) {
        final int i = code.indexOf('_');
        if (i < 0) {
            return code;
        }
        final String action = code.substring(0, i);
        return code.endsWith("_CHECKER") ? action + "_CHECKER"
                : code.equals("APPROVE_TREASURYDEAL_OVERLIMIT") ? "APPROVE_OVERLIMIT" : action;
    }

    /** The entity of a permission code: {@code TREASURYDEAL}. */
    static String entity(final String code) {
        final int i = code.indexOf('_');
        if (i < 0) {
            return code;
        }
        final String rest = code.substring(i + 1);
        final String entity = rest.endsWith("_CHECKER") ? rest.substring(0, rest.length() - "_CHECKER".length()) : rest;
        return entity.equals("TREASURYDEAL_OVERLIMIT") ? "TREASURYDEAL" : entity;
    }
}
