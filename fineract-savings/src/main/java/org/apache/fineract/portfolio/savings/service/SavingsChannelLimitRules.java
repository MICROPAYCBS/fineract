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
package org.apache.fineract.portfolio.savings.service;

import java.math.BigDecimal;
import org.apache.fineract.infrastructure.core.data.DataValidatorBuilder;
import org.apache.fineract.portfolio.savings.data.SavingsChannelLimitValues;

public final class SavingsChannelLimitRules {

    private SavingsChannelLimitRules() {}

    public static BigDecimal effective(final BigDecimal customer, final BigDecimal ceiling) {
        if (customer == null) {
            return ceiling;
        }
        if (ceiling == null) {
            return customer;
        }
        return customer.min(ceiling);
    }

    public static Integer effective(final Integer customer, final Integer ceiling) {
        if (customer == null) {
            return ceiling;
        }
        if (ceiling == null) {
            return customer;
        }
        return Math.min(customer, ceiling);
    }

    public static SavingsChannelLimitValues effective(final SavingsChannelLimitValues customer, final SavingsChannelLimitValues ceiling) {
        final SavingsChannelLimitValues customerValues = customer == null ? SavingsChannelLimitValues.none() : customer;
        final SavingsChannelLimitValues ceilingValues = ceiling == null ? SavingsChannelLimitValues.none() : ceiling;
        return new SavingsChannelLimitValues(effective(customerValues.maxPerTxn(), ceilingValues.maxPerTxn()),
                effective(customerValues.maxPerDay(), ceilingValues.maxPerDay()),
                effective(customerValues.maxPerMonth(), ceilingValues.maxPerMonth()),
                effective(customerValues.maxCountPerDay(), ceilingValues.maxCountPerDay()),
                effective(customerValues.maxCountPerMonth(), ceilingValues.maxCountPerMonth()));
    }

    /**
     * A null cap is unlimited, so replacing a number with null, or raising a number, is less restrictive.
     */
    public static boolean lessRestrictive(final BigDecimal current, final BigDecimal proposed) {
        if (proposed == null) {
            return current != null;
        }
        if (current == null) {
            return false;
        }
        return proposed.compareTo(current) > 0;
    }

    public static boolean lessRestrictive(final Integer current, final Integer proposed) {
        if (proposed == null) {
            return current != null;
        }
        if (current == null) {
            return false;
        }
        return proposed > current;
    }

    public static boolean anyLessRestrictive(final SavingsChannelLimitValues current, final SavingsChannelLimitValues proposed) {
        final SavingsChannelLimitValues currentValues = current == null ? SavingsChannelLimitValues.none() : current;
        return lessRestrictive(currentValues.maxPerTxn(), proposed.maxPerTxn())
                || lessRestrictive(currentValues.maxPerDay(), proposed.maxPerDay())
                || lessRestrictive(currentValues.maxPerMonth(), proposed.maxPerMonth())
                || lessRestrictive(currentValues.maxCountPerDay(), proposed.maxCountPerDay())
                || lessRestrictive(currentValues.maxCountPerMonth(), proposed.maxCountPerMonth());
    }

    public static BigDecimal remaining(final BigDecimal cap, final BigDecimal used) {
        if (cap == null) {
            return null;
        }
        final BigDecimal left = cap.subtract(used == null ? BigDecimal.ZERO : used);
        return left.signum() < 0 ? BigDecimal.ZERO : left;
    }

    public static Integer remaining(final Integer cap, final int used) {
        if (cap == null) {
            return null;
        }
        return Math.max(0, cap - used);
    }

    public static boolean exceedsCeiling(final BigDecimal customer, final BigDecimal ceiling) {
        return customer != null && ceiling != null && customer.compareTo(ceiling) > 0;
    }

    public static boolean exceedsCeiling(final Integer customer, final Integer ceiling) {
        return customer != null && ceiling != null && customer > ceiling;
    }

    public static void validateNonNegative(final BigDecimal amount, final DataValidatorBuilder validator, final String parameter) {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) < 0) {
            validator.reset().parameter(parameter).value(amount).failWithCode("not.zero.or.greater");
        }
    }

    public static void validateNonNegative(final Integer count, final DataValidatorBuilder validator, final String parameter) {
        if (count != null && count < 0) {
            validator.reset().parameter(parameter).value(count).failWithCode("not.zero.or.greater");
        }
    }

    public static void validateOrdering(final SavingsChannelLimitValues values, final DataValidatorBuilder validator,
            final String prefix) {
        if (values == null) {
            return;
        }
        if (values.maxPerTxn() != null && values.maxPerDay() != null && values.maxPerTxn().compareTo(values.maxPerDay()) > 0) {
            validator.reset().parameter(prefix + "PerTxn").value(values.maxPerTxn()).failWithCode("must.not.exceed.daily");
        }
        if (values.maxPerDay() != null && values.maxPerMonth() != null && values.maxPerDay().compareTo(values.maxPerMonth()) > 0) {
            validator.reset().parameter(prefix + "PerDay").value(values.maxPerDay()).failWithCode("must.not.exceed.monthly");
        }
        if (values.maxPerTxn() != null && values.maxPerMonth() != null && values.maxPerTxn().compareTo(values.maxPerMonth()) > 0) {
            validator.reset().parameter(prefix + "PerTxn").value(values.maxPerTxn()).failWithCode("must.not.exceed.monthly");
        }
        if (values.maxCountPerDay() != null && values.maxCountPerMonth() != null && values.maxCountPerDay() > values.maxCountPerMonth()) {
            validator.reset().parameter(prefix + "CountPerDay").value(values.maxCountPerDay()).failWithCode("must.not.exceed.monthly");
        }
    }

    public static void validateWithinCeiling(final SavingsChannelLimitValues customer, final SavingsChannelLimitValues ceiling,
            final DataValidatorBuilder validator, final String prefix) {
        if (customer == null || ceiling == null) {
            return;
        }
        if (exceedsCeiling(customer.maxPerTxn(), ceiling.maxPerTxn())) {
            validator.reset().parameter(prefix + "PerTxn").value(customer.maxPerTxn()).failWithCode("exceeds.ceiling");
        }
        if (exceedsCeiling(customer.maxPerDay(), ceiling.maxPerDay())) {
            validator.reset().parameter(prefix + "PerDay").value(customer.maxPerDay()).failWithCode("exceeds.ceiling");
        }
        if (exceedsCeiling(customer.maxPerMonth(), ceiling.maxPerMonth())) {
            validator.reset().parameter(prefix + "PerMonth").value(customer.maxPerMonth()).failWithCode("exceeds.ceiling");
        }
        if (exceedsCeiling(customer.maxCountPerDay(), ceiling.maxCountPerDay())) {
            validator.reset().parameter(prefix + "CountPerDay").value(customer.maxCountPerDay()).failWithCode("exceeds.ceiling");
        }
        if (exceedsCeiling(customer.maxCountPerMonth(), ceiling.maxCountPerMonth())) {
            validator.reset().parameter(prefix + "CountPerMonth").value(customer.maxCountPerMonth()).failWithCode("exceeds.ceiling");
        }
    }
}
