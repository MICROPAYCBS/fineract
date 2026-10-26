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
package com.micropay.fraud.engine.watchlist;

import com.micropay.fraud.engine.config.FraudEngineProperties;
import com.micropay.fraud.engine.event.ClientEvent;
import com.micropay.fraud.engine.event.ClientNames;
import com.micropay.fraud.engine.event.PartyType;
import com.micropay.fraud.engine.fineract.FineractClient;
import com.micropay.fraud.engine.store.ClientProfileRepository;
import java.io.IOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;

/**
 * The nightly watchlist run: download every list, copy clients from Fineract, and re-screen every client whose name or
 * list content changed since it was last screened.
 */
@Slf4j
@Component
public class WatchlistRefresher {

    static final int CLIENT_PAGE = 200;
    static final int SCREEN_BATCH = 500;

    private final List<WatchlistProvider> providers;
    private final WatchlistRepository lists;
    private final WatchlistService screening;
    private final ClientProfileRepository clients;
    private final FineractClient fineract;
    private final FraudEngineProperties.Watchlist config;
    private final Clock clock;
    private final AtomicBoolean running = new AtomicBoolean();

    /**
     * Results of one complete run.
     */
    public record RunSummary(List<RefreshResult> lists, int clientsChanged, int clientsScreened, int alertsRaised) {
    }

    public WatchlistRefresher(BuiltInWatchlistProviders builtIn, ObjectProvider<WatchlistProvider> extraProviders,
            WatchlistRepository lists, WatchlistService screening, ClientProfileRepository clients, FineractClient fineract,
            FraudEngineProperties properties, Clock clock) {
        final List<WatchlistProvider> all = new ArrayList<>(builtIn.providers());
        all.addAll(extraProviders.orderedStream().toList());
        this.providers = List.copyOf(all);
        this.lists = lists;
        this.screening = screening;
        this.clients = clients;
        this.fineract = fineract;
        this.config = properties.watchlist();
        this.clock = clock;
    }

    public List<WatchlistProvider> providers() {
        return providers;
    }

    public boolean isRunning() {
        return running.get();
    }

    @Scheduled(cron = "${fraud.watchlist.refresh-cron:0 0 2 * * *}", zone = "${fraud.time-zone:Africa/Kampala}")
    public void scheduledRun() {
        if (config.enabled()) {
            run();
        }
    }

    /**
     * Loads the lists right away on a fresh installation, or when a list was added, instead of waiting for the night.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void loadMissingListsOnStartup() {
        final List<String> stored = lists.sources().stream().filter(s -> s.version() != null).map(WatchlistSourceStatus::source).toList();
        if (config.enabled() && config.loadOnStartup() && providers.stream().anyMatch(p -> !stored.contains(p.source()))) {
            log.info("Some watchlists were never loaded; starting a watchlist run now");
            Thread.ofVirtual().name("watchlist-startup").start(() -> {
                try {
                    run();
                } catch (RuntimeException e) {
                    log.error("Watchlist run at startup failed", e);
                }
            });
        }
    }

    /**
     * @return the summary, or null when a run is already in progress
     */
    public RunSummary run() {
        if (!running.compareAndSet(false, true)) {
            log.info("Watchlist run skipped: another run is in progress");
            return null;
        }
        try {
            final List<RefreshResult> results = refreshLists();
            final int synced = syncClients();
            final int[] screened = rescreen();
            final RunSummary summary = new RunSummary(results, synced, screened[0], screened[1]);
            log.info("Watchlist run finished: {}", summary);
            return summary;
        } finally {
            running.set(false);
        }
    }

    public List<RefreshResult> refreshLists() {
        final List<RefreshResult> results = new ArrayList<>();
        for (WatchlistProvider provider : providers) {
            results.add(refresh(provider));
        }
        screening.reloadIndex();
        return results;
    }

