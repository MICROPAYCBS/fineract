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

import com.micropay.fraud.engine.stats.AlertStats;
import com.micropay.fraud.engine.stats.StatsRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alert figures for the dashboard. The period defaults to the last 30 days. Requires the X-API-Key header.
 */
@RestController
@RequestMapping("/api/v1/stats")
@RequiredArgsConstructor
public class StatsController {

    static final Duration DEFAULT_PERIOD = Duration.ofDays(30);

    private final StatsRepository stats;
    private final Clock clock;

    @GetMapping
    public AlertStats stats(@RequestParam(required = false) String tenantId, @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to) {
        final Instant end = to != null ? to : clock.instant();
        final Instant start = from != null ? from : end.minus(DEFAULT_PERIOD);
        return stats.stats(tenantId, start, end);
    }
}
