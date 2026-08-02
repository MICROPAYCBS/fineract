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
package org.apache.fineract.portfolio.client.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.apache.fineract.portfolio.client.exception.ClientCannotBeDeletedHasAssociatedDataException.AssociatedDataType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ClientCannotBeDeletedHasAssociatedDataExceptionTest {

    private static final Long CLIENT_ID = 25L;

    @ParameterizedTest
    @EnumSource(AssociatedDataType.class)
    void fromForeignKeyConstraintName_mapsKnownConstraint(final AssociatedDataType type) {
        final String postgresMessage = "ERROR: update or delete on table \"m_client\" violates RESTRICT setting of foreign key constraint \""
                + type.foreignKeyConstraintName() + "\" on table \"example\"";

        assertEquals(type, AssociatedDataType.fromForeignKeyConstraintName(postgresMessage));
    }

    @Test
    void fromForeignKeyConstraintName_returnsNullForUnknownConstraint() {
        assertNull(AssociatedDataType.fromForeignKeyConstraintName("ERROR: unknown constraint"));
    }

    @Test
    void fromForeignKeyConstraintName_returnsNullForNullMessage() {
        assertNull(AssociatedDataType.fromForeignKeyConstraintName(null));
    }

    @ParameterizedTest
    @EnumSource(AssociatedDataType.class)
    void exceptionExposesSpecificErrorCodeAndMessage(final AssociatedDataType type) {
        final ClientCannotBeDeletedHasAssociatedDataException exception = new ClientCannotBeDeletedHasAssociatedDataException(CLIENT_ID,
                type);

        assertEquals(type.errorCode(), exception.getGlobalisationMessageCode());
        assertEquals(type.message(CLIENT_ID), exception.getDefaultUserMessage());
    }
}
