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
package org.apache.fineract.portfolio.industry.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.fineract.portfolio.sector.data.SectorData;

final class IndustryApiResourceSwagger {

    private IndustryApiResourceSwagger() {}

    @Schema(description = "GetIndustryTemplateResponse")
    public static final class GetIndustryTemplateResponse {

        private GetIndustryTemplateResponse() {}

        @Schema(description = "Allowed values for status", example = "[\"ACTIVE\", \"INACTIVE\"]")
        public List<String> statusOptions;
        @Schema(description = "Active sectors an industry can belong to")
        public List<SectorData> sectorOptions;
    }
}
