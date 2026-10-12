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
@Schema(description = "Client contact create or update request")
public class ClientContactRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Contact type identifier. Required on create. On update, required only when supplied, and must be greater than zero.", example = "1")
    private Long contactTypeId;

    @Schema(description = "Contact value. Required on create. On update, required only when supplied. Maximum length 255. Must match the contact type validation regex when one is configured.", example = "256700000000", maxLength = 255)
    private String contactValue;

    @Schema(description = "Whether this is the primary contact for its contact type. Optional.", example = "true")
    private Boolean primary;
}
