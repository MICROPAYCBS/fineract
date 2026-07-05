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
package org.apache.fineract.infrastructure.accountnumberformat.domain;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public final class StructuredAccountNumberFormatEnumerations {

    private StructuredAccountNumberFormatEnumerations() {
    }

    public static List<EnumOptionData> sequenceScopeOptions() {
        final List<EnumOptionData> options = new ArrayList<>();
        for (final AccountNumberSequenceScope scope : AccountNumberSequenceScope.values()) {
            options.add(new EnumOptionData(scope.getValue().longValue(), scope.getCode(), scope.name()));
        }
        return options;
    }

    public static List<EnumOptionData> checkDigitAlgorithmOptions() {
        final List<EnumOptionData> options = new ArrayList<>();
        for (final CheckDigitAlgorithm algorithm : CheckDigitAlgorithm.values()) {
            options.add(new EnumOptionData(algorithm.getValue().longValue(), algorithm.getCode(), algorithm.name()));
        }
        return options;
    }

    public static List<String> segmentTokenOptions() {
        return Arrays.asList("officeCode", "regionCode", "branchType", "productCode", "clientTypeCode", "entityTypeCode", "sequence",
                "checkDigit");
    }

    public static EnumOptionData sequenceScope(final Integer value) {
        final AccountNumberSequenceScope scope = AccountNumberSequenceScope.fromInt(value);
        if (scope == null) {
            return null;
        }
        return new EnumOptionData(scope.getValue().longValue(), scope.getCode(), scope.name());
    }

    public static EnumOptionData checkDigitAlgorithm(final Integer value) {
        final CheckDigitAlgorithm algorithm = CheckDigitAlgorithm.fromInt(value);
        return new EnumOptionData(algorithm.getValue().longValue(), algorithm.getCode(), algorithm.name());
    }
}
