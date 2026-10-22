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
package org.apache.fineract.treasury.data;

import org.apache.fineract.treasury.domain.TreasuryStatementFormat;

public record TreasuryStatementFormatData(Long id, String code, String name, String formatType, String delimiter, int headerRows,
        String dateFormat, String decimalSeparator, String dateColumn, String valueDateColumn, String descriptionColumn,
        String referenceColumn, String debitColumn, String creditColumn, String amountColumn, String balanceColumn, boolean invertAmount,
        boolean active) {

    public static TreasuryStatementFormatData from(final TreasuryStatementFormat format) {
        return new TreasuryStatementFormatData(format.getId(), format.getCode(), format.getName(), format.getFormatType().name(),
                format.getDelimiter(), format.getHeaderRows(), format.getDateFormat(), format.getDecimalSeparator(), format.getDateColumn(),
                format.getValueDateColumn(), format.getDescriptionColumn(), format.getReferenceColumn(), format.getDebitColumn(),
                format.getCreditColumn(), format.getAmountColumn(), format.getBalanceColumn(), format.isInvertAmount(), format.isActive());
    }
}
