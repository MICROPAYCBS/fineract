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
package org.apache.fineract.portfolio.client.service;

import com.google.gson.JsonElement;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

/**
 * Default when no custom identity-type rules are installed: identifiers are not validated beyond the upstream checks.
 */
@Service
@ConditionalOnMissingBean(value = ClientIdentifierValidationService.class, ignored = NoOpClientIdentifierValidationService.class)
public class NoOpClientIdentifierValidationService implements ClientIdentifierValidationService {

    @Override
    public void validateDocumentKey(final Long documentTypeId, final String documentKey) {
        // no-op
    }

    @Override
    public void validateAtLeastOneIdentifierForPersonCreate(final JsonElement clientCreateElement, final Integer legalFormId) {
        // no-op
    }

    @Override
    public void validateAtLeastOneIdentifierForPersonClient(final Long clientId, final Integer legalFormId) {
        // no-op
    }
}
