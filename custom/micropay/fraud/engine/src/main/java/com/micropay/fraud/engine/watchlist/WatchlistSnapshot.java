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

import java.util.List;

/**
 * A full copy of one list as published.
 *
 * @param version
 *            identifies the content, for example its SHA-256, so an unchanged list is not reloaded
 */
public record WatchlistSnapshot(String source, String version, List<WatchlistEntry> entries) {

    public WatchlistSnapshot {
        entries = List.copyOf(entries);
    }
}
