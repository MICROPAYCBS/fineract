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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** De-duplication, escalation and clearing, and the breach state machine. */
class BreachActionTest {

    @Test
    void anObservationAgainstTheOpenBreach() {
        assertEquals(BreachAction.NONE, BreachAction.decide(null, null));
        assertEquals(BreachAction.CREATED, BreachAction.decide(null, LimitLevel.WARNING));
        assertEquals(BreachAction.CREATED, BreachAction.decide(null, LimitLevel.HARD));
        assertEquals(BreachAction.REPEATED, BreachAction.decide(LimitLevel.WARNING, LimitLevel.WARNING));
        assertEquals(BreachAction.REPEATED, BreachAction.decide(LimitLevel.HARD, LimitLevel.HARD));
        assertEquals(BreachAction.REPEATED, BreachAction.decide(LimitLevel.HARD, LimitLevel.WARNING), "never downgraded");
        assertEquals(BreachAction.ESCALATED, BreachAction.decide(LimitLevel.WARNING, LimitLevel.HARD));
        assertEquals(BreachAction.CLEARED, BreachAction.decide(LimitLevel.HARD, null));
        assertEquals(BreachAction.CLEARED, BreachAction.decide(LimitLevel.WARNING, null));
    }

    @Test
    void newAndEscalatedBreachesNotify() {
        assertTrue(BreachAction.CREATED.notifies());
        assertTrue(BreachAction.ESCALATED.notifies());
        assertFalse(BreachAction.REPEATED.notifies());
        assertFalse(BreachAction.CLEARED.notifies());
        assertFalse(BreachAction.NONE.notifies());
    }

    @Test
    void openAcknowledgedResolved() {
        assertTrue(BreachStatus.OPEN.canAcknowledge());
        assertFalse(BreachStatus.ACKNOWLEDGED.canAcknowledge());
        assertFalse(BreachStatus.RESOLVED.canAcknowledge());
        assertTrue(BreachStatus.OPEN.canResolve());
        assertTrue(BreachStatus.ACKNOWLEDGED.canResolve());
        assertFalse(BreachStatus.RESOLVED.canResolve());
        assertTrue(BreachStatus.ACKNOWLEDGED.isOpen());
        assertFalse(BreachStatus.RESOLVED.isOpen());
    }
}
