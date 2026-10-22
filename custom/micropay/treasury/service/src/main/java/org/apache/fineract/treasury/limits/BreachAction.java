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
package org.apache.fineract.treasury.limits;

/**
 * What an observation does to the breach log for its limit and subject, given the breach still open for them (OPEN or
 * ACKNOWLEDGED), if any.
 *
 * <ul>
 * <li>CREATED: no open breach, the value crosses a level: a new breach.</li>
 * <li>REPEATED: an open breach at the same or a higher level: it is seen again (last seen, occurrences).</li>
 * <li>ESCALATED: an open WARNING breach and the value now crosses the hard level: the breach is upgraded to HARD.</li>
 * <li>CLEARED: an open breach and the value is back within the levels: the breach is marked cleared, not resolved.</li>
 * <li>NONE: no open breach and no level crossed.</li>
 * </ul>
 */
public enum BreachAction {

    NONE, //
    CREATED, //
    REPEATED, //
    ESCALATED, //
    CLEARED; //

    public static BreachAction decide(final LimitLevel openLevel, final LimitLevel observed) {
        if (observed == null) {
            return openLevel == null ? NONE : CLEARED;
        }
        if (openLevel == null) {
            return CREATED;
        }
        return observed.isAbove(openLevel) ? ESCALATED : REPEATED;
    }

    /** New and escalated breaches are notified. */
    public boolean notifies() {
        return this == CREATED || this == ESCALATED;
    }
}
