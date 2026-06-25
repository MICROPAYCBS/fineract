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
package org.apache.fineract.portfolio.customerclass.service;

import org.apache.fineract.portfolio.client.domain.Client;
import org.apache.fineract.portfolio.customerclass.domain.CustomerClass;

public interface CustomerClassClientValidationService {

    /**
     * Validates that a client may be assigned the given customer class (legal form, age when configured, restriction, blacklist).
     */
    void validateAssignment(CustomerClass customerClass, Client client);

    /**
     * Validates that the client may leave their current class when {@code newCustomerClass} is assigned.
     */
    void validateReclassification(CustomerClass currentCustomerClass, CustomerClass newCustomerClass);

    /**
     * Validates KYC readiness (photo, signature, documents, compliance) before client activation.
     */
    void validateReadinessForActivation(CustomerClass customerClass, Client client);
}
