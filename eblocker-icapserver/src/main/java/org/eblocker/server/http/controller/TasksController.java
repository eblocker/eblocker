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
package org.eblocker.server.http.controller;

import org.eblocker.server.common.data.TasksViewConfig;
import org.eblocker.server.common.executor.LogEntry;
import org.eblocker.server.common.executor.PoolStats;
import org.restexpress.Request;
import org.restexpress.Response;

import java.util.Collection;
import java.util.Map;

public interface TasksController {
    TasksViewConfig getViewConfig(Request request, Response response);
    TasksViewConfig setViewConfig(Request request, Response response);
    Map<String, Collection<LogEntry>> getLog(Request request, Response response);

    Map<String, PoolStats> getPoolStats(Request request, Response response);
}
