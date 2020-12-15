/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.common.util;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Function;

/**
 * Levenshtein's Distance Algorithm
 * <p>
 * Calculates the minimum edit distance between two lists (generalized from strings)
 * including edit sequence.
 * <p>
 * This implementation distinguishes between substitutions x->y and x->x named substitution and no operation here.
 * <p>
 * Default cost functions are:
 * <li>deletion: + 1
 * <li>insertion: + 1
 * <li>substitution: + 2
 * <li>no operation: + 0
 */
public class Levenshtein<T> {

    public enum Operation {NO_OPERATION, DELETE, INSERT, SUBSTITUTE}

    private CostFunctions costFunctions = new CostFunctions();

    private Levenshtein(CostFunctions costFunctions) {
        this.costFunctions = costFunctions;
    }

    public <T> Distance distance(List<T> source, List<T> target) {
        DistanceMatrixEntry[][] d = new DistanceMatrixEntry[source.size() + 1][target.size() + 1];

        d[0][0] = new DistanceMatrixEntry(0, Operation.NO_OPERATION, 0, 0);

        for (int i = 1; i < d.length; ++i) {
            d[i][0] = new DistanceMatrixEntry(i, Operation.DELETE, i, 0);
        }

        for (int i = 1; i < d[0].length; ++i) {
            d[0][i] = new DistanceMatrixEntry(i, Operation.INSERT, 0, i);
        }

        for (int i = 1; i < d.length; ++i) {
            for (int j = 1; j < d[i].length; ++j) {

                Function<Integer, Integer> cost;
                Operation operation;
                if (source.get(i - 1).equals(target.get(j - 1))) {
                    operation = Operation.NO_OPERATION;
                    cost = costFunctions.noOperation;
                } else {
                    operation = Operation.SUBSTITUTE;
                    cost = costFunctions.substitution;
                }

                d[i][j] = minimum(
                        new DistanceMatrixEntry(costFunctions.deletion.apply(d[i - 1][j].distance), Operation.DELETE, i, j),
                        new DistanceMatrixEntry(costFunctions.insertion.apply(d[i][j - 1].distance), Operation.INSERT, i, j),
                        new DistanceMatrixEntry(cost.apply(d[i - 1][j - 1].distance), operation, i, j));
            }
        }

        Distance distance = new Distance();
        distance.d = d;
        distance.distance = d[source.size()][target.size()].distance;
        distance.editSequence = backtrace(d);
        return distance;
    }

    public static class Distance {
        private int distance;
        private DistanceMatrixEntry[][] d;
        private List<DistanceMatrixEntry> editSequence;

        public int getDistance() {
            return distance;
        }

        public List<DistanceMatrixEntry> getEditSequence() {
            return editSequence;
        }
    }

    public static class DistanceMatrixEntry {
        private int distance;
        private Operation operation;
        private int x;
        private int y;

        public DistanceMatrixEntry(int distance, Operation operation, int x, int y) {
            this.distance = distance;
            this.operation = operation;
            this.x = x;
            this.y = y;
        }

        public int getDistance() {
            return distance;
        }

        public Operation getOperation() {
            return operation;
        }

        public int getX() {
            return x;
        }

        public int getY() {
            return y;
        }

        @Override
        public String toString() {
            return String.format("%d %d %d %s", distance, x, y, operation);
        }
    }

    private static DistanceMatrixEntry minimum(DistanceMatrixEntry... entries) {
        DistanceMatrixEntry minimum = entries[0];
        for (int i = 1; i < entries.length; ++i) {
            if (entries[i].distance <= minimum.distance) {
                minimum = entries[i];
            }
        }
        return minimum;
    }

    private static List<DistanceMatrixEntry> backtrace(DistanceMatrixEntry[][] d) {
        LinkedList<DistanceMatrixEntry> path = new LinkedList<>();
        int i = d.length - 1;
        int j = d[0].length - 1;
        while (i != 0 || j != 0) {
            path.addFirst(d[i][j]);
            int di = 0;
            int dj = 0;
            switch (d[i][j].operation) {
                case NO_OPERATION:
                case SUBSTITUTE:
                    di = -1;
                case INSERT:
                    dj = -1;
                    break;
                case DELETE:
                    di = -1;
                    break;
            }
            i += di;
            j += dj;
        }
        return path;
    }

    private static class CostFunctions {
        public Function<Integer, Integer> insertion = d -> d + 1;
        public Function<Integer, Integer> deletion = d -> d + 1;
        public Function<Integer, Integer> substitution = d -> d + 2;
        public Function<Integer, Integer> noOperation = Function.identity();
    }

    public static class Builder<T> {
        private CostFunctions costFunctions = new CostFunctions();

        public Levenshtein<T> build() {
            return new Levenshtein(costFunctions);
        }

        public Builder insertionCost(Function<Integer, Integer> cost) {
            costFunctions.insertion = cost;
            return this;
        }

        public Builder deletionCost(Function<Integer, Integer> cost) {
            costFunctions.deletion = cost;
            return this;
        }

        public Builder substitutionCost(Function<Integer, Integer> cost) {
            costFunctions.substitution = cost;
            return this;
        }

        public Builder noOperationCost(Function<Integer, Integer> cost) {
            costFunctions.noOperation = cost;
            return this;
        }

    }
}
