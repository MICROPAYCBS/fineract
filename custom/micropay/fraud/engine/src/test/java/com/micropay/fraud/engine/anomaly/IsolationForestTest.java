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
package com.micropay.fraud.engine.anomaly;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class IsolationForestTest {

    @Test
    void outliersScoreHigherThanOrdinaryPoints() {
        final Random random = new Random(7);
        final List<double[]> data = new ArrayList<>();
        for (int i = 0; i < 2000; i++) {
            data.add(new double[] { random.nextGaussian(), random.nextGaussian(), random.nextGaussian() });
        }
        final IsolationForest forest = IsolationForest.train(data, 100, 256, 42L);

        final double ordinary = forest.score(new double[] { 0.1, -0.2, 0.0 });
        final double outlier = forest.score(new double[] { 8, -7, 9 });

        assertThat(ordinary).isLessThan(0.55);
        assertThat(outlier).isGreaterThan(0.7);
    }

    @Test
    void sameSeedGivesSameModel() {
        final List<double[]> data = List.of(new double[] { 1, 2 }, new double[] { 2, 3 }, new double[] { 3, 1 }, new double[] { 10, 10 });
        final double first = IsolationForest.train(data, 20, 4, 1L).score(new double[] { 10, 10 });
        final double second = IsolationForest.train(data, 20, 4, 1L).score(new double[] { 10, 10 });
        assertThat(first).isEqualTo(second);
    }

    @Test
    void averagePathLengthMatchesTheReferenceFormula() {
        assertThat(IsolationForest.averagePathLength(1)).isZero();
        assertThat(IsolationForest.averagePathLength(2)).isEqualTo(1.0);
        assertThat(IsolationForest.averagePathLength(256)).isBetween(10.2, 10.3);
    }
}
