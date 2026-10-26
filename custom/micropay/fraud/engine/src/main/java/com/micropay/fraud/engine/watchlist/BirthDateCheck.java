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
package com.micropay.fraud.engine.watchlist;

import java.time.LocalDate;
import java.util.List;

/**
 * How a client's date of birth compares with a listed person's.
 */
public enum BirthDateCheck {

    /** The same full date, or the same year when the list only gives a year. */
    MATCH,
    /** Within a year of a listed date; lists often only know an approximate year. */
    CLOSE,
    /** More than a year away from every listed date: most likely a different person with a similar name. */
    MISMATCH,
    /** Either side has no date of birth. */
    UNKNOWN;

    /** Score points added for a matching full date of birth. */
    static final int MATCH_BONUS = 5;
    /** Score points taken off when the dates of birth clearly differ. */
    static final int MISMATCH_PENALTY = 25;

    public static BirthDateCheck of(LocalDate dateOfBirth, List<String> listed) {
        if (dateOfBirth == null || listed.isEmpty()) {
            return UNKNOWN;
        }
        boolean close = false;
        for (String date : listed) {
            final int year = BirthDates.year(date);
            if (date.length() == 4 ? year == dateOfBirth.getYear() : date.equals(dateOfBirth.toString())) {
                return MATCH;
            }
            if (Math.abs(year - dateOfBirth.getYear()) <= 1) {
                close = true;
            }
        }
        return close ? CLOSE : MISMATCH;
    }

    public int adjust(int nameScore) {
        return switch (this) {
            case MATCH -> Math.min(100, nameScore + MATCH_BONUS);
            case MISMATCH -> Math.max(0, nameScore - MISMATCH_PENALTY);
            default -> nameScore;
        };
    }
}
