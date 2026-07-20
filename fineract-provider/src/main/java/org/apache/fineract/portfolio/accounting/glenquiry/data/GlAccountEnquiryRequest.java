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
package org.apache.fineract.portfolio.accounting.glenquiry.data;

import lombok.Builder;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

@Getter
@Builder
public class GlAccountEnquiryRequest {

    private final String glPrefix;
    private final String ledgerNumber;
    private final Long officeId;
    private final Long departmentId;
    private final String currencyCode;
    private final Boolean disabled;

    public boolean hasAnyFilter() {
        return StringUtils.isNotBlank(this.glPrefix) //
                || StringUtils.isNotBlank(this.ledgerNumber) //
                || this.officeId != null //
                || this.departmentId != null //
                || StringUtils.isNotBlank(this.currencyCode) //
                || this.disabled != null;
    }
}
