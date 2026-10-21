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
package org.apache.fineract.scoring.exception;

import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.exception.AbstractPlatformDomainRuleException;

public class ScoringLimitExceededException extends AbstractPlatformDomainRuleException {

    public ScoringLimitExceededException(final Long loanId, final String policyCode, final BigDecimal limit, final BigDecimal used,
            final BigDecimal amount) {
        super("error.msg.scoring.limit.exceeded", "Loan " + loanId + " of " + amount + " exceeds the pre-approved limit under " + policyCode
                + ": limit " + limit + ", already used " + used, loanId, policyCode, limit, used, amount);
    }

    public ScoringLimitExceededException(final Long loanId, final String policyCode) {
        super("error.msg.scoring.limit.missing",
                "Loan " + loanId + " needs a current pre-approved limit under " + policyCode + " and the client has none", loanId,
                policyCode);
    }
}
