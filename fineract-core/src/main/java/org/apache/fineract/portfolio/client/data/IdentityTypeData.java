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
package org.apache.fineract.portfolio.client.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serial;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(description = "Identity document validation rule")
public class IdentityTypeData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Identity type identifier.", example = "1")
    private Long id;

    @Schema(description = "Customer Identifier code value this rule applies to.", example = "1")
    private Long codeValueId;

    @Schema(description = "Name of the Customer Identifier code value.", example = "National ID")
    private String codeValueName;

    @Schema(description = "Example document number. Maximum length 255.", example = "CM12345678AB1C")
    private String example;

    @Schema(description = "Human-readable format description. Maximum length 500.", example = "14-character national ID")
    private String formatDescription;

    @Schema(description = "Message shown when a document number fails validation. Maximum length 500.", example = "Enter a valid national ID")
    private String validationMessage;

    @Schema(description = "Regular expression the document number must match. Maximum length 500.", example = "^[A-Z0-9]{14}$")
    private String validationRegex;

    @Schema(description = "Display order. Zero or greater.", example = "1")
    private Integer displayOrder;

    @Schema(description = "Status. Allowed values: ACTIVE, INACTIVE.", example = "ACTIVE")
    private String status;
}
