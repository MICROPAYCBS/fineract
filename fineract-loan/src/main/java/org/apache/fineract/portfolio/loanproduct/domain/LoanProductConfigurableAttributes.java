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
package org.apache.fineract.portfolio.loanproduct.domain;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.Objects;
import org.apache.fineract.infrastructure.core.api.JsonCommand;
import org.apache.fineract.infrastructure.core.domain.AbstractPersistableCustom;
import org.apache.fineract.portfolio.loanproduct.LoanProductConstants;

@Entity
@Table(name = "m_product_loan_configurable_attributes")
public class LoanProductConfigurableAttributes extends AbstractPersistableCustom<Long> implements Serializable {

    @OneToOne
    @JoinColumn(name = "loan_product_id", nullable = false)
    private LoanProduct loanProduct;

    @Column(name = "amortization_method_enum")
    private Boolean amortizationType;

    @Column(name = "interest_method_enum")
    private Boolean interestType;

    @Column(name = "loan_transaction_strategy_code")
    private Boolean transactionProcessingStrategyCode;

    @Column(name = "interest_calculated_in_period_enum")
    private Boolean interestCalculationPeriodType;

    @Column(name = "arrearstolerance_amount")
    private Boolean inArrearsTolerance;

    @Column(name = "repay_every")
    private Boolean repaymentEvery;

    @Column(name = "moratorium")
    private Boolean graceOnPrincipalAndInterestPayment;

    @Column(name = "grace_on_arrears_ageing")
    private Boolean graceOnArrearsAgeing;

    private static final String[] supportedLoanConfigurableAttributes = { LoanProductConstants.amortizationTypeParamName,
            LoanProductConstants.interestTypeParamName, LoanProductConstants.transactionProcessingStrategyCodeParamName,
            LoanProductConstants.interestCalculationPeriodTypeParamName, LoanProductConstants.inArrearsToleranceParamName,
            LoanProductConstants.repaymentEveryParamName, LoanProductConstants.graceOnPrincipalAndInterestPaymentParamName,
            LoanProductConstants.GRACE_ON_ARREARS_AGEING_PARAMETER_NAME };

    public static LoanProductConfigurableAttributes createFrom(JsonCommand command) {
        final JsonObject overrides = overridesObject(command);
        return new LoanProductConfigurableAttributes( //
                readFlag(overrides, LoanProductConstants.amortizationTypeParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.interestTypeParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.transactionProcessingStrategyCodeParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.interestCalculationPeriodTypeParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.inArrearsToleranceParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.repaymentEveryParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.graceOnPrincipalAndInterestPaymentParamName, Boolean.TRUE), //
                readFlag(overrides, LoanProductConstants.GRACE_ON_ARREARS_AGEING_PARAMETER_NAME, Boolean.TRUE));
    }

