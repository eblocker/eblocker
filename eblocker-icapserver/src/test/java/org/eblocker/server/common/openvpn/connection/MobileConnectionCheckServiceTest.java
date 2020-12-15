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
package org.eblocker.server.common.openvpn.connection;

import com.google.inject.Provider;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.openvpn.PortForwardingMode;
import org.eblocker.server.common.exceptions.UpnpPortForwardingException;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.eblocker.server.upnp.UpnpManagementService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;

public class MobileConnectionCheckServiceTest {

    private ScheduledExecutorService executorService;
    private Future future;
    private MobileConnectionCheckTask task;
    private Provider<MobileConnectionCheckTask> provider;
    private MobileConnectionCheckService service;
    private UpnpManagementService upnpService;
    private DataSource dataSource;
    private int portNum = 1194;
    private int duration = 60;
    private String eBlockerIp = "192.168.0.23";
    private String portForwardingDescription = "eBlocker Mobile";

    @Before
    public void setUp() {
        future = Mockito.mock(Future.class);

        executorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executorService.submit(Mockito.any(Runnable.class))).thenReturn(future);

        task = Mockito.mock(MobileConnectionCheckTask.class);

        provider = Mockito.mock(Provider.class);
        Mockito.when(provider.get()).thenReturn(task);

        upnpService = Mockito.mock(UpnpManagementService.class);

        OpenVpnServerService openVpnServerService = Mockito.mock(OpenVpnServerService.class);
        Mockito.when(openVpnServerService.getOpenVpnTempMappedPort()).thenReturn(portNum);

        dataSource = Mockito.mock(DataSource.class);

        int portNum = 1194;

        service = new MobileConnectionCheckService(upnpService, executorService, provider, openVpnServerService,
                dataSource, duration, portNum, portForwardingDescription);
    }

    @Test
    public void testStartRunningStop() throws UpnpPortForwardingException {
        service.start();
        Mockito.verify(provider).get();
        Mockito.verify(executorService).submit(task);

        Mockito.when(future.cancel(true)).thenReturn(true);
        Mockito.when(future.isDone()).thenReturn(true);

        service.stop();
        Mockito.verify(future).cancel(true);
    }

    @Test
    public void testStartOpensPort() throws UpnpPortForwardingException {
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.AUTO);

        service.start();

        Mockito.verify(dataSource).getOpenVpnPortForwardingMode();

        Mockito.verify(upnpService).addPortForwarding(Mockito.eq(portNum), Mockito.eq(portNum), Mockito.eq(duration),
                Mockito.eq(portForwardingDescription), Mockito.eq(false));
    }

    @Test
    public void testStartOpensNoPort() throws UpnpPortForwardingException {
        Mockito.when(dataSource.getOpenVpnPortForwardingMode()).thenReturn(PortForwardingMode.MANUAL);

        service.start();

        Mockito.verify(dataSource).getOpenVpnPortForwardingMode();

        Mockito.verify(upnpService, Mockito.never()).addPortForwardings(Mockito.any(), Mockito.eq(false));
    }

    @Test
    public void testStartFinishedStop() throws UpnpPortForwardingException {
        service.start();
        Mockito.verify(provider).get();
        Mockito.verify(executorService).submit(task);

        Mockito.when(future.cancel(Mockito.anyBoolean())).thenReturn(false);
        Mockito.when(future.isDone()).thenReturn(true);

        service.stop();

        Mockito.verify(future).cancel(true);
    }

    @Test
    public void testStartAlreadyStarted() throws UpnpPortForwardingException {
        service.start();
        Mockito.verify(provider).get();
        Mockito.verify(executorService).submit(task);

        Mockito.when(future.cancel(true)).thenReturn(true);
        Mockito.when(future.isDone()).thenReturn(true);

        service.start();
        Mockito.verify(future).cancel(true);
        Mockito.verify(provider, Mockito.times(2)).get();
        Mockito.verify(executorService, Mockito.times(2)).submit(task);
    }

    @Test
    public void testGetStatus() throws UpnpPortForwardingException {
        Assert.assertNull(service.getStatus());

        service.start();

        MobileConnectionCheckStatus status = new MobileConnectionCheckStatus(
                MobileConnectionCheckStatus.State.PENDING_REQUESTS, 0, 0, 0, 0);
        Mockito.when(task.getStatus()).thenReturn(status);

        Assert.assertEquals(status, service.getStatus());

        Mockito.when(future.cancel(true)).thenReturn(true);
        Mockito.when(future.isDone()).thenReturn(true);

        service.stop();
        Mockito.verify(task, Mockito.times(2)).getStatus();

        Assert.assertEquals(status, service.getStatus());

        Mockito.verify(task, Mockito.times(2)).getStatus();
    }
}
