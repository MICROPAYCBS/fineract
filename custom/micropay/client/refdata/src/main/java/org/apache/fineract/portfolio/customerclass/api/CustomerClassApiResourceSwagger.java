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
package org.apache.fineract.portfolio.customerclass.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassData;
import org.apache.fineract.portfolio.customerclass.data.CustomerClassTemplateData;

final class CustomerClassApiResourceSwagger {

    private CustomerClassApiResourceSwagger() {}

    @Schema(description = "GetCustomerClassTemplateResponse")
    public static final class GetCustomerClassTemplateResponse {

        private GetCustomerClassTemplateResponse() {}

        @Schema(description = "Customer types allowed on create.", example = "[\"GROUP\",\"JOINT\"]")
        public List<String> customerTypeOptions;

        @Schema(description = "Legal form options. id 1 is Person and id 2 is Entity.", example = "[{\"id\":1,\"name\":\"Person\"}]")
        public List<CustomerClassTemplateData.LegalFormOption> legalFormOptions;

        @Schema(description = "Risk level options.", example = "[\"LOW\",\"MEDIUM\",\"HIGH\"]")
        public List<String> riskLevelOptions;

        @Schema(description = "KYC level options.", example = "[\"BASIC\",\"STANDARD\",\"ENHANCED\"]")
        public List<String> kycLevelOptions;

        @Schema(description = "Status options.", example = "[\"ACTIVE\",\"INACTIVE\"]")
        public List<String> statusOptions;

        @Schema(description = "Active restriction options.", example = "[{\"id\":1,\"restrictionCode\":\"NONE\",\"restrictionName\":\"No restriction\"}]")
        public List<CustomerClassTemplateData.RestrictionOption> restrictionOptions;
    }

    @Schema(description = "GetCustomerClassWithTemplateResponse")
    public static final class GetCustomerClassWithTemplateResponse {

        private GetCustomerClassWithTemplateResponse() {}

        @Schema(description = "Customer types allowed on create.", example = "[\"GROUP\",\"JOINT\"]")
        public List<String> customerTypeOptions;

        @Schema(description = "Legal form options. id 1 is Person and id 2 is Entity.", example = "[{\"id\":1,\"name\":\"Person\"}]")
        public List<CustomerClassTemplateData.LegalFormOption> legalFormOptions;

        @Schema(description = "Risk level options.", example = "[\"LOW\",\"MEDIUM\",\"HIGH\"]")
        public List<String> riskLevelOptions;

        @Schema(description = "KYC level options.", example = "[\"BASIC\",\"STANDARD\",\"ENHANCED\"]")
        public List<String> kycLevelOptions;

        @Schema(description = "Status options.", example = "[\"ACTIVE\",\"INACTIVE\"]")
        public List<String> statusOptions;

        @Schema(description = "Active restriction options.", example = "[{\"id\":1,\"restrictionCode\":\"NONE\",\"restrictionName\":\"No restriction\"}]")
        public List<CustomerClassTemplateData.RestrictionOption> restrictionOptions;

        @Schema(description = "The customer class being edited.", example = "{\"id\":1,\"classCode\":\"RETAIL\"}")
        public CustomerClassData customerClass;
    }
}
