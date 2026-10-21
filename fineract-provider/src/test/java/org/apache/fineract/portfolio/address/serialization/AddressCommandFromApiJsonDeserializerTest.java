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
package org.apache.fineract.portfolio.address.serialization;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.List;
import org.apache.fineract.infrastructure.core.exception.PlatformApiDataValidationException;
import org.apache.fineract.infrastructure.core.serialization.FromJsonHelper;
import org.apache.fineract.portfolio.address.service.FieldConfigurationReadPlatformService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AddressCommandFromApiJsonDeserializerTest {

    @Mock
    private FieldConfigurationReadPlatformService fieldConfigurationReadPlatformService;

    private AddressCommandFromApiJsonDeserializer deserializer;

    @BeforeEach
    void setUp() {
        this.deserializer = new AddressCommandFromApiJsonDeserializer(new FromJsonHelper(), this.fieldConfigurationReadPlatformService);
    }

    @Test
    void acceptsPostalCodeWithinMaxLength() {
        when(this.fieldConfigurationReadPlatformService.retrieveFieldConfigurationList("ADDRESS")).thenReturn(List.of());
        final String json = """
                {
                  "addressTypeId": 1,
                  "postalCode": "12345-6789"
                }
                """;

        assertThatCode(() -> this.deserializer.validateForCreate(json, true)).doesNotThrowAnyException();
    }

    @Test
    void rejectsPostalCodeExceedingMaxLength() {
        when(this.fieldConfigurationReadPlatformService.retrieveFieldConfigurationList("ADDRESS")).thenReturn(List.of());
        final String json = """
                {
                  "addressTypeId": 1,
                  "postalCode": "%s"
                }
                """.formatted("A".repeat(21));

        assertThatThrownBy(() -> this.deserializer.validateForCreate(json, true))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }

    @Test
    void rejectsAddressLineExceedingMaxLength() {
        when(this.fieldConfigurationReadPlatformService.retrieveFieldConfigurationList("ADDRESS")).thenReturn(List.of());
        final String json = """
                {
                  "addressTypeId": 1,
                  "addressLine1": "%s"
                }
                """.formatted("A".repeat(101));

        assertThatThrownBy(() -> this.deserializer.validateForCreate(json, true))
                .isInstanceOf(PlatformApiDataValidationException.class);
    }
}
