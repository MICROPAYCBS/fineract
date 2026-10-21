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
package org.apache.fineract.infrastructure.dataqueries.api;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

final class DatatableColumnValidationsApiResourceSwagger {

    private DatatableColumnValidationsApiResourceSwagger() {}

    @Schema(description = "PutColumnValidationsRequest")
    public static final class PutColumnValidationsRequest {

        private PutColumnValidationsRequest() {}

        @Schema(description = "Columns whose regex, example and message are replaced. A column with all three empty loses its rule.")
        public List<ColumnValidation> columnValidations;
        @Schema(description = "Column names whose rules are removed.")
        public List<String> deleteColumnNames;
    }

    @Schema(description = "ColumnValidation")
    public static final class ColumnValidation {

        private ColumnValidation() {}

        @Schema(description = "Column name", example = "national_id")
        public String columnName;
        @Schema(description = "Validation regex. Empty together with example and message clears the rule.", example = "^[A-Z0-9]+$")
        public String validationRegex;
        @Schema(description = "Example value shown to the user", example = "CM12345678")
        public String validationExample;
        @Schema(description = "Message shown when the value does not match", example = "Enter a valid id")
        public String validationMessage;
    }
}
