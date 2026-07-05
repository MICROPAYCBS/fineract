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
import java.util.List;
import java.util.Map;
import org.apache.fineract.infrastructure.core.data.EnumOptionData;

public class AccountNumberFormatData implements Serializable {

    private final Long id;

    private final EnumOptionData accountType;
    private final EnumOptionData prefixType;

    // template options
    private List<EnumOptionData> accountTypeOptions;
    private Map<String, List<EnumOptionData>> prefixTypeOptions;
    private List<EnumOptionData> sequenceScopeOptions;
    private List<EnumOptionData> checkDigitAlgorithmOptions;
    private List<String> segmentTokenOptions;

    private String prefixCharacter;
    private String formatPattern;
    private EnumOptionData sequenceScope;
    private EnumOptionData checkDigitAlgorithm;
    private Boolean structuredEnabled;

    public AccountNumberFormatData(final Long id, final EnumOptionData accountType, final EnumOptionData prefixType,
            final String prefixCharacter, final String formatPattern, final EnumOptionData sequenceScope,
            final EnumOptionData checkDigitAlgorithm, final Boolean structuredEnabled) {
        this(id, accountType, prefixType, null, null, prefixCharacter, formatPattern, sequenceScope, checkDigitAlgorithm,
                structuredEnabled, null, null, null);
    }

    public AccountNumberFormatData(final List<EnumOptionData> accountTypeOptions, Map<String, List<EnumOptionData>> prefixTypeOptions) {
        this(null, null, null, accountTypeOptions, prefixTypeOptions, null, null, null, null, null, null, null, null);
    }

    public void templateOnTop(List<EnumOptionData> accountTypeOptions, Map<String, List<EnumOptionData>> prefixTypeOptions) {
        this.accountTypeOptions = accountTypeOptions;
        this.prefixTypeOptions = prefixTypeOptions;
    }

    public void structuredTemplateOnTop(final List<EnumOptionData> sequenceScopeOptions,
            final List<EnumOptionData> checkDigitAlgorithmOptions, final List<String> segmentTokenOptions) {
        this.sequenceScopeOptions = sequenceScopeOptions;
        this.checkDigitAlgorithmOptions = checkDigitAlgorithmOptions;
        this.segmentTokenOptions = segmentTokenOptions;
    }

    private AccountNumberFormatData(final Long id, final EnumOptionData accountType, final EnumOptionData prefixType,
            final List<EnumOptionData> accountTypeOptions, Map<String, List<EnumOptionData>> prefixTypeOptions,
            final String prefixCharacter, final String formatPattern, final EnumOptionData sequenceScope,
            final EnumOptionData checkDigitAlgorithm, final Boolean structuredEnabled, final List<EnumOptionData> sequenceScopeOptions,
            final List<EnumOptionData> checkDigitAlgorithmOptions, final List<String> segmentTokenOptions) {
        this.id = id;
        this.accountType = accountType;
        this.prefixType = prefixType;
        this.accountTypeOptions = accountTypeOptions;
        this.prefixTypeOptions = prefixTypeOptions;
        this.prefixCharacter = prefixCharacter;
        this.formatPattern = formatPattern;
        this.sequenceScope = sequenceScope;
        this.checkDigitAlgorithm = checkDigitAlgorithm;
        this.structuredEnabled = structuredEnabled;
        this.sequenceScopeOptions = sequenceScopeOptions;
        this.checkDigitAlgorithmOptions = checkDigitAlgorithmOptions;
        this.segmentTokenOptions = segmentTokenOptions;
    }

    public Long getId() {
        return this.id;
    }

    public EnumOptionData getAccountType() {
        return this.accountType;
    }

    public EnumOptionData getPrefixType() {
        return this.prefixType;
    }

    public List<EnumOptionData> getAccountTypeOptions() {
        return this.accountTypeOptions;
    }

    public Map<String, List<EnumOptionData>> getPrefixTypeOptions() {
        return this.prefixTypeOptions;
    }

    public String getPrefixCharacter() {
        return this.prefixCharacter;
    }

    public String getFormatPattern() {
        return this.formatPattern;
    }

    public EnumOptionData getSequenceScope() {
        return this.sequenceScope;
    }

    public EnumOptionData getCheckDigitAlgorithm() {
        return this.checkDigitAlgorithm;
    }

    public Boolean getStructuredEnabled() {
        return this.structuredEnabled;
    }

    public List<EnumOptionData> getSequenceScopeOptions() {
        return this.sequenceScopeOptions;
    }

    public List<EnumOptionData> getCheckDigitAlgorithmOptions() {
        return this.checkDigitAlgorithmOptions;
    }

    public List<String> getSegmentTokenOptions() {
        return this.segmentTokenOptions;
    }
}
