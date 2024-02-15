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
package org.eblocker.server.common.update;

import org.eblocker.server.common.network.InetAddressWrapper;
import org.eblocker.server.common.network.unix.NetworkInterfaceAliases;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import javax.xml.ws.Holder;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class ControlBarAliasUpdaterTest {

    private static final String CONTROL_BAR_HOST_NAME = "controlbar.eblocker";
    private static final String CONTROL_BAR_HOST_FALLBACK_IP = "8.8.4.4";
    private static final long INITIAL_DELAY = 0;
    private static final long REGULAR_PERIOD = 7200;
    private static final long ERROR_PERIOD = 30;
    private static final int ERROR_LOG_THRESHOLD = 0;

    private InetAddressWrapper inetAddress;
    private NetworkInterfaceAliases networkInterfaceAliases;
    private ScheduledExecutorService scheduledExecutorService;

    private List<Task> tasks = new ArrayList<>();

    private ControlBarAliasUpdater updater;

    @Before
    public void setup() {
        inetAddress = Mockito.mock(InetAddressWrapper.class);

        Holder<Integer> aliasCounter = new Holder<>(0);
        networkInterfaceAliases = Mockito.mock(NetworkInterfaceAliases.class);
        Mockito.when(networkInterfaceAliases.add(Mockito.any(), Mockito.any())).then(im -> {
            aliasCounter.value += 1;
            return "eth0:" + aliasCounter.value;
        });

        scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(scheduledExecutorService.scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.anyLong(), Mockito.any())).thenAnswer(im -> {
                    Task task = new Task(im.getArgument(0), Mockito.mock(ScheduledFuture.class));
                    tasks.add(task);
                    return task.future;
                }
        );

        updater = new ControlBarAliasUpdater(CONTROL_BAR_HOST_NAME, CONTROL_BAR_HOST_FALLBACK_IP, INITIAL_DELAY, REGULAR_PERIOD, ERROR_PERIOD, ERROR_LOG_THRESHOLD, inetAddress, networkInterfaceAliases, scheduledExecutorService);

        updater.start();
    }

    @Test
    public void testInitialSchedule() {
        Mockito.verify(scheduledExecutorService).scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.eq(INITIAL_DELAY), Mockito.eq(REGULAR_PERIOD), Mockito.eq(TimeUnit.SECONDS));
    }

    @Test
    public void testUpdateSuccess() throws UnknownHostException {
        byte[] addr = { 8, 8, 8, 8 };
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenReturn(InetAddress.getByAddress(CONTROL_BAR_HOST_NAME, addr));

        // run initial update task
        tasks.get(0).runnable.run();

        // check alias is setup
        Mockito.verify(networkInterfaceAliases).add("8.8.8.8", "255.255.255.255");

        // check task is still scheduled
        Mockito.verifyNoInteractions(tasks.get(0).future);
    }

    @Test
    public void testIpChange() throws UnknownHostException {
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenReturn(InetAddress.getByAddress(CONTROL_BAR_HOST_NAME, new byte[]{ 8, 8, 8, 8 }));

        // run initial update task
        tasks.get(0).runnable.run();

        // check alias is setup
        Mockito.verify(networkInterfaceAliases).add("8.8.8.8", "255.255.255.255");

        // change ip and run next update
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenReturn(InetAddress.getByAddress(CONTROL_BAR_HOST_NAME, new byte[]{ 8, 8, 8, 4 }));
        tasks.get(0).runnable.run();

        // check alias has been updated
        Mockito.verify(networkInterfaceAliases).remove("eth0:1");
        Mockito.verify(networkInterfaceAliases).add("8.8.8.4", "255.255.255.255");
    }

    @Test
    public void testNoxIpChange() throws UnknownHostException {
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenReturn(InetAddress.getByAddress(CONTROL_BAR_HOST_NAME, new byte[]{ 8, 8, 8, 8 }));

        // run initial update task
        tasks.get(0).runnable.run();

        // check alias is setup
        Mockito.verify(networkInterfaceAliases).add("8.8.8.8", "255.255.255.255");

        // change ip and run next update
        tasks.get(0).runnable.run();

        // check alias has not been updated
        Mockito.verifyNoMoreInteractions(networkInterfaceAliases);
    }

    @Test
    public void testUpdateFailure() throws UnknownHostException {
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenThrow(new UnknownHostException("mock-exception"));

        // run initial update task
        tasks.get(0).runnable.run();

        // check alias with fallback ip is setup
        InOrder networkInterfaceAliasesInOrder = Mockito.inOrder(networkInterfaceAliases);
        networkInterfaceAliasesInOrder.verify(networkInterfaceAliases).add(CONTROL_BAR_HOST_FALLBACK_IP, "255.255.255.255");

        // check regular update task has been canceled
        Mockito.verify(tasks.get(0).future).cancel(false);

        // a new task must have been scheduled
        InOrder scheduledExecutorServiceInOrder = Mockito.inOrder(scheduledExecutorService);
        scheduledExecutorServiceInOrder.verify(scheduledExecutorService).scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.eq(ERROR_PERIOD), Mockito.eq(ERROR_PERIOD), Mockito.eq(TimeUnit.SECONDS));
        Assert.assertEquals(2, tasks.size());

        // run error task
        tasks.get(1).runnable.run();

        // check no changes has been done
        scheduledExecutorServiceInOrder.verifyNoMoreInteractions();
        networkInterfaceAliasesInOrder.verifyNoMoreInteractions();

        // change mock to resolve host successfully and re-run update task
        Mockito.reset(inetAddress);
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenReturn(InetAddress.getByAddress(CONTROL_BAR_HOST_NAME, new byte[]{ 8, 8, 8, 8 }));
        tasks.get(1).runnable.run();

        // check alias has been setup
        networkInterfaceAliasesInOrder.verify(networkInterfaceAliases).add("8.8.8.8", "255.255.255.255");
        networkInterfaceAliasesInOrder.verify(networkInterfaceAliases).remove("eth0:1");

        // check updater has been re-scheduledMockito.verify(tasks.get(1).future).cancel(false);
        scheduledExecutorServiceInOrder.verify(scheduledExecutorService).scheduleAtFixedRate(Mockito.any(Runnable.class), Mockito.eq(REGULAR_PERIOD), Mockito.eq(REGULAR_PERIOD), Mockito.eq(TimeUnit.SECONDS));
        Assert.assertEquals(3, tasks.size());
    }

    @Test
    public void tesAliasIsKeptOnError() throws UnknownHostException {
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenReturn(InetAddress.getByAddress(CONTROL_BAR_HOST_NAME, new byte[]{ 8, 8, 8, 8 }));

        // run initial update task
        tasks.get(0).runnable.run();

        // check alias is setup
        Mockito.verify(networkInterfaceAliases).add("8.8.8.8", "255.255.255.255");

        // fail to resolve ip
        Mockito.reset(inetAddress);
        Mockito.when(inetAddress.getByName(CONTROL_BAR_HOST_NAME)).thenThrow(new UnknownHostException("unit-test mock"));
        tasks.get(0).runnable.run();

        // check alias has been kept
        Mockito.verifyNoMoreInteractions(networkInterfaceAliases);
    }

    private class Task {
        Runnable runnable;
        ScheduledFuture future;

        Task(Runnable runnable, ScheduledFuture future) {
            this.runnable = runnable;
            this.future = future;
        }
    }
}
