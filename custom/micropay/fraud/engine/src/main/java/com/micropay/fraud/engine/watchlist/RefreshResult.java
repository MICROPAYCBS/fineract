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

/**
 * What happened to one list in a refresh.
 */
public record RefreshResult(String source, Outcome outcome, int entries, String message) {

    public enum Outcome {
        /** A new version was stored. */
        LOADED,
        /** The published list is the one already stored. */
        UNCHANGED,
        /** The download or parse failed, or the list looked broken; the stored copy stays in use. */
        FAILED
    }
}
