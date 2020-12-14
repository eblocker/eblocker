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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class LoggingExecutorServiceTest {

    private LoggingExecutorService executorService;
    private ScheduledExecutorService delegateExecutorService;

    @Before
    public void setUp() {
        delegateExecutorService = Mockito.mock(ScheduledExecutorService.class);
        executorService = new LoggingExecutorService("name", delegateExecutorService);
    }

    @Test
    public void shutdown() {
        executorService.shutdown();
        Mockito.verify(delegateExecutorService).shutdown();
    }

    @Test
    public void shutdownNow() {
        List<Runnable> tasks = Collections.singletonList(Mockito.mock(Runnable.class));
        Mockito.when(delegateExecutorService.shutdownNow()).thenReturn(tasks);
        Assert.assertEquals(tasks, executorService.shutdownNow());
    }

    @Test
    public void isShutdown() {
        Assert.assertFalse(executorService.isShutdown());
        Mockito.when(delegateExecutorService.isShutdown()).thenReturn(true);
        Assert.assertTrue(executorService.isShutdown());
    }

    @Test
    public void isTerminated() {
        Assert.assertFalse(executorService.isTerminated());
        Mockito.when(delegateExecutorService.isTerminated()).thenReturn(true);
        Assert.assertTrue(executorService.isTerminated());
    }

    @Test
    public void awaitTermination() throws InterruptedException {
        executorService.awaitTermination(10, TimeUnit.SECONDS);
        Mockito.verify(delegateExecutorService).awaitTermination(10, TimeUnit.SECONDS);
    }

    @Test
    public void submitRunnable() {
        Runnable runnable = Mockito.mock(Runnable.class);
        executorService.submit(runnable);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(delegateExecutorService).submit(captor.capture());
        Assert.assertEquals(LoggingExecutorService.RunnableCallable.class, captor.getValue().getClass());

        captor.getValue().run();
        Mockito.verify(runnable).run();
    }

    @Test
    public void submitCallable() throws Exception {
        Object result = new Object();
        Callable<Object> callable = Mockito.mock(Callable.class);
        Mockito.when(callable.call()).thenReturn(result);
        executorService.submit(callable);

        ArgumentCaptor<Callable<Object>> captor = ArgumentCaptor.forClass(Callable.class);
        Mockito.verify(delegateExecutorService).submit(captor.capture());
        Assert.assertEquals(LoggingExecutorService.LoggingCallable.class, captor.getValue().getClass());

        Assert.assertEquals(result, captor.getValue().call());
    }

    @Test
    public void submitRunnableResult() {
        Object result = new Object();
        Runnable runnable = Mockito.mock(Runnable.class);
        executorService.submit(runnable, result);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(delegateExecutorService).submit(captor.capture(), Mockito.eq(result));
        Assert.assertEquals(LoggingExecutorService.RunnableCallable.class, captor.getValue().getClass());

        captor.getValue().run();
        Mockito.verify(runnable).run();
    }

    @Test
    public void invokeAll() throws Exception {
        Object result = new Object();
        Callable<Object> task = Mockito.mock(Callable.class);
        Mockito.when(task.call()).thenReturn(result);
        Collection<Callable<Object>> tasks = Collections.singletonList(task);
        executorService.invokeAll(tasks);

        ArgumentCaptor<Collection<Callable<Object>>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(delegateExecutorService).invokeAll(captor.capture());

        Assert.assertEquals(1, captor.getValue().size());

        Callable<Object> capturedCallable = captor.getValue().iterator().next();
        Assert.assertEquals(LoggingExecutorService.LoggingCallable.class, capturedCallable.getClass());
        Assert.assertEquals(result, capturedCallable.call());
    }

    @Test
    public void invokeAllWithTimeout() throws Exception {
        Object result = new Object();
        Callable<Object> task = Mockito.mock(Callable.class);
        Mockito.when(task.call()).thenReturn(result);
        Collection<Callable<Object>> tasks = Collections.singletonList(task);
        executorService.invokeAll(tasks, 10, TimeUnit.SECONDS);

        ArgumentCaptor<Collection<Callable<Object>>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(delegateExecutorService).invokeAll(captor.capture(), Mockito.eq(10L), Mockito.eq(TimeUnit.SECONDS));

        Assert.assertEquals(1, captor.getValue().size());

        Callable<Object> capturedCallable = captor.getValue().iterator().next();
        Assert.assertEquals(LoggingExecutorService.LoggingCallable.class, capturedCallable.getClass());
        Assert.assertEquals(result, capturedCallable.call());
    }

    @Test
    public void invokeAny() throws Exception {
        Object result = new Object();
        Callable<Object> task = Mockito.mock(Callable.class);
        Mockito.when(task.call()).thenReturn(result);
        Collection<Callable<Object>> tasks = Collections.singletonList(task);
        executorService.invokeAny(tasks);

        ArgumentCaptor<Collection<Callable<Object>>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(delegateExecutorService).invokeAny(captor.capture());

        Assert.assertEquals(1, captor.getValue().size());

        Callable<Object> capturedCallable = captor.getValue().iterator().next();
        Assert.assertEquals(LoggingExecutorService.LoggingCallable.class, capturedCallable.getClass());
        Assert.assertEquals(result, capturedCallable.call());
    }

    @Test
    public void invokeAnyWithTimeout() throws Exception {
        Object result = new Object();
        Callable<Object> task = Mockito.mock(Callable.class);
        Mockito.when(task.call()).thenReturn(result);
        Collection<Callable<Object>> tasks = Collections.singletonList(task);
        executorService.invokeAny(tasks, 10, TimeUnit.SECONDS);

        ArgumentCaptor<Collection<Callable<Object>>> captor = ArgumentCaptor.forClass(Collection.class);
        Mockito.verify(delegateExecutorService).invokeAny(captor.capture(), Mockito.eq(10L), Mockito.eq(TimeUnit.SECONDS));

        Assert.assertEquals(1, captor.getValue().size());

        Callable<Object> capturedCallable = captor.getValue().iterator().next();
        Assert.assertEquals(LoggingExecutorService.LoggingCallable.class, capturedCallable.getClass());
        Assert.assertEquals(result, capturedCallable.call());
    }

    @Test
    public void execute() {
        Runnable runnable = Mockito.mock(Runnable.class);
        executorService.execute(runnable);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(delegateExecutorService).execute(captor.capture());
        Assert.assertEquals(LoggingExecutorService.RunnableCallable.class, captor.getValue().getClass());

        captor.getValue().run();
        Mockito.verify(runnable).run();
    }

    @Test
    public void scheduleRunnable() {
        Runnable runnable = Mockito.mock(Runnable.class);
        executorService.schedule(runnable, 10, TimeUnit.SECONDS);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(delegateExecutorService).schedule(captor.capture(), Mockito.eq(10L), Mockito.eq(TimeUnit.SECONDS));
        Assert.assertEquals(LoggingExecutorService.RunnableCallable.class, captor.getValue().getClass());

        captor.getValue().run();
        Mockito.verify(runnable).run();
    }

    @Test
    public void scheduleCallable() throws Exception {
        Callable<Object> callable = Mockito.mock(Callable.class);
        executorService.schedule(callable, 10, TimeUnit.SECONDS);

        ArgumentCaptor<Callable> captor = ArgumentCaptor.forClass(Callable.class);
        Mockito.verify(delegateExecutorService).schedule(captor.capture(), Mockito.eq(10L), Mockito.eq(TimeUnit.SECONDS));
        Assert.assertEquals(LoggingExecutorService.LoggingCallable.class, captor.getValue().getClass());

        captor.getValue().call();
        Mockito.verify(callable).call();
    }

    @Test
    public void scheduleAtFixedRate() {
        Runnable runnable = Mockito.mock(Runnable.class);
        executorService.scheduleAtFixedRate(runnable, 10, 20, TimeUnit.SECONDS);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(delegateExecutorService).scheduleAtFixedRate(captor.capture(), Mockito.eq(10L), Mockito.eq(20L), Mockito.eq(TimeUnit.SECONDS));
        Assert.assertEquals(LoggingExecutorService.RunnableCallable.class, captor.getValue().getClass());

        captor.getValue().run();
        Mockito.verify(runnable).run();
    }

    @Test
    public void scheduleWithFixedDelay() {
        Runnable runnable = Mockito.mock(Runnable.class);
        executorService.scheduleWithFixedDelay(runnable, 10, 20, TimeUnit.SECONDS);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(delegateExecutorService).scheduleWithFixedDelay(captor.capture(), Mockito.eq(10L), Mockito.eq(20L), Mockito.eq(TimeUnit.SECONDS));
        Assert.assertEquals(LoggingExecutorService.RunnableCallable.class, captor.getValue().getClass());

        captor.getValue().run();
        Mockito.verify(runnable).run();
    }

    @Test
    public void getName() {
        Assert.assertEquals("name", executorService.getName());
    }

    @Test(timeout = 10000)
    public void getLastLogEntriesByName() throws InterruptedException, ExecutionException {
        executorService = new LoggingExecutorService("name", Executors.newFixedThreadPool(2));

        // task to be run a single time
        NamedRunnable singleRunTask = new NamedRunnable("task-0", () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // task to be run three times
        AtomicInteger wait = new AtomicInteger();
        NamedRunnable thriceRunTask = new NamedRunnable("task-1", () -> {
            try {
                int waitValue = wait.addAndGet(100);
                Thread.sleep(waitValue);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // task to be running
        // semaphore to signal task has been started (released by task on start)
        Semaphore runningTaskStartedSemaphore = new Semaphore(1);
        runningTaskStartedSemaphore.acquire();

        // semaphore to signal task to terminate (release to terminate task)
        Semaphore runningTaskTerminateSemaphore = new Semaphore(1);
        runningTaskTerminateSemaphore.acquire();

        NamedRunnable runningTask = new NamedRunnable("task-2", () -> {
            try {
                runningTaskStartedSemaphore.release();
                runningTaskTerminateSemaphore.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });

        // task throwing exception
        NamedRunnable exceptionTask = new NamedRunnable("task-3", () -> {
            throw new RuntimeException("unit-test");
        });

        // unnamed task
        Runnable unnamedRunnable = () -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };

        // unnamed callable
        Callable<Integer> unnamedCallable = () -> {
            try {
                Thread.sleep(100);
                return 23;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        };

        List<Future<?>> futures = new ArrayList<>();
        futures.add(executorService.submit(singleRunTask));
        futures.add(executorService.submit(thriceRunTask));
        futures.add(executorService.submit(thriceRunTask));
        futures.add(executorService.submit(thriceRunTask));
        futures.add(executorService.submit(exceptionTask));
        futures.add(executorService.submit(unnamedRunnable));
        futures.add(executorService.submit(unnamedCallable));
        executorService.submit(runningTask);

        // wait for tasks to be finished (or started)
        futures.forEach(future -> {
            try {
                future.get();
            } catch (Exception e) {
                // ignore
            }
        });
        runningTaskStartedSemaphore.acquire();

        // check logs
        Map<String, LogEntry> log = executorService.getLastLogEntriesByName();
        Assert.assertNotNull(log.get("task-0"));
        Assert.assertEquals("task-0", log.get("task-0").getName());
        Assert.assertEquals(1, log.get("task-0").getExecutions());
        Assert.assertEquals(0, log.get("task-0").getRunning());
        Assert.assertNotNull(log.get("task-0").getLastStop());
        Assert.assertTrue(log.get("task-0").getLastStop() - log.get("task-0").getLastStart() >= 100);
        Assert.assertTrue(log.get("task-0").getLastStop() - log.get("task-0").getLastStart() < 1000);
        Assert.assertNotNull(log.get("task-0").getTotalRuntime());
        Assert.assertEquals((long) log.get("task-0").getTotalRuntime(), log.get("task-0").getLastStop() - log.get("task-0").getLastStart());
        Assert.assertNull(log.get("task-0").getException());

        Assert.assertNotNull(log.get("task-1"));
        Assert.assertEquals("task-1", log.get("task-1").getName());
        Assert.assertEquals(3, log.get("task-1").getExecutions());
        Assert.assertEquals(0, log.get("task-1").getRunning());
        Assert.assertNotNull(log.get("task-1").getLastStop());
        Assert.assertTrue(log.get("task-1").getLastStop() - log.get("task-1").getLastStart() >= 100);
        Assert.assertTrue(log.get("task-1").getLastStop() - log.get("task-1").getLastStart() < 1000);
        Assert.assertNotNull(log.get("task-1").getTotalRuntime());
        Assert.assertTrue(log.get("task-1").getTotalRuntime() < 2000);
        Assert.assertNotNull(log.get("task-1").getMinRuntime());
        Assert.assertTrue(log.get("task-1").getMinRuntime() < 150);
        Assert.assertNotNull(log.get("task-1").getMaxRuntime());
        Assert.assertTrue(log.get("task-1").getMaxRuntime() >= 300);

        Assert.assertNull(log.get("task-1").getException());

        Assert.assertNotNull(log.get("task-2"));
        Assert.assertEquals("task-2", log.get("task-2").getName());
        Assert.assertEquals(1, log.get("task-2").getExecutions());
        Assert.assertEquals(1, log.get("task-2").getRunning());
        Assert.assertNull(log.get("task-2").getLastStop());
        Assert.assertNull(log.get("task-2").getException());

        Assert.assertNotNull(log.get("task-3"));
        Assert.assertEquals("task-3", log.get("task-3").getName());
        Assert.assertEquals(1, log.get("task-3").getExecutions());
        Assert.assertEquals(0, log.get("task-3").getRunning());
        Assert.assertNotNull(log.get("task-3").getLastStop());
        Assert.assertTrue(log.get("task-3").getLastStop() - log.get("task-3").getLastStart() >= 0);
        Assert.assertTrue(log.get("task-3").getLastStop() - log.get("task-3").getLastStart() < 1000);
        Assert.assertNotNull(log.get("task-3"));
        Assert.assertEquals((long) log.get("task-3").getTotalRuntime(), log.get("task-3").getLastStop() - log.get("task-3").getLastStart());
        Assert.assertNotNull(log.get("task-3").getException());
        Assert.assertTrue(log.get("task-3").getException().contains("RuntimeException"));

        Assert.assertNotNull(log.get(unnamedRunnable.getClass().getSimpleName()));
        Assert.assertEquals(unnamedRunnable.getClass().getSimpleName(), log.get(unnamedRunnable.getClass().getSimpleName()).getName());
        Assert.assertEquals(1, log.get(unnamedRunnable.getClass().getSimpleName()).getExecutions());
        Assert.assertNotNull(log.get(unnamedRunnable.getClass().getSimpleName()).getLastStop());
        Assert.assertTrue(log.get(unnamedRunnable.getClass().getSimpleName()).getLastStop() - log.get(unnamedRunnable.getClass().getSimpleName()).getLastStart() >= 100);
        Assert.assertTrue(log.get(unnamedRunnable.getClass().getSimpleName()).getLastStop() - log.get(unnamedRunnable.getClass().getSimpleName()).getLastStart() < 1000);
        Assert.assertNull(log.get(unnamedRunnable.getClass().getSimpleName()).getException());

        Assert.assertNotNull(log.get(unnamedCallable.getClass().getSimpleName()));
        Assert.assertEquals(unnamedCallable.getClass().getSimpleName(), log.get(unnamedCallable.getClass().getSimpleName()).getName());
        Assert.assertEquals(1, log.get(unnamedCallable.getClass().getSimpleName()).getExecutions());
        Assert.assertNotNull(log.get(unnamedCallable.getClass().getSimpleName()).getLastStop());
        Assert.assertTrue(log.get(unnamedCallable.getClass().getSimpleName()).getLastStop() - log.get(unnamedCallable.getClass().getSimpleName()).getLastStart() >= 100);
        Assert.assertTrue(log.get(unnamedCallable.getClass().getSimpleName()).getLastStop() - log.get(unnamedCallable.getClass().getSimpleName()).getLastStart() < 1000);
        Assert.assertNull(log.get(unnamedCallable.getClass().getSimpleName()).getException());
        Assert.assertEquals(23, futures.get(6).get());

        // terminate running task and shutdown
        runningTaskTerminateSemaphore.release();
        executorService.shutdown();
        executorService.awaitTermination(5, TimeUnit.SECONDS);
    }

}
