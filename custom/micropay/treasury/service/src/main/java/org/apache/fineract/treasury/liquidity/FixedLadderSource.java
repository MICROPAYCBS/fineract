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
package org.apache.fineract.treasury.liquidity;

import static org.apache.fineract.treasury.api.TreasuryApiConstants.MODULE_ENABLED_PROPERTY;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.treasury.domain.TreasuryLadderItem;
import org.apache.fineract.treasury.domain.TreasuryLadderItemRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Configured ladder items (salaries, rent, tax): a one-off item on its date, a monthly item on its day of each month
 * (the month's last day when shorter), from the as-of date to the ladder horizon and within the item's active window.
 * An office run takes that office's items and the institution-wide ones.
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(value = MODULE_ENABLED_PROPERTY, havingValue = "true")
public class FixedLadderSource implements LadderSource {

    private final TreasuryLadderItemRepository itemRepository;

    @Override
    public LadderSourceCode code() {
        return LadderSourceCode.FIXED;
    }

    @Override
    public List<LadderFlow> flows(final LadderContext context) {
        final List<TreasuryLadderItem> items = this.itemRepository.findByCurrencyCodeOrderByIdAsc(context.currencyCode()).stream()
                .filter(i -> context.officeId() == null || i.getOfficeId() == null || context.officeId().equals(i.getOfficeId())).toList();
        final List<LadderFlow> flows = new ArrayList<>();
        for (final TreasuryLadderItem item : items) {
            for (final LocalDate date : dates(item, context.asOf(), context.horizonEnd())) {
                flows.add(LadderFlow.contractual(LadderSourceCode.FIXED, item.getDirection().name(), item.getId(), item.getName(), date,
                        item.getDirection() == LadderItemDirection.INFLOW ? item.getAmount() : item.getAmount().negate()));
            }
        }
        return flows;
    }

    /** The dates of the item from {@code from} to {@code to}, both included, within its active window. */
    public static List<LocalDate> dates(final TreasuryLadderItem item, final LocalDate from, final LocalDate to) {
        final LocalDate start = item.getActiveFrom().isAfter(from) ? item.getActiveFrom() : from;
        final LocalDate end = item.getActiveTo() != null && item.getActiveTo().isBefore(to) ? item.getActiveTo() : to;
        final List<LocalDate> dates = new ArrayList<>();
        if (end.isBefore(start)) {
            return dates;
        }
        if (item.getOneOffDate() != null) {
            if (!item.getOneOffDate().isBefore(start) && !item.getOneOffDate().isAfter(end)) {
                dates.add(item.getOneOffDate());
            }
            return dates;
        }
        for (YearMonth month = YearMonth.from(start); !month.atDay(1).isAfter(end); month = month.plusMonths(1)) {
            final LocalDate date = month.atDay(Math.min(item.getDayOfMonth(), month.lengthOfMonth()));
            if (!date.isBefore(start) && !date.isAfter(end)) {
                dates.add(date);
            }
        }
        return dates;
    }
}