    private RefreshResult refresh(WatchlistProvider provider) {
        final String source = provider.source();
        final WatchlistSnapshot snapshot;
        try {
            snapshot = provider.fetch();
        } catch (IOException e) {
            log.warn("Watchlist {} could not be fetched, keeping the stored copy: {}", source, e.getMessage());
            lists.markChecked(source, clock.instant(), e.toString());
            return new RefreshResult(source, RefreshResult.Outcome.FAILED, 0, e.toString());
        } catch (RuntimeException e) {
            log.error("Watchlist {} could not be read, keeping the stored copy", source, e);
            lists.markChecked(source, clock.instant(), e.toString());
            return new RefreshResult(source, RefreshResult.Outcome.FAILED, 0, e.toString());
        }
        final WatchlistSourceStatus stored = lists.source(source);
        if (stored != null && snapshot.version().equals(stored.version())) {
            lists.markChecked(source, clock.instant(), null);
            return new RefreshResult(source, RefreshResult.Outcome.UNCHANGED, stored.entryCount(), null);
        }
        final int count = snapshot.entries().size();
        final boolean shrunk = stored != null && stored.entryCount() > 0 && count < stored.entryCount() * config.minRetainedShare();
        if (count == 0 || shrunk) {
            final String message = "Refused: " + count + " entries read"
                    + (stored == null ? "" : " against " + stored.entryCount() + " stored");
            log.warn("Watchlist {} looks broken, keeping the stored copy. {}", source, message);
            lists.markChecked(source, clock.instant(), message);
            return new RefreshResult(source, RefreshResult.Outcome.FAILED, count, message);
        }
        lists.replace(snapshot, clock.instant());
        log.info("Watchlist {} loaded: {} entries (version {})", source, count, snapshot.version());
        return new RefreshResult(source, RefreshResult.Outcome.LOADED, count, null);
    }

    /**
     * Copies every client of the configured tenants from Fineract, so clients created before the engine ran, or renamed
     * since, are screened too.
     */
    public int syncClients() {
        if (!config.syncClients() || !fineract.isEnabled()) {
            return 0;
        }
        int synced = 0;
        for (String tenant : config.tenants()) {
            try {
                for (int offset = 0;; offset += CLIENT_PAGE) {
                    final List<FineractClient.ClientSummary> page = fineract.listClients(tenant, offset, CLIENT_PAGE);
                    for (FineractClient.ClientSummary c : page) {
                        if (sync(tenant, c)) {
                            synced++;
                        }
                    }
                    if (page.size() < CLIENT_PAGE) {
                        break;
                    }
                }
            } catch (RestClientException e) {
                log.warn("Copying clients of tenant {} from Fineract stopped: {}", tenant, e.getMessage());
            }
        }
        return synced;
    }

    /**
     * Stores a client copied from Fineract unless nothing that screening uses has changed, so unchanged clients are not
     * re-screened every night.
     *
     * @return whether the client was new or changed
     */
    private boolean sync(String tenant, FineractClient.ClientSummary c) {
        final String name = ClientNames.fullName(c.firstname(), c.middlename(), c.lastname(), c.fullname(), c.displayName());
        final PartyType party = ClientNames.partyType(c.legalFormId());
        final Optional<ScreeningSubject> stored = clients.findSubject(tenant, c.id());
        if (stored.isPresent() && Objects.equals(stored.get().name(), name) && Objects.equals(stored.get().dateOfBirth(), c.dateOfBirth())
                && (party == PartyType.UNKNOWN || stored.get().partyType() == party)) {
            return false;
        }
        clients.save(new ClientEvent(tenant, "sync", "ClientSync", c.id(), c.activationDate(), c.officeId(), c.displayName(), name,
                c.dateOfBirth(), party), clock.instant());
        return true;
    }

    /**
     * @return clients screened and alerts raised
     */
    public int[] rescreen() {
        final String version = screening.index().version();
        int screened = 0;
        int raised = 0;
        String afterTenant = "";
        Long afterClient = Long.MIN_VALUE;
        while (true) {
            final List<ScreeningSubject> batch = clients.findDueForScreening(version, afterTenant, afterClient, SCREEN_BATCH);
            for (ScreeningSubject subject : batch) {
                if (screening.screenClient(subject).isPresent()) {
                    raised++;
                }
                screened++;
                afterTenant = subject.tenantId();
                afterClient = subject.clientId();
            }
            if (batch.size() < SCREEN_BATCH) {
                break;
            }
        }
        return new int[] { screened, raised };
    }
}
