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
package org.apache.fineract.infrastructure.accountnumberformat.data;

import java.io.Serializable;
import lombok.Builder;
import lombok.Getter;
import org.apache.fineract.infrastructure.accountnumberformat.domain.CheckDigitAlgorithm;
import org.apache.fineract.infrastructure.accountnumberformat.domain.EntityAccountType;
import org.apache.fineract.infrastructure.accountnumberformat.domain.AccountNumberSequenceScope;
import org.apache.fineract.organisation.office.domain.Office;

@Getter
@Builder
public class AccountNumberGenerationContext implements Serializable {

    private final EntityAccountType entityAccountType;
    private final Office office;
    private final String officeCode;
    private final String regionCode;
    private final String branchType;
    private final String productCode;
    private final String clientTypeCode;
    private final String entityTypeCode;
    private final String formatPattern;
    private final AccountNumberSequenceScope sequenceScope;
    private final CheckDigitAlgorithm checkDigitAlgorithm;
    private final boolean preview;
}
