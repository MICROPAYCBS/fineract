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
package org.apache.fineract.portfolio.department.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;
import org.apache.fineract.organisation.office.data.OfficeData;

final class DepartmentApiResourceSwagger {

    private DepartmentApiResourceSwagger() {}

    @Schema(description = "GetDepartmentTemplateResponse")
    public static final class GetDepartmentTemplateResponse {

        private GetDepartmentTemplateResponse() {}

        @Schema(description = "Allowed values for the active flag")
        public List<EnumOptionData> activeOptions;
        @Schema(description = "Offices that can be linked to a department")
        public List<OfficeData> officeOptions;
    }
}
