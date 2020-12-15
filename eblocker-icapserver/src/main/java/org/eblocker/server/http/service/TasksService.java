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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.TasksViewConfig;
import org.eblocker.server.common.executor.LogEntry;
import org.eblocker.server.common.executor.LoggingExecutorService;
import org.eblocker.server.common.executor.PoolStats;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

public class TasksService {

    private final DataSource dataSource;
    private final List<LoggingExecutorService> loggingExecutorServices;

    @Inject
    public TasksService(DataSource dataSource,
                        @Named("highPrioScheduledExecutor") ScheduledExecutorService highPrioScheduledExecutor,
                        @Named("lowPrioScheduledExecutor") ScheduledExecutorService lowPrioScheduledExecutor,
                        @Named("unlimitedCachePoolExecutor") Executor unlimitedCachePoolExecutor) {
        this.dataSource = dataSource;
        this.loggingExecutorServices = Arrays.asList(
                (LoggingExecutorService) highPrioScheduledExecutor,
                (LoggingExecutorService) lowPrioScheduledExecutor,
                (LoggingExecutorService) unlimitedCachePoolExecutor);
    }

    public Map<String, Collection<LogEntry>> getLastExecutions() {
        return loggingExecutorServices.stream().collect(Collectors.toMap(
                LoggingExecutorService::getName,
                e -> e.getLastLogEntriesByName().values()));
    }

    public Map<String, PoolStats> getPoolStats() {
        return loggingExecutorServices.stream().collect(Collectors.toMap(
                LoggingExecutorService::getName,
                LoggingExecutorService::getStats));
    }

    public TasksViewConfig getViewConfig() {
        TasksViewConfig config = dataSource.get(TasksViewConfig.class);
        return config != null ? config : new TasksViewConfig();
    }

    public TasksViewConfig setViewConfig(TasksViewConfig config) {
        return dataSource.save(config);
    }
}
