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
package org.eblocker.server.common;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A ScheduledExecutorService for tests where the elapsed time is controlled "manually"
 * with method elapse().
 * <p>
 * NOTE: periodic scheduling via scheduleAtFixedRate() or scheduleWithFixedDelay() is not
 * yet implemented.
 */
public class MockScheduledExecutorService implements ScheduledExecutorService {
    private static final Logger log = LoggerFactory.getLogger(MockScheduledExecutorService.class);

    private Duration taskDuration; // how long does a task take to complete
    private Duration serviceAge;   // the "current" age of the service
    private List<ScheduledFutureTask<?>> tasks;

    /**
     * Creates a test ScheduledExecutorService where all tasks take
     * the given duration to complete.
     *
     * @param taskDuration
     */
    public MockScheduledExecutorService(Duration taskDuration) {
        this.taskDuration = taskDuration;
        tasks = new ArrayList<ScheduledFutureTask<?>>();
        serviceAge = Duration.ZERO; // newborn
    }

    /**
     * Creates a test ScheduledExecutorService where all tasks complete instantly.
     */
    public MockScheduledExecutorService() {
        this(Duration.ZERO);
    }

    enum TaskState {
        WAITING, RUNNING, DONE, CANCELLED;
    }

    ;

    class ScheduledFutureTask<T> implements ScheduledFuture<T> {
        private TaskState state = TaskState.WAITING;
        private final Duration delay;          // the initially scheduled delay
        private final Duration scheduledAfter; // time between creation of the service and scheduling of the task
        private Runnable command;

        public ScheduledFutureTask(Runnable command, Duration delay) {
            this.delay = delay;
            this.command = command;
            this.scheduledAfter = serviceAge;
        }

        private Duration getCurrentDelay() {
            return delay.plus(scheduledAfter).minus(serviceAge);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return timeUnits(getCurrentDelay(), unit);
        }

        @Override
        public int compareTo(Delayed that) {
            return Long.valueOf(this.getDelay(TimeUnit.NANOSECONDS))
                    .compareTo(that.getDelay(TimeUnit.NANOSECONDS));
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            if (state == TaskState.WAITING) {
                state = TaskState.CANCELLED;
                return true;
            } else if (state == TaskState.RUNNING && mayInterruptIfRunning) {
                state = TaskState.CANCELLED;
                return true;
            } else {
                return false;
            }
        }

        @Override
        public boolean isCancelled() {
            return state == TaskState.CANCELLED;
        }

        @Override
        public boolean isDone() {
            return (state == TaskState.DONE || state == TaskState.CANCELLED);
        }

        @Override
        public T get() throws InterruptedException, ExecutionException {
            notImplemented();
            return null;
        }

        @Override
        public T get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            notImplemented();
            return null;
        }

        private void startIfDue() {
            if (state == TaskState.WAITING) {
                Duration delay = getCurrentDelay();
                if (delay.isNegative() || delay.isZero()) {
                    state = TaskState.RUNNING;
                    log.info("Started: {}", this);
                }
            }
        }

        private void finishIfComplete() {
            if (state == TaskState.RUNNING) {
                Duration delayToCompletion = getCurrentDelay().plus(taskDuration);
                if (delayToCompletion.isNegative() || delayToCompletion.isZero()) {
                    command.run();
                    state = TaskState.DONE;
                    log.info("Completed: {}", this);
                }
            }
        }

        @Override
        public String toString() {
            return String.format("Task [%s] (scheduled: %d, delay: %d, duration: %d, age of service: %d)", state,
                    scheduledAfter.getSeconds(), delay.getSeconds(), taskDuration.getSeconds(), serviceAge.getSeconds());
        }
    }

    /**
     * Converts a time span to a duration
     *
     * @param delay number of time units
     * @param unit  time unit
     * @return
     */
    private Duration duration(long delay, TimeUnit unit) {
        return Duration.ofNanos(unit.toNanos(delay));
    }

    /**
     * Converts a duration to a number of time units
     *
     * @param duration
     * @param unit     the unit to convert to
     * @return
     */
    private long timeUnits(Duration duration, TimeUnit unit) {
        return unit.convert(duration.toNanos(), TimeUnit.NANOSECONDS);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        ScheduledFutureTask<Void> task = new ScheduledFutureTask<Void>(command, duration(delay, unit));
        tasks.add(task);
        return task;
    }

    /**
     * Advance time by the given duration. This might start or complete some tasks.
     * Tasks are really run at the end of their duration (all tasks take the same
     * time to complete, see: taskDuration).
     */
    public void elapse(Duration duration) {
        if (duration.isNegative()) {
            throw new IllegalArgumentException("You can't go back in time!");
        }
        serviceAge = serviceAge.plus(duration);
        tasks.stream().forEach(ScheduledFutureTask::startIfDue);
        tasks.stream().forEach(ScheduledFutureTask::finishIfComplete);
    }

    private void notImplemented() {
        throw new RuntimeException("Not implemented yet in MockScheduledExecutorService. Feel free to implement it if you need it for tests.");
    }

    /*
     * Methods not implemented yet:
     */

    @Override
    public void shutdown() {
        notImplemented();
    }

    @Override
    public List<Runnable> shutdownNow() {
        notImplemented();
        return null;
    }

    @Override
    public boolean isShutdown() {
        notImplemented();
        return false;
    }

    @Override
    public boolean isTerminated() {
        notImplemented();
        return false;
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        notImplemented();
        return false;
    }

    @Override
    public <T> Future<T> submit(Callable<T> task) {
        notImplemented();
        return null;
    }

    @Override
    public <T> Future<T> submit(Runnable task, T result) {
        notImplemented();
        return null;
    }

    @Override
    public Future<?> submit(Runnable task) {
        notImplemented();
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks) throws InterruptedException {
        notImplemented();
        return null;
    }

    @Override
    public <T> List<Future<T>> invokeAll(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException {
        notImplemented();
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
        notImplemented();
        return null;
    }

    @Override
    public <T> T invokeAny(Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
        notImplemented();
        return null;
    }

    @Override
    public void execute(Runnable command) {
        notImplemented();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        notImplemented();
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        notImplemented();
        return null;
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        notImplemented();
        return null;
    }

}
