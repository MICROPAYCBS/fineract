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
package org.apache.fineract.portfolio.savings.exception;

import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class SavingsInterestPostingGlMappingMissingException extends AbstractPlatformDomainRuleException {

    public SavingsInterestPostingGlMappingMissingException(final Long savingsAccountId, final Long productId, final String accountingRule,
            final String transactionType, final String side, final String mapping) {
        super("error.msg.savings.interest.posting.gl.mapping.missing",
                "Interest not posted for savings account " + savingsAccountId + ": product " + productId + " uses " + accountingRule
                        + " accounting but has no GL account mapped for " + mapping + " (" + side + " side of " + transactionType
                        + "). Map it on the savings product and run the job again.",
                savingsAccountId, productId, accountingRule, transactionType, side, mapping);
    }
}
