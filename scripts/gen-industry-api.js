const fs = require('fs');
const path = require('path');

const root = path.join(__dirname, '..');

function write(rel, content) {
  const full = path.join(root, rel);
  fs.mkdirSync(path.dirname(full), { recursive: true });
  fs.writeFileSync(full, content);
  console.log('wrote', rel);
}

const license = (pkg) => `/**
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
package org.apache.fineract.portfolio.${pkg};
`;

// Industry core DTOs
write('fineract-core/src/main/java/org/apache/fineract/portfolio/industry/data/IndustryData.java', `${license('industry.data')}
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IndustryData implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private Long id;
    private String industryCode;
    private String industryName;
    private String description;
    private Long sectorId;
    private String sectorName;
    private String regulatoryCode;
    private String riskLevel;
    private String amlRiskLevel;
    private String creditRiskLevel;
    private Boolean priorityIndustry;
    private Boolean prohibitedIndustry;
    private Boolean requiresEdd;
    private BigDecimal exposureLimit;
    private BigDecimal expectedTurnoverMin;
    private BigDecimal expectedTurnoverMax;
    private String status;
}
`);

write('fineract-core/src/main/java/org/apache/fineract/portfolio/industry/data/IndustryRequest.java', `${license('industry.data')}
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class IndustryRequest implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private String industryCode;
    private String industryName;
    private String description;
    private Long sectorId;
    private String regulatoryCode;
    private String riskLevel;
    private String amlRiskLevel;
    private String creditRiskLevel;
    private Boolean priorityIndustry;
    private Boolean prohibitedIndustry;
    private Boolean requiresEdd;
    private BigDecimal exposureLimit;
    private BigDecimal expectedTurnoverMin;
    private BigDecimal expectedTurnoverMax;
    private String status;
}
`);

// SubIndustry core DTOs
write('fineract-core/src/main/java/org/apache/fineract/portfolio/subindustry/data/SubIndustryData.java', `${license('subindustry.data')}
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubIndustryData implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private Long id;
    private String subIndustryCode;
    private String subIndustryName;
    private String description;
    private Long industryId;
    private String industryName;
    private String regulatoryCode;
    private String riskLevel;
    private String amlRiskLevel;
    private String creditRiskLevel;
    private Boolean priority;
    private Boolean prohibited;
    private Boolean requiresEdd;
    private BigDecimal exposureLimit;
    private BigDecimal expectedTurnoverMin;
    private BigDecimal expectedTurnoverMax;
    private String status;
}
`);

write('fineract-core/src/main/java/org/apache/fineract/portfolio/subindustry/data/SubIndustryRequest.java', `${license('subindustry.data')}
import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SubIndustryRequest implements Serializable {
    @Serial private static final long serialVersionUID = 1L;
    private String subIndustryCode;
    private String subIndustryName;
    private String description;
    private Long industryId;
    private String regulatoryCode;
    private String riskLevel;
    private String amlRiskLevel;
    private String creditRiskLevel;
    private Boolean priority;
    private Boolean prohibited;
    private Boolean requiresEdd;
    private BigDecimal exposureLimit;
    private BigDecimal expectedTurnoverMin;
    private BigDecimal expectedTurnoverMax;
    private String status;
}
`);

console.log('Done generating core DTOs');
