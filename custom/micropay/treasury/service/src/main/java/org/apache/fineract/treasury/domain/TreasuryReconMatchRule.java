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
package org.apache.fineract.treasury.domain;

/**
 * How a match was made: by one of the automatic rules, by hand, or by explaining a line (with or without an adjusting
 * entry).
 */
public enum TreasuryReconMatchRule {

    REFERENCE, //
    SAME_DATE, //
    DATE_WINDOW, //
    MANUAL, //
    EXPLANATION; //

    public boolean isAutomatic() {
        return this == REFERENCE || this == SAME_DATE || this == DATE_WINDOW;
    }
}
