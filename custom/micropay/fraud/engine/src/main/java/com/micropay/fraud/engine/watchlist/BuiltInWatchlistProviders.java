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
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The providers configured under {@code fraud.watchlist}: the OFAC, UN and EU lists and any local CSV files.
 */
@Slf4j
@Component
public class BuiltInWatchlistProviders {

    public static final String OFAC = "OFAC";
    public static final String UN = "UN";
    public static final String EU = "EU";

    private final List<WatchlistProvider> providers;

    public BuiltInWatchlistProviders(FraudEngineProperties properties) {
        final FraudEngineProperties.Watchlist config = properties.watchlist();
        final HttpClient http = StreamWatchlistProvider.defaultHttpClient();
        final List<WatchlistProvider> list = new ArrayList<>();
        add(list, OFAC, config.ofac(), new OfacSdnParser(), http);
        add(list, UN, config.un(), new UnConsolidatedParser(), http);
        add(list, EU, config.eu(), new EuConsolidatedParser(), http);
        for (Map.Entry<String, String> file : config.files().entrySet()) {
            if (file.getValue() != null && !file.getValue().isBlank()) {
                list.add(new StreamWatchlistProvider(file.getKey().toUpperCase(Locale.ROOT), file.getValue().trim(), new CsvListParser(),
                        http));
            }
        }
        this.providers = List.copyOf(list);
    }

    public List<WatchlistProvider> providers() {
        return providers;
    }

    private static void add(List<WatchlistProvider> list, String source, FraudEngineProperties.Feed feed, ListParser parser,
            HttpClient http) {
        if (feed == null || !feed.enabled()) {
            return;
        }
        if (feed.url() == null || feed.url().isBlank()) {
            log.warn("Watchlist {} is enabled but has no URL; it will not be loaded", source);
            return;
        }
        list.add(new StreamWatchlistProvider(source, feed.url().trim(), parser, http));
    }
}
