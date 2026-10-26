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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Isolation forest (Liu, Ting and Zhou, 2008): unusual points are isolated by fewer random splits than ordinary ones.
 * It needs no labelled fraud cases, which suits a start without fraud history. Scores run 0 to 1; around 0.5 is
 * ordinary and values towards 1 are increasingly unusual.
 */
public final class IsolationForest {

    private static final double EULER_GAMMA = 0.5772156649;

    private final List<Node> trees;
    private final int sampleSize;

    private IsolationForest(List<Node> trees, int sampleSize) {
        this.trees = trees;
        this.sampleSize = sampleSize;
    }

    public static IsolationForest train(List<double[]> data, int treeCount, int sampleSize, long seed) {
        if (data.isEmpty()) {
            throw new IllegalArgumentException("Cannot train on no data");
        }
        final Random random = new Random(seed);
        final int effectiveSample = Math.min(sampleSize, data.size());
        final int heightLimit = (int) Math.ceil(Math.log(effectiveSample) / Math.log(2));
        final List<Node> trees = new ArrayList<>(treeCount);
        for (int t = 0; t < treeCount; t++) {
            final List<double[]> sample = new ArrayList<>(effectiveSample);
            for (int i = 0; i < effectiveSample; i++) {
                sample.add(data.get(random.nextInt(data.size())));
            }
            trees.add(build(sample, 0, heightLimit, random));
        }
        return new IsolationForest(trees, effectiveSample);
    }

    public double score(double[] point) {
        double totalPath = 0;
        for (Node tree : trees) {
            totalPath += pathLength(tree, point, 0);
        }
        final double averagePath = totalPath / trees.size();
        return Math.pow(2, -averagePath / averagePathLength(sampleSize));
    }

    private static Node build(List<double[]> data, int depth, int heightLimit, Random random) {
        if (depth >= heightLimit || data.size() <= 1) {
            return Node.leaf(data.size());
        }
        final int dimensions = data.get(0).length;
        // Try a few random dimensions to find one that still varies in this branch
        for (int attempt = 0; attempt < dimensions; attempt++) {
            final int dim = random.nextInt(dimensions);
            double min = Double.POSITIVE_INFINITY;
            double max = Double.NEGATIVE_INFINITY;
            for (double[] row : data) {
                min = Math.min(min, row[dim]);
                max = Math.max(max, row[dim]);
            }
            if (max > min) {
                final double split = min + random.nextDouble() * (max - min);
                final List<double[]> left = new ArrayList<>();
                final List<double[]> right = new ArrayList<>();
                for (double[] row : data) {
                    (row[dim] < split ? left : right).add(row);
                }
                return Node.split(dim, split, build(left, depth + 1, heightLimit, random), build(right, depth + 1, heightLimit, random));
            }
        }
        return Node.leaf(data.size());
    }

    private static double pathLength(Node node, double[] point, int depth) {
        if (node.isLeaf()) {
            return depth + averagePathLength(node.size);
        }
        return pathLength(point[node.dimension] < node.split ? node.left : node.right, point, depth + 1);
    }

    /**
     * Average path length of an unsuccessful search in a binary search tree of n points, used to normalise path
     * lengths.
     */
    static double averagePathLength(int n) {
        if (n <= 1) {
            return 0;
        }
        if (n == 2) {
            return 1;
        }
        return 2 * (Math.log(n - 1.0) + EULER_GAMMA) - 2.0 * (n - 1) / n;
    }

    private record Node(int dimension, double split, Node left, Node right, int size) {

        static Node leaf(int size) {
            return new Node(-1, 0, null, null, size);
        }

        static Node split(int dimension, double split, Node left, Node right) {
            return new Node(dimension, split, left, right, 0);
        }

        boolean isLeaf() {
            return left == null;
        }
    }
}
