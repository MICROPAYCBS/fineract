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
package org.apache.fineract.infrastructure.dataqueries.service;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.fineract.infrastructure.core.service.database.DatabaseSpecificSQLGenerator;
import org.apache.fineract.infrastructure.core.service.database.DatabaseType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class DatatableColumnValidationWriteServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private DatabaseSpecificSQLGenerator sqlGenerator;

    @Test
    void postgresqlUpsertUsesOnConflict() {
        when(sqlGenerator.getDialect()).thenReturn(DatabaseType.POSTGRESQL);
        final DatatableColumnValidationWriteService service = new DatatableColumnValidationWriteService(jdbcTemplate, sqlGenerator);

        service.upsertColumnValidation("dt_client", "national_code", "^[A-Z]{2}$", "UG", "two letters");

        assertTrue(capturedSql().contains("ON CONFLICT"));
    }

    @Test
    void mysqlUpsertUsesOnDuplicateKey() {
        when(sqlGenerator.getDialect()).thenReturn(DatabaseType.MYSQL);
        final DatatableColumnValidationWriteService service = new DatatableColumnValidationWriteService(jdbcTemplate, sqlGenerator);

        service.upsertColumnValidation("dt_client", "national_code", "^[A-Z]{2}$", "UG", "two letters");

        assertTrue(capturedSql().contains("ON DUPLICATE KEY UPDATE"));
    }

    private String capturedSql() {
        final ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).update(sql.capture(), any(), any(), any(), any(), any());
        return sql.getValue();
    }
}
