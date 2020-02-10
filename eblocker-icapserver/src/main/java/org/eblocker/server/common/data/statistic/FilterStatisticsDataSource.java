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
package org.eblocker.server.common.data.statistic;

import org.eblocker.server.common.data.IpAddress;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.stream.Stream;

public interface FilterStatisticsDataSource {
    Stream<StatisticsCounter> getCounters();
    Stream<StatisticsCounter> getCounters(String type, IpAddress ipAddress, Instant begin, Instant end);
    void incrementCounters(Collection<StatisticsCounter> counters);
    void deleteCountersBefore(Instant instant);

    List<TotalCounter> getTotalCounters(String type);
    void incrementTotalCounters(Collection<TotalCounter> totalCounters);
    Instant getLastResetTotalCounters();
    void resetTotalCounters();
}
