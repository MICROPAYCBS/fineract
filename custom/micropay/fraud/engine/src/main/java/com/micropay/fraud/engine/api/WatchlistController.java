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

import com.micropay.fraud.engine.event.PartyType;
import com.micropay.fraud.engine.watchlist.ScreeningSubject;
import com.micropay.fraud.engine.watchlist.WatchlistIndex;
import com.micropay.fraud.engine.watchlist.WatchlistMatch;
import com.micropay.fraud.engine.watchlist.WatchlistProvider;
import com.micropay.fraud.engine.watchlist.WatchlistRefresher;
import com.micropay.fraud.engine.watchlist.WatchlistRepository;
import com.micropay.fraud.engine.watchlist.WatchlistService;
import com.micropay.fraud.engine.watchlist.WatchlistSourceStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Watchlist status, a manual refresh, and a name check for onboarding. Requires the X-API-Key header.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/watchlist")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistRepository lists;
    private final WatchlistService screening;
    private final WatchlistRefresher refresher;

    public record Status(List<WatchlistSourceStatus> sources, List<String> providers, int entries, int names, boolean running) {
    }

    public record CheckRequest(@NotBlank @Size(max = 300) String name, LocalDate dateOfBirth, PartyType partyType) {
    }

    public record CheckMatch(String source, String entryUid, String entryName, String matchedName, int score, int nameScore,
            String birthDateCheck, List<String> birthDates, List<String> programs) {

        static CheckMatch of(WatchlistMatch m) {
            return new CheckMatch(m.entry().source(), m.entry().uid(), m.entry().primaryName(), m.matchedName(), m.score(), m.nameScore(),
                    m.birthDateCheck().name(), m.entry().birthDates(), m.entry().programs());
        }
    }

    @GetMapping("/sources")
    public Status status() {
        final WatchlistIndex index = screening.index();
        return new Status(lists.sources(), refresher.providers().stream().map(WatchlistProvider::source).toList(), index.entryCount(),
                index.nameCount(), refresher.isRunning());
    }

    /**
     * Starts a full run (download lists, copy clients, re-screen) in the background.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Status> refresh() {
        if (refresher.isRunning()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(status());
        }
        Thread.ofVirtual().name("watchlist-refresh").start(() -> {
            try {
                refresher.run();
            } catch (RuntimeException e) {
                log.error("Manual watchlist run failed", e);
            }
        });
        return ResponseEntity.accepted().body(status());
    }

    /**
     * Checks a name against the lists without storing anything or raising an alert.
     */
    @PostMapping("/check")
    public List<CheckMatch> check(@Valid @RequestBody CheckRequest request) {
        final PartyType party = request.partyType() == null ? PartyType.UNKNOWN : request.partyType();
        return screening.check(new ScreeningSubject(null, null, request.name(), request.dateOfBirth(), party)).stream().map(CheckMatch::of)
                .toList();
    }
}
