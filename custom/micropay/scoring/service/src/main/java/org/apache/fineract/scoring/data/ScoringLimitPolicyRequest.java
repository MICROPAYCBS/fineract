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
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ScoringLimitPolicyRequest {

    private String code;
    private String name;
    private String scorecardCode;
    private Long loanProductId;
    private String mode;
    private Boolean staffOnly;
    private Long officeId;
    private Long customerClassId;
    private Integer validDays;
    private String baseCharacteristic;
    private BigDecimal roundingUnit;
    private List<BandRequest> bands;

    @Data
    @NoArgsConstructor
    public static class BandRequest {

        private String grade;
        private BigDecimal multiplier;
        private BigDecimal maxAmount;
    }
}
