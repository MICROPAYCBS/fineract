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
package org.apache.fineract.integrationtests.common;

/**
 * Names of Micropay {@code c_configuration} entries and permissions used by the fork integration tests.
 *
 * Mirrors {@code com.micropay.fineract.platform.configuration.MicropayGlobalConfigurationConstants} and
 * {@code org.apache.fineract.infrastructure.audit.api.OrganizationWideAuditConstants} in {@code custom/micropay}. The
 * integration tests deliberately have no compile dependency on custom modules: they exercise the HTTP contract of the
 * Micropay distribution, so the names are duplicated here and checked by the tests themselves.
 */
public final class MicropayConfigurationNames {

    public static final String ENFORCE_STRUCTURED_GL_CODES = "enforce-structured-gl-codes";
    public static final String STRUCTURED_GL_CODE_LENGTH = "structured-gl-code-length";
    public static final String ENABLE_ORGANIZATION_WIDE_AUDIT_VIEW = "enable-organization-wide-audit-view";
    public static final String VIEW_ORGANIZATION_AUDIT_PERMISSION = "VIEW_ORGANIZATION_AUDIT";

    private MicropayConfigurationNames() {}
}
