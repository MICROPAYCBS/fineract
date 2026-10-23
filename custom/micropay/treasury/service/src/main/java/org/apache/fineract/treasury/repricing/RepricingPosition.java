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
package org.apache.fineract.treasury.repricing;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.apache.fineract.treasury.liquidity.LadderSourceCode;

/**
 * Principal outstanding that reprices on a date: in the bucket of {@code bucketCode} when given (distributions), else
 * of {@code repricingDate} (the first bucket when null or past). NON_SENSITIVE positions are in no bucket.
 */
public record RepricingPosition(LadderSourceCode source, String lineCode, Long productId, String productName, RepricingSide side,
        RateType rateType, LocalDate repricingDate, String bucketCode, BigDecimal amount) {
}
