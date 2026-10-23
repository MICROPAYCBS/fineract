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
package org.apache.fineract.treasury.stress;

/**
 * What a shock does.
 *
 * <ul>
 * <li>MULTIPLY: the matching lines at {@code factor} percent (collections at 60%, agent float demand at 150%).</li>
 * <li>ADD_OUTFLOW_PCT_OF_BALANCE: an extra outflow of {@code factor} percent of the balance of the matching lines
 * (their contractual total over every bucket) in the target bucket, the first by default (a 20% run on demand savings
 * in the first week).</li>
 * <li>REMOVE_ROLLOVER: the matching DEALS or TERM_DEPOSITS lines without their behavioural rollover.</li>
 * <li>HAIRCUT_LIQUID_ASSET: a further {@code factor} percent haircut on an asset class's counterbalancing
 * capacity.</li>
 * <li>FREEZE_LINE: funding lines of a source type, or one funding line, unavailable.</li>
 * </ul>
 */
public enum ShockType {

    MULTIPLY, //
    ADD_OUTFLOW_PCT_OF_BALANCE, //
    REMOVE_ROLLOVER, //
    HAIRCUT_LIQUID_ASSET, //
    FREEZE_LINE; //

    /** Changes ladder lines rather than the capacity. */
    public boolean shocksLines() {
        return this == MULTIPLY || this == ADD_OUTFLOW_PCT_OF_BALANCE || this == REMOVE_ROLLOVER;
    }
}
