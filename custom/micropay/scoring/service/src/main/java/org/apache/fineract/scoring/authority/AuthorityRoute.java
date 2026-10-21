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
package org.apache.fineract.scoring.authority;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which stages of a workflow a loan must pass, stage by stage, with the reason.
 */
public record AuthorityRoute(List<StageRoute> stages) {

    public static final String ALWAYS = "NO_AUTHORITY_RULES";
    public static final String MATCHED = "RULE_MATCHED";
    public static final String NOT_MATCHED = "NO_RULE_MATCHED";
    public static final String NO_DECISION = "NO_SCORING_DECISION";

    /**
     * @param required
     *            the stage applies to this loan
     * @param reason
     *            one of {@link #ALWAYS}, {@link #MATCHED}, {@link #NOT_MATCHED} or {@link #NO_DECISION}
     * @param matchedRuleIds
     *            the rules that require the stage
     */
    public record StageRoute(String stageCode, boolean required, String reason, List<Long> matchedRuleIds) {
    }

    public Set<String> skipped() {
        return this.stages.stream().filter(stage -> !stage.required()).map(StageRoute::stageCode).collect(Collectors.toSet());
    }
}
