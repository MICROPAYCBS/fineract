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
package com.micropay.fraud.engine.scoring;

import com.micropay.fraud.engine.rule.RuleHit;
import java.util.List;

/**
 * The combined verdict on one transaction.
 *
 * @param anomalyScore
 *            the isolation forest score (0 to 1), or null when no model exists yet
 */
public record RiskAssessment(int score, Severity severity, boolean alert, List<RuleHit> reasons, Double anomalyScore) {
}
