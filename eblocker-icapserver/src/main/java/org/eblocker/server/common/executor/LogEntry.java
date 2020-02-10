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
package org.eblocker.server.common.executor;

public class LogEntry {
    private final String name;
    private Schedule schedule;
    private Long lastStart;
    private Long lastStop;
    private String exception;
    private int executions;
    private int running;
    private Long minRuntime;
    private Long maxRuntime;
    private Long totalRuntime;

    LogEntry(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public synchronized Long getLastStart() {
        return lastStart;
    }

    public synchronized Long getLastStop() {
        return lastStop;
    }

    public int getExecutions() {
        return executions;
    }

    public int getRunning() {
        return running;
    }

    public Long getMinRuntime() {
        return minRuntime;
    }

    public Long getMaxRuntime() {
        return maxRuntime;
    }

    public Long getTotalRuntime() {
        return totalRuntime;
    }

    public String getException() {
        return exception;
    }

    synchronized void updateSchedule(Schedule schedule) {
        this.schedule = schedule;
    }

    synchronized void update(Long start, Long stop, String exception) {
        this.lastStart = start;
        this.lastStop = stop;
        this.exception = exception;
        if (stop != null) {
            --running;
            long runtime = stop - start;
            if (totalRuntime == null) {
                minRuntime = runtime;
                maxRuntime = runtime;
                totalRuntime = runtime;
            } else {
                minRuntime = Math.min(minRuntime, runtime);
                maxRuntime = Math.max(maxRuntime, runtime);
                totalRuntime += runtime;
            }
        } else {
            ++executions;
            ++running;
        }
    }

}
