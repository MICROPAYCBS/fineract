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
package org.apache.fineract.fraud.hold.service;

public final class FraudHoldConstants {

    public static final String MODULE_ENABLED_PROPERTY = "fineract.module.fraud.enabled";
    public static final String ENTITY_NAME = "FRAUD_HOLD";
    public static final String RESOURCE_NAME = "fraudhold";

    public static final String ENTITY_TYPE_PARAM = "entityType";
    public static final String ENTITY_ID_PARAM = "entityId";
    public static final String REASON_PARAM = "reason";
    public static final String SOURCE_PARAM = "source";
    public static final String EXTERNAL_REFERENCE_PARAM = "externalReference";
    public static final String NOTE_PARAM = "note";

    public static final int REASON_MAX_LENGTH = 500;
    public static final int EXTERNAL_REFERENCE_MAX_LENGTH = 100;

    private FraudHoldConstants() {}
}
