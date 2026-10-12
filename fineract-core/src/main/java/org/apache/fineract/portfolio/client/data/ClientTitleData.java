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
@Schema(description = "Client title")
public class ClientTitleData implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Client title identifier.", example = "1")
    private Long id;

    @Schema(description = "Title code. Maximum length 20.", example = "MR")
    private String titleCode;

    @Schema(description = "Title name. Maximum length 100.", example = "Mr")
    private String titleName;

    @Schema(description = "Gender the title applies to. Allowed values: 1 (Male), 2 (Female). Null when the title is not gender-specific.", example = "1")
    private Integer genderId;

    @Schema(description = "Display order. Zero or greater.", example = "1")
    private Integer displayOrder;

    @Schema(description = "Status. Allowed values: ACTIVE, INACTIVE.", example = "ACTIVE")
    private String status;
}
