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

import java.io.IOException;

/**
 * A source of watchlist entries. The built-in providers read the OFAC, UN and EU lists and local CSV files; a paid
 * provider (World-Check, Dow Jones, LexisNexis and the like) plugs in by declaring another bean of this type.
 */
public interface WatchlistProvider {

    /**
     * Short, stable name of the list, for example {@code OFAC}. Used as the key of its entries and in alert reasons.
     */
    String source();

    /**
     * Fetches the whole current list.
     */
    WatchlistSnapshot fetch() throws IOException;
}
