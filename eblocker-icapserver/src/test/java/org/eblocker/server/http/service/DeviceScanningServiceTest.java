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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.network.ArpSweeper;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class DeviceScanningServiceTest {
    public static final long STARTUP_DELAY = 1;
    public static final long DEFAULT_SCANNING_INTERVAL = 10;
    private DeviceScanningService service;
    private ArpSweeper arpSweeper;
    private DataSource dataSource;
    private ScheduledExecutorService executorService;
    private ScheduledFuture<?> future;
    private NetworkInterfaceWrapper networkInterface;

    @Before
    public void setUp() {
        arpSweeper = Mockito.mock(ArpSweeper.class);
        dataSource = Mockito.mock(DataSource.class);
        executorService = Mockito.mock(ScheduledExecutorService.class);
        future = Mockito.mock(ScheduledFuture.class);
        networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.0.11"));

        service = new DeviceScanningService(
                arpSweeper,
                dataSource,
                executorService,
                STARTUP_DELAY,
                DEFAULT_SCANNING_INTERVAL,
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "embedded",
                networkInterface);

        Mockito.when(executorService.scheduleAtFixedRate(Mockito.any(), Mockito.anyLong(), Mockito.anyLong(), Mockito.eq(TimeUnit.MICROSECONDS))).
                thenAnswer(invocation -> future);
    }

    @Test
    public void scan() {
        service.scan();
        Mockito.verify(arpSweeper).fullScan();
    }

    @Test
    public void startNoIntervalInDataSource() {
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(null);
        service.start();
        Mockito.verify(executorService).scheduleAtFixedRate(arpSweeper, STARTUP_DELAY, 39062L, TimeUnit.MICROSECONDS);
    }

    @Test
    public void startWithIntervalInDataSource() {
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(42L);
        service.start();
        Mockito.verify(executorService).scheduleAtFixedRate(arpSweeper, STARTUP_DELAY, 164062L, TimeUnit.MICROSECONDS);
    }

    @Test
    public void getScanningInterval() {
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(42L);
        Assert.assertEquals(42, service.getScanningInterval());
    }

    @Test
    public void getScanningIntervalNotInDataSource() {
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(null);
        Assert.assertEquals(DEFAULT_SCANNING_INTERVAL, service.getScanningInterval());
    }

    @Test
    public void getScanningIntervalBadValueInDataSource() {
        Mockito.when(dataSource.getDeviceScanningInterval()).thenThrow(new NumberFormatException("not an int"));
        Assert.assertEquals(DEFAULT_SCANNING_INTERVAL, service.getScanningInterval());
    }

    @Test
    public void setScanningInterval() {
        service.setScanningInterval(42L);
        Mockito.verify(dataSource).setDeviceScanningInterval(42L);
    }

    @Test
    public void reschedule() {
        InOrder order = Mockito.inOrder(executorService, future);
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(42L);

        service.start();
        order.verify(executorService).scheduleAtFixedRate(arpSweeper, STARTUP_DELAY, 164062L, TimeUnit.MICROSECONDS);

        service.setScanningInterval(999L);
        order.verify(future).cancel(true);
        order.verify(executorService).scheduleAtFixedRate(arpSweeper, 0L, 3902343L, TimeUnit.MICROSECONDS);
    }

    @Test
    public void enableScanning() {
        // scanning is disabled initially:
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(0L);

        service.start();
        Mockito.verifyZeroInteractions(executorService);

        // scanning is enabled:
        service.setScanningInterval(42L);
        Mockito.verify(executorService).scheduleAtFixedRate(arpSweeper, 0L, 164062L, TimeUnit.MICROSECONDS);
    }

    @Test
    public void disableScanning() {
        // scanning is enabled initially:
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(42L);

        service.start();
        Mockito.verify(executorService).scheduleAtFixedRate(arpSweeper, 1L, 164062L, TimeUnit.MICROSECONDS);

        // scanning is disabled:
        service.setScanningInterval(0L);
        Mockito.verify(future).cancel(true);
        Mockito.verifyZeroInteractions(executorService);
    }

    @Test
    public void reEnableScanning() {
        InOrder order = Mockito.inOrder(executorService, future);
        Mockito.when(dataSource.getDeviceScanningInterval()).thenReturn(42L);

        service.start();
        order.verify(executorService).scheduleAtFixedRate(arpSweeper, STARTUP_DELAY, 164062L, TimeUnit.MICROSECONDS);

        // disable scanning
        service.setScanningInterval(0L);
        order.verify(future).cancel(true);

        // re-enable scanning
        service.setScanningInterval(42L);
        order.verify(executorService).scheduleAtFixedRate(arpSweeper, 0L, 164062L, TimeUnit.MICROSECONDS);
    }

    @Test
    public void testSweepingNonPrivateNets() {
        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("169.254.1.2"));
        service.start();
        Mockito.verifyZeroInteractions(executorService);

        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("12.1.2.254"));
        service.start();
        Mockito.verifyZeroInteractions(executorService);

        Mockito.when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("56.200.200.201"));
        service.start();
        Mockito.verifyZeroInteractions(executorService);
    }

    @Test
    public void disableScanningInServerMode() {
        service = new DeviceScanningService(
                arpSweeper,
                dataSource,
                executorService,
                STARTUP_DELAY,
                DEFAULT_SCANNING_INTERVAL,
                "10.0.0.0/8",
                "172.16.0.0/12",
                "192.168.0.0/16",
                "server",
                networkInterface);

        service.start();
        Mockito.verifyZeroInteractions(executorService);
    }
}