    /** The allowAttributeOverrides object of the request, or null when it is absent, JSON null or not an object. */
    public static JsonObject overridesObject(final JsonCommand command) {
        final JsonElement element = command.parsedJson().getAsJsonObject().get(LoanProductConstants.allowAttributeOverridesParamName);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    /** Value of one flag, or the fallback when the object or the key is missing or JSON null. */
    public static Boolean readFlag(final JsonObject overrides, final String key, final Boolean fallback) {
        if (overrides == null) {
            return fallback;
        }
        final JsonElement value = overrides.get(key);
        return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
    }

    public static LoanProductConfigurableAttributes copyOf(final LoanProductConfigurableAttributes other) {
        return new LoanProductConfigurableAttributes(other.amortizationType, other.interestType, other.transactionProcessingStrategyCode,
                other.interestCalculationPeriodType, other.inArrearsTolerance, other.repaymentEvery,
                other.graceOnPrincipalAndInterestPayment, other.graceOnArrearsAgeing);
    }

    public boolean sameFlagsAs(final LoanProductConfigurableAttributes other) {
        return Objects.equals(amortizationType, other.amortizationType) && Objects.equals(interestType, other.interestType)
                && Objects.equals(transactionProcessingStrategyCode, other.transactionProcessingStrategyCode)
                && Objects.equals(interestCalculationPeriodType, other.interestCalculationPeriodType)
                && Objects.equals(inArrearsTolerance, other.inArrearsTolerance) && Objects.equals(repaymentEvery, other.repaymentEvery)
                && Objects.equals(graceOnPrincipalAndInterestPayment, other.graceOnPrincipalAndInterestPayment)
                && Objects.equals(graceOnArrearsAgeing, other.graceOnArrearsAgeing);
    }

    public void updateLoanProduct(final LoanProduct loanProduct) {
        this.loanProduct = loanProduct;
    }

    public static LoanProductConfigurableAttributes populateDefaultsForConfigurableAttributes() {
        final Boolean amortization = true;
        final Boolean interestMethod = true;
        final Boolean transactionProcessingStrategy = true;
        final Boolean interestCalcPeriod = true;
        final Boolean arrearsTolerance = true;
        final Boolean repaymentEvery = true;
        final Boolean graceOnPrincipalAndInterestPayment = true;
        final Boolean graceOnArrearsAging = true;

        return new LoanProductConfigurableAttributes(amortization, interestMethod, transactionProcessingStrategy, interestCalcPeriod,
                arrearsTolerance, repaymentEvery, graceOnPrincipalAndInterestPayment, graceOnArrearsAging);
    }

    public LoanProductConfigurableAttributes(Boolean amortization, Boolean interestMethod, Boolean transactionProcessingStrategyCode,
            Boolean interestCalcPeriod, Boolean arrearsTolerance, Boolean repaymentEvery, Boolean graceOnPrincipalAndInterestPayment,
            Boolean graceOnArrearsAging) {
        this.amortizationType = amortization;
        this.interestType = interestMethod;
        this.inArrearsTolerance = arrearsTolerance;
        this.graceOnArrearsAgeing = graceOnArrearsAging;
        this.interestCalculationPeriodType = interestCalcPeriod;
        this.graceOnPrincipalAndInterestPayment = graceOnPrincipalAndInterestPayment;
        this.repaymentEvery = repaymentEvery;
        this.transactionProcessingStrategyCode = transactionProcessingStrategyCode;
    }

    protected LoanProductConfigurableAttributes() {

    }

    public static String[] getAllowedLoanConfigurableAttributes() {
        return supportedLoanConfigurableAttributes;
    }

    public LoanProduct getLoanProduct() {
        return loanProduct;
    }

    public Boolean getAmortizationBoolean() {
        return amortizationType;
    }

    public Boolean getInterestMethodBoolean() {
        return interestType;
    }

    public Boolean getTransactionProcessingStrategyBoolean() {
        return transactionProcessingStrategyCode;
    }

    public Boolean getInterestCalcPeriodBoolean() {
        return interestCalculationPeriodType;
    }

    public Boolean getArrearsToleranceBoolean() {
        return inArrearsTolerance;
    }

    public Boolean getRepaymentEveryBoolean() {
        return repaymentEvery;
    }

    public Boolean getGraceOnPrincipalAndInterestPaymentBoolean() {
        return graceOnPrincipalAndInterestPayment;
    }

    public Boolean getGraceOnArrearsAgingBoolean() {
        return graceOnArrearsAgeing;
    }

    public void setLoanProduct(LoanProduct loanProduct) {
        this.loanProduct = loanProduct;
    }

    public void setAmortizationType(Boolean amortizationType) {
        this.amortizationType = amortizationType;
    }

    public void setInterestType(Boolean interestType) {
        this.interestType = interestType;
    }

    public void setTransactionProcessingStrategyCode(Boolean transactionProcessingStrategyCode) {
        this.transactionProcessingStrategyCode = transactionProcessingStrategyCode;
    }

    public void setInterestCalculationPeriodType(Boolean interestCalculationPeriodType) {
        this.interestCalculationPeriodType = interestCalculationPeriodType;
    }

    public void setInArrearsTolerance(Boolean inArrearsTolerance) {
        this.inArrearsTolerance = inArrearsTolerance;
    }

    public void setRepaymentEvery(Boolean repaymentEvery) {
        this.repaymentEvery = repaymentEvery;
    }

    public void setGraceOnPrincipalAndInterestPayment(Boolean graceOnPrincipalAndInterestPayment) {
        this.graceOnPrincipalAndInterestPayment = graceOnPrincipalAndInterestPayment;
    }

    public void setGraceOnArrearsAgeing(Boolean graceOnArrearsAgeing) {
        this.graceOnArrearsAgeing = graceOnArrearsAgeing;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof LoanProductConfigurableAttributes)) {
            return false;
        }

        LoanProductConfigurableAttributes that = (LoanProductConfigurableAttributes) o;

        return Objects.equals(loanProduct, that.loanProduct) && Objects.equals(amortizationType, that.amortizationType)
                && Objects.equals(interestType, that.interestType)
                && Objects.equals(transactionProcessingStrategyCode, that.transactionProcessingStrategyCode)
                && Objects.equals(interestCalculationPeriodType, that.interestCalculationPeriodType)
                && Objects.equals(inArrearsTolerance, that.inArrearsTolerance) && Objects.equals(repaymentEvery, that.repaymentEvery)
                && Objects.equals(graceOnPrincipalAndInterestPayment, that.graceOnPrincipalAndInterestPayment)
                && Objects.equals(graceOnArrearsAgeing, that.graceOnArrearsAgeing);
    }

    @Override
    public int hashCode() {
        return Objects.hash(loanProduct, amortizationType, interestType, transactionProcessingStrategyCode, interestCalculationPeriodType,
                inArrearsTolerance, repaymentEvery, graceOnPrincipalAndInterestPayment, graceOnArrearsAgeing);
    }
}
