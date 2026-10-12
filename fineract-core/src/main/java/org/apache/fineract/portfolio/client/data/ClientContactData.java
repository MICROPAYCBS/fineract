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
@Schema(description = "Client contact")
public class ClientContactData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Client contact identifier.", example = "1")
    private Long id;

    @Schema(description = "Client identifier.", example = "1")
    private Long clientId;

    @Schema(description = "Contact type identifier.", example = "1")
    private Long contactTypeId;

    @Schema(description = "Contact type code copied from the contact type.", example = "MOBILE")
    private String contactTypeCode;

    @Schema(description = "Contact type name copied from the contact type.", example = "Mobile phone")
    private String contactTypeName;

    @Schema(description = "Example value from the contact type, used when the value fails its regex.", example = "256700000000")
    private String example;

    @Schema(description = "Validation regular expression from the contact type.", example = "^256[0-9]{9}$")
    private String validationRegex;

    @Schema(description = "Whether this contact type is mandatory for a client.", example = "true")
    private Boolean mandatory;

    @Schema(description = "Contact value. Maximum length 255.", example = "256700000000")
    private String contactValue;

    @Schema(description = "Whether this is the primary contact for its contact type.", example = "true")
    private Boolean primary;
}
