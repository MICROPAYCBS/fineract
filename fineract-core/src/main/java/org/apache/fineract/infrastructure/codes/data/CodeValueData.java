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
package org.apache.fineract.infrastructure.codes.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Accessors;

/**
 * Immutable data object represent code-value data in system.
 */
@Data
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Accessors(chain = true)
@Schema(description = "Code value")
public class CodeValueData implements Serializable {

    @Schema(description = "Code value identifier.", example = "1")
    private Long id;

    @Schema(description = "Code value name.", example = "Employment")
    private String name;

    @Schema(description = "Display position.", example = "1")
    private Integer position;

    @Schema(description = "Code value description.", example = "Income from employment")
    private String description;

    @Schema(description = "Whether the code value is active.", example = "true")
    private Boolean active;

    @Schema(description = "Whether the code value is mandatory.", example = "false")
    private Boolean mandatory;

    public static CodeValueData instance(final Long id, final String name, final String description, final Integer position,
            final boolean isActive, final boolean mandatory) {
        return new CodeValueData().setId(id).setName(name).setPosition(position).setDescription(description).setActive(isActive)
                .setMandatory(mandatory);
    }

    public static CodeValueData instance(final Long id, final String name, final String description, final boolean isActive) {
        Integer position = null;
        boolean mandatory = false;

        return new CodeValueData().setId(id).setName(name).setPosition(position).setDescription(description).setActive(isActive)
                .setMandatory(mandatory);
    }

    public static CodeValueData instance(final Long id, final String name) {
        String description = null;
        Integer position = null;
        Boolean active = null;
        Boolean mandatory = null;

        return new CodeValueData().setId(id).setName(name).setPosition(position).setDescription(description).setActive(active)
                .setMandatory(mandatory);
    }

    public static CodeValueData instance(final Long id, final String name, final Integer position, final String description,
            final boolean isActive, final boolean mandatory) {
        return new CodeValueData().setId(id).setName(name).setPosition(position).setDescription(description).setActive(isActive)
                .setMandatory(mandatory);
    }
}
