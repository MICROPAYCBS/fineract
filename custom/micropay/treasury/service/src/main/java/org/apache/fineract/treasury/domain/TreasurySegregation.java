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
package org.apache.fineract.treasury.domain;

import java.util.Objects;
import org.apache.fineract.treasury.exception.TreasuryWorkflowException;

/**
 * Separation of duties: the user who performed a step may not perform the next one on the same transfer or float
 * request, whatever permissions they hold.
 */
public final class TreasurySegregation {

    private TreasurySegregation() {}

    /**
     * @param previousStep
     *            the step already taken, for example {@code requested}
     * @param previousUserId
     *            who took it
     * @param step
     *            the step being attempted, for example {@code approve}
     * @param userId
     *            who attempts it
     */
    public static void requireDifferentUser(final String entity, final Long entityId, final String previousStep, final Long previousUserId,
            final String step, final Long userId) {
        if (previousUserId != null && Objects.equals(previousUserId, userId)) {
            throw new TreasuryWorkflowException(entity, "same.user",
                    "The user who " + previousStep + " " + entity + " " + entityId + " cannot also " + step + " it", entityId, previousStep,
                    step);
        }
    }
}
