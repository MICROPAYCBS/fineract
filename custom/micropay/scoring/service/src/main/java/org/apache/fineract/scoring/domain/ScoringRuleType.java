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
package org.apache.fineract.scoring.domain;

/**
 * Knock-out and refer rules evaluated before the scorecard points. {@link #CHARACTERISTIC} compares any scoring input
 * against a threshold; the others check facts that are not numeric inputs.
 */
public enum ScoringRuleType {
    /** The client has an active identifier whose expiry date is before the business date. */
    EXPIRED_ID,
    /** The client is on the Micropay blacklist with block_loan_application set. */
    BLACKLISTED,
    /** The client's customer class is marked not loan eligible. */
    CUSTOMER_CLASS_NOT_ELIGIBLE,
    /** A characteristic compared with a threshold using an operator. */
    CHARACTERISTIC
}
