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
package org.apache.fineract.portfolio.sector.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.fineract.portfolio.sector.data.SectorData;

final class SectorApiResourceSwagger {

    private SectorApiResourceSwagger() {}

    @Schema(description = "GetSectorTemplateResponse")
    public static final class GetSectorTemplateResponse {

        private GetSectorTemplateResponse() {}

        @Schema(description = "Allowed values for status", example = "[\"ACTIVE\", \"INACTIVE\"]")
        public List<String> statusOptions;
        @Schema(description = "Active sectors that can be chosen as parent")
        public List<SectorData> parentSectorOptions;
    }
}
