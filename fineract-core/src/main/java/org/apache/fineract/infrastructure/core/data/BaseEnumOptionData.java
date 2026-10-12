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
package org.apache.fineract.infrastructure.core.data;

import io.swagger.v3.oas.annotations.media.Schema;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * <p>
 * Immutable data object representing generic enumeration value.
 * </p>
 */
@Getter
@EqualsAndHashCode
@AllArgsConstructor
public abstract class BaseEnumOptionData<T> implements Serializable {

    @Schema(description = "Enumeration identifier.", example = "1")
    protected T id;

    @Schema(description = "Enumeration code.", example = "gender.male")
    protected final String code;

    @Schema(description = "Enumeration label.", example = "Male")
    protected final String value;
}
