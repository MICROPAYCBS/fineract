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
package com.micropay.fraud.engine.alert;

import java.time.Clock;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The analyst side of alerts: the queue, one alert's detail, and updating status and verdict. Every update is kept in
 * the alert's history.
 */
@Service
@RequiredArgsConstructor
public class AlertService {

    private static final int MAX_PAGE_SIZE = 200;

    private final AlertRepository alerts;
    private final Clock clock;

    public record Page(List<AlertData> items, long total, int offset, int limit) {
    }

    public Page list(AlertFilter filter, int offset, int limit) {
        return list(filter, AlertSort.SCORE, offset, limit);
    }

    public Page list(AlertFilter filter, AlertSort sort, int offset, int limit) {
        final int pageSize = Math.max(1, Math.min(limit, MAX_PAGE_SIZE));
        final int start = Math.max(0, offset);
        return new Page(alerts.find(filter, sort == null ? AlertSort.SCORE : sort, start, pageSize), alerts.count(filter), start, pageSize);
    }

    public AlertData get(Long id) {
        return alerts.findById(id).orElseThrow(() -> new AlertNotFoundException(id));
    }

    /**
     * A verdict closes the alert unless the update says otherwise.
     */
    @Transactional
    public AlertData update(Long id, AlertUpdate update) {
        final AlertData current = get(id);
        final AlertVerdict verdict = update.verdict() != null ? update.verdict() : current.verdict();
        AlertStatus status = update.status() != null ? update.status() : current.status();
        if (update.status() == null && update.verdict() != null) {
            status = AlertStatus.CLOSED;
        }
        final String assignedTo = update.assignedTo() != null ? update.assignedTo() : current.assignedTo();
        final String note = update.note() != null ? update.note() : current.note();
        alerts.update(id, status, verdict, assignedTo, note, update.actor(), clock.instant());
        return get(id);
    }
}
