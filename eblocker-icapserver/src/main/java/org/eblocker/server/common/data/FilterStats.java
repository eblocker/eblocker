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
package org.eblocker.server.common.data;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class FilterStats {

    private final Instant begin;
    private final Instant end;
    private final Bin summary;
    private final List<Bin> bins;

    public FilterStats(Instant begin, Instant end, Bin summary, List<Bin> bins) {
        this.begin = begin;
        this.end = end;
        this.summary = summary;
        this.bins = bins;
    }

    public Instant getBegin() {
        return begin;
    }

    public Instant getEnd() {
        return end;
    }

    public Bin getSummary() {
        return summary;
    }

    public List<Bin> getBins() {
        return bins;
    }

    public static class Bin {
        private Instant begin;
        private Instant end;
        private int queries;
        private int blockedQueries;
        private Map<Category, Integer> blockedQueriesByReason = new EnumMap<>(Category.class);

        public Bin(Instant begin, Instant end) {
            this.begin = begin;
            this.end = end;
        }

        public Instant getBegin() {
            return begin;
        }

        public Instant getEnd() {
            return end;
        }

        public int getQueries() {
            return queries;
        }

        public void setQueries(int queries) {
            this.queries = queries;
        }

        public int getBlockedQueries() {
            return blockedQueries;
        }

        public void setBlockedQueries(int blockedQueries) {
            this.blockedQueries = blockedQueries;
        }

        public Map<Category, Integer> getBlockedQueriesByReason() {
            return blockedQueriesByReason;
        }
    }

    public enum Category {
        ADS,
        CUSTOM,
        TRACKERS,
        PARENTAL_CONTROL,
        UNKNOWN
    }
}
