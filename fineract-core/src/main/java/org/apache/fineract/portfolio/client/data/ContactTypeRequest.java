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
@Schema(description = "Contact type create or update request")
public class ContactTypeRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Contact type code. Required on create and when supplied on update. Maximum length 20.", example = "MOBILE", maxLength = 20)
    private String typeCode;

    @Schema(description = "Contact type name. Required on create and when supplied on update. Maximum length 100.", example = "Mobile phone", maxLength = 100)
    private String typeName;

    @Schema(description = "Example value shown when a contact fails validation. Optional. Maximum length 255.", example = "256700000000", maxLength = 255)
    private String example;

    @Schema(description = "Regular expression the contact value must match. Optional. Maximum length 500. Must be a compilable Java regular expression.", example = "^256[0-9]{9}$", maxLength = 500)
    private String validationRegex;

    @Schema(description = "Whether a client must have at least one contact of this type. Optional.", example = "true")
    private Boolean mandatory;

    @Schema(description = "Display order. Optional. Zero or greater.", example = "1")
    private Integer displayOrder;

    @Schema(description = "Status. Optional. Allowed values: ACTIVE, INACTIVE.", example = "ACTIVE")
    private String status;
}
