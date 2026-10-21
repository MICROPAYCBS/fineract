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
package org.apache.fineract.scoring.data;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Body of create and update scorecard requests.
 */
@Data
@NoArgsConstructor
public class ScorecardRequest {

    private String code;
    private String name;
    private String description;
    private String type;
    private BigDecimal basePoints;
    private List<BinRequest> bins = new ArrayList<>();
    private List<GradeBandRequest> gradeBands = new ArrayList<>();
    private List<RuleRequest> rules = new ArrayList<>();

    @Data
    @NoArgsConstructor
    public static class BinRequest {

        private String characteristic;
        private BigDecimal lower;
        private BigDecimal upper;
        private String matchValue;
        private Boolean missing;
        private BigDecimal points;
        private String reasonCode;
    }

    @Data
    @NoArgsConstructor
    public static class GradeBandRequest {

        private String grade;
        private BigDecimal minScore;
        private BigDecimal maxScore;
        private BigDecimal expectedPd;
        private String outcome;
    }

    @Data
    @NoArgsConstructor
    public static class RuleRequest {

        private String ruleType;
        private String characteristic;
        private String operator;
        private String value;
        private String outcome;
        private String reasonCode;
    }
}
