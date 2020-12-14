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

import org.eblocker.registration.TosContainer;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.util.HttpClient;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class RegistrationServiceAvailabilityCheckTest {

    private static String PING_TARGETS = "www.eblocker.com, 8.8.8.8, google.com, 1.1.1.1";
    private static String HTTP_TARGETS = "https://www.eblocker.com, https://www.google.com";
    private static String PING_COMMAND = "ping";

    private DeviceRegistrationClient registrationClient;
    private DeviceRegistrationProperties registrationProperties;
    private ExecutorService executorService;
    private HttpClient httpClient;
    private ScheduledExecutorService scheduledExecutorService;
    private ScriptRunner scriptRunner;
    private RegistrationServiceAvailabilityCheck registrationServiceAvailabilityCheck;

    @Before
    public void setUp() {
        executorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executorService.submit(Mockito.any(Callable.class))).then(im -> {
            Callable callable = im.getArgument(0);
            Future future = Mockito.mock(Future.class);
            Mockito.when(future.get()).then(im2 -> callable.call());
            return future;
        });
        registrationClient = Mockito.mock(DeviceRegistrationClient.class);
        registrationProperties = Mockito.mock(DeviceRegistrationProperties.class);
        httpClient = Mockito.mock(HttpClient.class);
        scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
        scriptRunner = Mockito.mock(ScriptRunner.class);
        registrationServiceAvailabilityCheck = new RegistrationServiceAvailabilityCheck(PING_TARGETS, HTTP_TARGETS, PING_COMMAND, registrationClient, registrationProperties, executorService, httpClient, scheduledExecutorService, scriptRunner);
    }

    @Test
    public void testServiceAvailable() throws IOException, InterruptedException {
        mockPingResult("www.eblocker.com", 1);
        mockPingResult("8.8.8.8", 1);
        mockPingResult("google.com", 1);
        mockPingResult("1.1.1.1", 0);
        mockHttpResult("https://www.eblocker.com", true);
        mockHttpResult("https://www.google.com", false);
        Mockito.when(registrationClient.getTosContainer()).thenReturn(new TosContainer("test", new Date(), Collections.singletonMap("key", "value")));
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);

        initService();
        Assert.assertTrue(registrationServiceAvailabilityCheck.isRegistrationAvailable());
        Mockito.verify(scheduledExecutorService, Mockito.never()).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testInternetReachableButServiceNotAvailable() throws IOException, InterruptedException {
        mockPingResult("www.eblocker.com", 0);
        mockPingResult("8.8.8.8", 0);
        mockPingResult("google.com", 0);
        mockPingResult("1.1.1.1", 0);
        mockHttpResult("https://www.eblocker.com", true);
        mockHttpResult("https://www.google.com", true);
        Mockito.when(registrationClient.getTosContainer()).thenThrow(new RuntimeException());
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);

        initService();
        Assert.assertFalse(registrationServiceAvailabilityCheck.isRegistrationAvailable());
        Mockito.verify(scheduledExecutorService).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testNoRecheckInternetReachableButServiceNotAvailable() throws IOException, InterruptedException {
        mockPingResult("www.eblocker.com", 0);
        mockPingResult("8.8.8.8", 0);
        mockPingResult("google.com", 0);
        mockPingResult("1.1.1.1", 0);
        mockHttpResult("https://www.eblocker.com", true);
        mockHttpResult("https://www.google.com", true);
        Mockito.when(registrationClient.getTosContainer()).thenThrow(new RuntimeException());
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);

        initService();
        Assert.assertFalse(registrationServiceAvailabilityCheck.isRegistrationAvailable());
        Mockito.verify(executorService, Mockito.times(6)).submit(Mockito.any(Callable.class));

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(scheduledExecutorService).schedule(captor.capture(), Mockito.anyLong(), Mockito.any(TimeUnit.class));

        // assert running check again does not re-check internet connectivity
        captor.getValue().run();
        Mockito.verify(registrationClient, Mockito.times(2)).getTosContainer();
        Mockito.verify(executorService, Mockito.times(6)).submit(Mockito.any(Callable.class));
        Mockito.verify(scheduledExecutorService, Mockito.times(2)).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testNoInternet() throws IOException, InterruptedException {
        mockPingResult("www.eblocker.com", 1);
        mockPingResult("8.8.8.8", 1);
        mockPingResult("google.com", 1);
        mockPingResult("1.1.1.1", 1);
        mockHttpResult("https://www.eblocker.com", false);
        mockHttpResult("https://www.google.com", false);
        Mockito.when(registrationClient.getTosContainer()).thenThrow(new RuntimeException());
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);

        initService();
        Assert.assertTrue(registrationServiceAvailabilityCheck.isRegistrationAvailable());
        Mockito.verify(scheduledExecutorService).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testNoHttpAccess() throws IOException, InterruptedException {
        mockPingResult("www.eblocker.com", 0);
        mockPingResult("8.8.8.8", 0);
        mockPingResult("google.com", 0);
        mockPingResult("1.1.1.1", 0);
        mockHttpResult("https://www.eblocker.com", false);
        mockHttpResult("https://www.google.com", false);
        Mockito.when(registrationClient.getTosContainer()).thenThrow(new RuntimeException());
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);

        initService();
        Assert.assertTrue(registrationServiceAvailabilityCheck.isRegistrationAvailable());
        Mockito.verify(scheduledExecutorService).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testRegisteredDevice() {
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.OK);

        initService();
        Assert.assertTrue(registrationServiceAvailabilityCheck.isRegistrationAvailable());
        Mockito.verifyZeroInteractions(httpClient);
        Mockito.verifyZeroInteractions(registrationClient);
        Mockito.verifyZeroInteractions(scriptRunner);
        Mockito.verify(scheduledExecutorService, Mockito.never()).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class));
    }

    @Test
    public void testRecheckSchedulingDelays() throws IOException, InterruptedException {
        mockPingResult("www.eblocker.com", 0);
        mockPingResult("8.8.8.8", 0);
        mockPingResult("google.com", 0);
        mockPingResult("1.1.1.1", 0);
        mockHttpResult("https://www.eblocker.com", true);
        mockHttpResult("https://www.google.com", true);
        Mockito.when(registrationClient.getTosContainer()).thenThrow(new RuntimeException());
        Mockito.when(registrationProperties.getRegistrationState()).thenReturn(RegistrationState.NEW);

        initService();
        Assert.assertFalse(registrationServiceAvailabilityCheck.isRegistrationAvailable());

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        long expectedDelay = 2;
        for (int i = 0; i < 10; ++i) {
            Mockito.verify(scheduledExecutorService).schedule(captor.capture(), Mockito.eq(expectedDelay), Mockito.eq(TimeUnit.MINUTES));
            captor.getValue().run();
            expectedDelay *= 2;
        }

        Mockito.verify(scheduledExecutorService, Mockito.times(2)).schedule(captor.capture(), Mockito.eq(1024L), Mockito.eq(TimeUnit.MINUTES));
        captor.getValue().run();
        Mockito.verify(scheduledExecutorService, Mockito.times(3)).schedule(captor.capture(), Mockito.eq(1024L), Mockito.eq(TimeUnit.MINUTES));
    }

    private void mockPingResult(String host, int exitCode) throws IOException, InterruptedException {
        Mockito.when(scriptRunner.runScript(PING_COMMAND, "-c3", "-w3", host)).thenReturn(exitCode);
    }

    private void mockHttpResult(String url, boolean success) throws IOException {
        if (success) {
            Mockito.when(httpClient.download(url)).thenReturn(new ByteArrayInputStream(new byte[0]));
        } else {
            Mockito.when(httpClient.download(url)).thenThrow(new IOException("error"));
        }
    }

    private void initService() {
        registrationServiceAvailabilityCheck.init();
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(scheduledExecutorService).submit(captor.capture());
        captor.getValue().run();
    }

}
