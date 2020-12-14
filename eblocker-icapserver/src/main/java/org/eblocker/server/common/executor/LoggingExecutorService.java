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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

public class LoggingExecutorService implements ScheduledExecutorService {

    private static final Logger log = LoggerFactory.getLogger(LoggingExecutorService.class);

    private final String name;
    private final ExecutorService executorService;
    private final ConcurrentMap<String, LogEntry> lastLogEntriesByName;

    public LoggingExecutorService(String name, ExecutorService executorService) {
        this.name = name;
        this.executorService = executorService;
        lastLogEntriesByName = new ConcurrentHashMap<>(16, 0.75f, 4);
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public List<Runnable> shutdownNow() {
        return executorService.shutdownNow();
    }

    public boolean isShutdown() {
        return executorService.isShutdown();
    }

    public boolean isTerminated() {
        return executorService.isTerminated();
    }

    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executorService.awaitTermination(timeout, unit);
    }

    public <T> Future<T> submit(Callable<T> task) {
        return executorService.submit(decorate(task));
    }

    public <T> Future<T> submit(Runnable task, T result) {
        return executorService.submit(decorate(task), result);
    }

    public Future<?> submit(Runnable task) {
        return executorService.submit(decorate(task));
    }

    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks) throws InterruptedException {
        return executorService.invokeAll(tasks.stream().map(this::decorate).collect(Collectors.toList()));
    }

    public <T> List<Future<T>> invokeAll(
            Collection<? extends Callable<T>> tasks, long timeout,
            TimeUnit unit) throws InterruptedException {
        return executorService.invokeAll(tasks.stream().map(this::decorate).collect(Collectors.toList()), timeout, unit);
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        return executorService.invokeAny(tasks.stream().map(this::decorate).collect(Collectors.toList()));
    }

    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout,
                           TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        return executorService.invokeAny(tasks.stream().map(this::decorate).collect(Collectors.toList()), timeout, unit);
    }

    public void execute(Runnable command) {
        executorService.execute(decorate(command));
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay,
                                       TimeUnit unit) {
        return ((ScheduledExecutorService) executorService).schedule(decorate(command, schedule("future", unit, delay, null)), delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay,
                                           TimeUnit unit) {
        return ((ScheduledExecutorService) executorService).schedule(decorate(callable, schedule("future", unit, delay, null)), delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period,
                                                  TimeUnit unit) {
        return ((ScheduledExecutorService) executorService).scheduleAtFixedRate(decorate(command, schedule("fixed", unit, initialDelay, period)), initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay,
                                                     TimeUnit unit) {
        return ((ScheduledExecutorService) executorService).scheduleWithFixedDelay(decorate(command, schedule("delay", unit, initialDelay, delay)), initialDelay, delay, unit);
    }

    public String getName() {
        return name;
    }

    public Map<String, LogEntry> getLastLogEntriesByName() {
        return new HashMap<>(lastLogEntriesByName);
    }

    public PoolStats getStats() {
        if (executorService instanceof ThreadPoolExecutor) {
            ThreadPoolExecutor threadPoolExecutor = (ThreadPoolExecutor) executorService;

            PoolStats poolStats = new PoolStats();
            poolStats.setActiveCount(threadPoolExecutor.getActiveCount());
            poolStats.setCompletedTaskCount(threadPoolExecutor.getCompletedTaskCount());
            poolStats.setCorePoolSize(threadPoolExecutor.getCorePoolSize());
            poolStats.setKeepAliveTime(threadPoolExecutor.getKeepAliveTime(TimeUnit.SECONDS));
            poolStats.setLargestPoolSize(threadPoolExecutor.getLargestPoolSize());
            poolStats.setPoolSize(threadPoolExecutor.getPoolSize());
            poolStats.setQueueLength(threadPoolExecutor.getQueue().size());
            poolStats.setTaskCount(threadPoolExecutor.getTaskCount());
            return poolStats;
        }

        return null;
    }

    private Schedule schedule(String type, TimeUnit unit, long delay, Long interval) {
        long delayMs = unit.toMillis(delay);
        Long intervalMs = interval != null ? unit.toMillis(interval) : null;
        return new Schedule(type, delayMs, intervalMs);
    }

    private Runnable decorate(Runnable runnable) {
        return decorate(runnable, null);
    }

    private Runnable decorate(Runnable runnable, Schedule schedule) {
        String taskName = runnable instanceof NamedRunnable ? ((NamedRunnable) runnable).getName() : getTaskName(runnable);
        return new RunnableCallable(new LoggingCallable<>(taskName, schedule, new CallableRunnable<>(runnable)));
    }

    private <T> Callable<T> decorate(Callable<T> callable) {
        return decorate(callable, null);
    }

    private <T> Callable<T> decorate(Callable<T> callable, Schedule schedule) {
        String taskName = callable instanceof NamedCallable ? ((NamedCallable<T>) callable).getName() : getTaskName(callable);
        return new LoggingCallable<>(taskName, schedule, callable);
    }

    private String getTaskName(Object object) {
        String simpleName = object.getClass().getSimpleName();
        if (!"".equals(simpleName)) {
            return simpleName;
        }
        return object.getClass().getName();
    }

    class LoggingCallable<T> implements Callable<T> {
        private final String taskName;
        private final Callable<T> callable;

        private LoggingCallable(String taskName, Schedule schedule, Callable<T> callable) {
            this.taskName = taskName;
            this.callable = callable;
            getLogEntry().updateSchedule(schedule);
        }

        @Override
        public T call() throws Exception {
            long threadId = Thread.currentThread().getId();
            long start = System.currentTimeMillis();
            String exception = null;
            LogEntry entry = getLogEntry();
            entry.update(start, null, null);
            log.debug("executor {}-{}: starting: {}", name, threadId, taskName);
            try {
                return callable.call();
            } catch (Exception e) {
                exception = getStackTrace(e);
                log.error("executor {}-{}: exception in {}", name, threadId, taskName, e);
                throw e;
            } finally {
                long stop = System.currentTimeMillis();
                long elapsed = stop - start;
                log.debug("executor {}-{}: finished: {} in {}ms", name, threadId, taskName, elapsed);
                entry.update(start, stop, exception);
            }
        }

        private LogEntry getLogEntry() {
            return lastLogEntriesByName.computeIfAbsent(taskName, k -> new LogEntry(taskName));
        }

        private String getStackTrace(Exception e) {
            StringWriter stringWriter = new StringWriter();
            PrintWriter printWriter = new PrintWriter(stringWriter);
            e.printStackTrace(printWriter);
            printWriter.flush();
            return stringWriter.toString();
        }
    }

    class RunnableCallable implements Runnable {
        private final Callable<?> callable;

        private RunnableCallable(Callable<?> callable) {
            this.callable = callable;
        }

        @Override
        public void run() {
            try {
                callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException("runnable wrapper for callable is unable to throw checked exception", e);
            }
        }
    }

    private class CallableRunnable<T> implements Callable<T> {
        private final Runnable runnable;

        private CallableRunnable(Runnable runnable) {
            this.runnable = runnable;
        }

        @Override
        public T call() {
            runnable.run();
            return null;
        }
    }
}
