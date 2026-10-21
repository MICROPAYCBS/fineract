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
package org.apache.fineract.useradministration.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.exception.PlatformDataIntegrityException;
import org.apache.fineract.infrastructure.security.service.PlatformSecurityContext;
import org.apache.fineract.useradministration.domain.PermissionRepository;
import org.apache.fineract.useradministration.domain.Role;
import org.apache.fineract.useradministration.domain.RoleRepository;
import org.apache.fineract.useradministration.serialization.PermissionsCommandFromApiJsonDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.jpa.JpaSystemException;

@ExtendWith(MockitoExtension.class)
class RoleWritePlatformServiceJpaRepositoryImplTest {

    private static final String ROLE_NAME = "Test";

    @Mock
    private PlatformSecurityContext context;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleDataValidator roleDataValidator;

    private RoleWritePlatformServiceJpaRepositoryImpl underTest;
    private JsonCommand command;

    @BeforeEach
    void setUp() {
        underTest = new RoleWritePlatformServiceJpaRepositoryImpl(context, roleRepository, mock(PermissionRepository.class),
                roleDataValidator, mock(PermissionsCommandFromApiJsonDeserializer.class));
        command = mock(JsonCommand.class);
        when(command.json()).thenReturn("{\"name\":\"Test\",\"description\":\"A test role\"}");
        when(command.stringValueOfParameterNamed("name")).thenReturn(ROLE_NAME);
        lenient().when(command.stringValueOfParameterNamed("description")).thenReturn("A test role");
    }

    @Test
    void createRoleMapsPostgreSqlDuplicateNameToDataIntegrityException() {
        when(roleRepository.saveAndFlush(any(Role.class))).thenThrow(duplicateKey("m_role_name_key"));

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> underTest.createRole(command));

        assertDuplicateName(exception);
    }

    @Test
    void createRoleMapsMySqlDuplicateNameToDataIntegrityException() {
        when(roleRepository.saveAndFlush(any(Role.class)))
                .thenThrow(new DataIntegrityViolationException("Duplicate entry 'Test' for key 'unq_name'"));

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> underTest.createRole(command));

        assertDuplicateName(exception);
    }

    @Test
    void updateRoleMapsPostgreSqlDuplicateNameToDataIntegrityException() {
        final Role existing = new Role("Existing", "A test role");
        when(roleRepository.findById(7L)).thenReturn(Optional.of(existing));
        when(command.isChangeInStringParameterNamed("name", "Existing")).thenReturn(true);
        when(roleRepository.saveAndFlush(any(Role.class))).thenThrow(duplicateKey("m_role_name_key"));

        final PlatformDataIntegrityException exception = assertThrows(PlatformDataIntegrityException.class,
                () -> underTest.updateRole(7L, command));

        assertDuplicateName(exception);
    }

    private static void assertDuplicateName(final PlatformDataIntegrityException exception) {
        assertEquals("error.msg.role.duplicate.name", exception.getGlobalisationMessageCode());
        assertEquals("Role with name `Test` already exists", exception.getDefaultUserMessage());
        assertEquals("name", exception.getParameterName());
    }

    private static JpaSystemException duplicateKey(final String constraintName) {
        final String message = "ERROR: duplicate key value violates unique constraint \"" + constraintName
                + "\"\n  Detail: Key (name)=(Test) already exists.";
        return new JpaSystemException(new RuntimeException(message));
    }
}
