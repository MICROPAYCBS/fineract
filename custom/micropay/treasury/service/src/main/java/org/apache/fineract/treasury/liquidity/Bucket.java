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

/**
 * A ladder bucket: the days from {@code fromDay} to {@code toDay} after the as-of date, both included; {@code toDay}
 * null for the open-ended last bucket.
 */
public record Bucket(int seq, String code, String label, int fromDay, Integer toDay) {

    public boolean contains(final long day) {
        return day >= this.fromDay && (this.toDay == null || day <= this.toDay);
    }

    /** Calendar days the bucket spans; zero for an open-ended one. */
    public int days() {
        return this.toDay == null ? 0 : this.toDay - this.fromDay + 1;
    }
}
