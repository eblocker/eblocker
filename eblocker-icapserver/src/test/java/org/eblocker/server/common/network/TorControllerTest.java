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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.Language;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static java.util.Arrays.asList;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class TorControllerTest {

    private TorController controller;
    private DataSource dataSource;
    private EblockerDnsServer dnsServer;
    private ScheduledExecutorService executorService;
    private MockTorControllerConnection telnetConnection;
    private final int torControlPort = 9051;
    private TorConfiguration torConfiguration;
    private static final Language LANGUAGE_EN = new Language("en", "english");

    @Before
    public void setUp() throws Exception {

        dataSource = Mockito.mock(DataSource.class);
        when(dataSource.getCurrentLanguage()).thenReturn(LANGUAGE_EN);
        dnsServer = Mockito.mock(EblockerDnsServer.class);
        telnetConnection = new MockTorControllerConnection();
        executorService = Mockito.mock(ScheduledExecutorService.class);
        torConfiguration = Mockito.mock(TorConfiguration.class);
    }

    protected TorController createTorController(int port) {
        TorExitNodeCountries exitNodeCountries = new TorExitNodeCountries(dataSource);

        return new TorController(port, dataSource, dnsServer, telnetConnection, exitNodeCountries, torConfiguration);
    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void reloadConfigurationAtStartup() throws IOException {
        controller = createTorController(torControlPort);
        getConnectionCheckingTask().run();
        assertTrue(telnetConnection.hasReceivedReloadSignal());
    }

    @Test
    public void reconnectToControlPortAutomatically() {
        controller = createTorController(torControlPort);
        getConnectionCheckingTask().run();

        telnetConnection.resetHasReceivedReloadSignal();
        telnetConnection.disconnect();
        Set<String> countries = new HashSet<String>(asList("Denmark", "Norway", "Sweden"));
        controller.setAllowedExitNodesCountries(countries);
        assertTrue(telnetConnection.hasReceivedReloadSignal());
    }

    @Test
    public void getNewIdentity() {
        controller = createTorController(torControlPort);
        getConnectionCheckingTask().run();
        controller.getNewIdentity();
        assertTrue(telnetConnection.hasReceivedNewnymSignal());
    }

    protected Runnable getConnectionCheckingTask() {
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        controller.startCheckingConnection(executorService, 1234);

        verify(executorService).scheduleWithFixedDelay(captor.capture(), eq(0L), eq(1234L), eq(TimeUnit.MILLISECONDS));

        return captor.getValue();
    }

    @Test
    public void testAddDeviceUsingTor() {
        controller = createTorController(torControlPort);

        // setup mock device
        Device device = new Device();
        device.setIpAddresses(Collections.singletonList(IpAddress.parse("192.168.3.3")));

        // tell controller to route device
        controller.addDeviceUsingTor(device);

        // check all necessary calls are done
        Mockito.verify(dnsServer).useTorResolver(device);
    }

    @Test
    public void testRemoveDeviceUsingTor() {
        controller = createTorController(torControlPort);

        // setup mock device
        Device device = new Device();
        device.setIpAddresses(Collections.singletonList(IpAddress.parse("192.168.3.3")));

        // tell controller to route device
        controller.removeDeviceNotUsingTor(device);

        // check all necessary calls are done
        Mockito.verify(dnsServer).useDefaultResolver(device);
    }
}
