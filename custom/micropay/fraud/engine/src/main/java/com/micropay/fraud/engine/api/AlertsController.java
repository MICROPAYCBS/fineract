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
package com.micropay.fraud.engine.api;

import com.micropay.fraud.engine.alert.AlertData;
import com.micropay.fraud.engine.alert.AlertFilter;
import com.micropay.fraud.engine.alert.AlertService;
import com.micropay.fraud.engine.alert.AlertSort;
import com.micropay.fraud.engine.alert.AlertStatus;
import com.micropay.fraud.engine.alert.AlertType;
import com.micropay.fraud.engine.alert.AlertUpdate;
import com.micropay.fraud.engine.scoring.Severity;
import jakarta.validation.Valid;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alert queue for the fraud dashboard. Requires the X-API-Key header (see {@link ApiKeyFilter}).
 */
@RestController
@RequestMapping("/api/v1/alerts")
@RequiredArgsConstructor
public class AlertsController {

    private final AlertService alertService;

    @GetMapping
    public AlertService.Page list(@RequestParam(required = false) String tenantId, @RequestParam(required = false) AlertType type,
            @RequestParam(required = false) AlertStatus status, @RequestParam(required = false) Severity severity,
            @RequestParam(required = false) Long clientId, @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false) Instant from, @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "SCORE") AlertSort sort, @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "50") int limit) {
        return alertService.list(new AlertFilter(tenantId, type, status, severity, clientId, from, to, assignedTo), sort, offset, limit);
    }

    @GetMapping("/{id}")
    public AlertData get(@PathVariable Long id) {
        return alertService.get(id);
    }

    @PatchMapping("/{id}")
    public AlertData update(@PathVariable Long id, @Valid @RequestBody AlertUpdate update) {
        return alertService.update(id, update);
    }
}
