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
package org.apache.fineract.portfolio.customerclass.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Customer class template option lists")
public class CustomerClassTemplateData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Customer types allowed on create.", example = "[\"GROUP\",\"JOINT\"]")
    private List<String> customerTypeOptions;

    @Schema(description = "Legal form options.", example = "[{\"id\":1,\"name\":\"Person\"},{\"id\":2,\"name\":\"Entity\"}]")
    private List<LegalFormOption> legalFormOptions;

    @Schema(description = "Risk level options.", example = "[\"LOW\",\"MEDIUM\",\"HIGH\"]")
    private List<String> riskLevelOptions;

    @Schema(description = "KYC level options.", example = "[\"BASIC\",\"STANDARD\",\"ENHANCED\"]")
    private List<String> kycLevelOptions;

    @Schema(description = "Status options.", example = "[\"ACTIVE\",\"INACTIVE\"]")
    private List<String> statusOptions;

    @Schema(description = "Active restriction options.", example = "[{\"id\":1,\"restrictionCode\":\"NONE\",\"restrictionName\":\"No restriction\"}]")
    private List<RestrictionOption> restrictionOptions;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "Legal form option")
    public static class LegalFormOption implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "Legal form identifier. 1 is Person and 2 is Entity.", example = "1")
        private Integer id;

        @Schema(description = "Legal form name.", example = "Person")
        private String name;
    }

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Schema(description = "Restriction option")
    public static class RestrictionOption implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "Restriction identifier.", example = "1")
        private Long id;

        @Schema(description = "Restriction code.", example = "NONE")
        private String restrictionCode;

        @Schema(description = "Restriction name.", example = "No restriction")
        private String restrictionName;
    }
}
