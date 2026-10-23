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

import java.util.Collection;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.fineract.notification.service.NotificationWritePlatformService;
import org.apache.fineract.treasury.domain.TreasuryBreach;
import org.apache.fineract.treasury.domain.TreasuryLimit;
import org.apache.fineract.treasury.limits.BreachAction;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Notifies the users of a limit's role of a new or escalated breach through Fineract's in-app notifications (object
 * type {@code treasuryBreach}, the breach id; read with {@code GET /notifications}). A limit without a role notifies
 * nobody; the open breaches are also listed by {@code GET /treasury/breaches?status=OPEN}. Contingency funding plan
 * activations notify their owners the same way.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class TreasuryBreachNotifier {

    static final String OBJECT_TYPE = "treasuryBreach";

    private final ObjectProvider<NotificationWritePlatformService> notifications;
    private final JdbcTemplate jdbcTemplate;

    /** @return how many users were notified */
    public int notify(final TreasuryLimit limit, final TreasuryBreach breach, final BreachAction action, final Long actorId) {
        if (limit.getNotifyRoleId() == null || !action.notifies()) {
            return 0;
        }
        return notifyUsers(roleUsers(limit.getNotifyRoleId()), OBJECT_TYPE, breach.getId(),
                action == BreachAction.CREATED ? "breachCreated" : "breachEscalated", actorId, content(limit, breach, action));
    }

    /** The enabled users of a role. */
    public List<Long> roleUsers(final Long roleId) {
        return roleId == null ? List.of() : this.jdbcTemplate.queryForList("""
                SELECT u.id FROM m_appuser u JOIN m_appuser_role ur ON ur.appuser_id = u.id
                WHERE ur.role_id = ? AND u.is_deleted = false AND u.enabled = true ORDER BY u.id
                """, Long.class, roleId);
    }

    /** An in-app notification to the users; also used for contingency funding plan activations. */
    public int notifyUsers(final Collection<Long> users, final String objectType, final Long objectId, final String action,
            final Long actorId, final String content) {
        final NotificationWritePlatformService service = this.notifications.getIfAvailable();
        if (users.isEmpty() || service == null) {
            return 0;
        }
        service.notify(users, objectType, objectId, action, actorId, content.length() > 500 ? content.substring(0, 500) : content, true);
        return users.size();
    }

    static String content(final TreasuryLimit limit, final TreasuryBreach breach, final BreachAction action) {
        final String text = (action == BreachAction.CREATED ? "New " : "Escalated to ") + breach.getLevel() + " breach of treasury limit "
                + limit.getCode() + " (" + limit.getName() + ") by " + breach.getScopeLabel() + " on " + breach.getLastSeenOn() + ": "
                + (breach.getObservedValue() == null ? "no value" : breach.getObservedValue().stripTrailingZeros().toPlainString())
                + (breach.getHardLevel() == null ? "" : " against " + breach.getHardLevel().stripTrailingZeros().toPlainString())
                + (breach.getUnit() == null ? "" : " " + breach.getUnit().name().toLowerCase());
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}
