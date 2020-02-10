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
import org.eblocker.server.common.data.DynDnsConfig;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.registration.DynDnsEntry;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class DynDnsServiceTest {

    private DataSource dataSource;
    private DeviceRegistrationClient client;
    private DynDnsService service;

    @Before
    public void setUp() {
        dataSource = Mockito.mock(DataSource.class);
        client = Mockito.mock(DeviceRegistrationClient.class);
        service = new DynDnsService(dataSource, client);
    }

    @Test
    public void testEnable() {
        Mockito.when(dataSource.get(DynDnsConfig.class)).thenReturn(createConfig(false, null, null));

        service.enable();

        ArgumentCaptor<DynDnsConfig> captor = ArgumentCaptor.forClass(DynDnsConfig.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertTrue(captor.getValue().isEnabled());
    }

    @Test
    public void testDisable() {
        Mockito.when(dataSource.get(DynDnsConfig.class)).thenReturn(createConfig(true, "host.dyndns.eblocker.com", "8.8.8.9"));

        service.disable();

        ArgumentCaptor<DynDnsConfig> captor = ArgumentCaptor.forClass(DynDnsConfig.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertFalse(captor.getValue().isEnabled());
        Assert.assertNull(captor.getValue().getHostname());
        Assert.assertNull(captor.getValue().getIpAddress());
    }

    @Test
    public void testGetHostname() {
        DynDnsConfig config = createConfig(true, "host.dyndns.eblocker.com", "8.8.8.9");
        Mockito.when(dataSource.get(DynDnsConfig.class)).thenReturn(config);
        Assert.assertEquals("host.dyndns.eblocker.com", service.getHostname());
    }

    @Test
    public void testUpdateEnabled() {
        Mockito.when(client.updateDynDnsIpAddress()).thenReturn(new DynDnsEntry("host.dyndns.eblocker.com", "8.8.8.9"));
        DynDnsConfig config = createConfig(true, null, null);
        Mockito.when(dataSource.get(DynDnsConfig.class)).thenReturn(config);

        service.update();

        Mockito.verify(client).updateDynDnsIpAddress();

        ArgumentCaptor<DynDnsConfig> captor = ArgumentCaptor.forClass(DynDnsConfig.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertEquals("host.dyndns.eblocker.com", config.getHostname());
        Assert.assertEquals("8.8.8.9", config.getIpAddress());
    }

    @Test
    public void testUpdateNotEnabled() {
        DynDnsConfig config = createConfig(false, null, null);
        Mockito.when(dataSource.get(DynDnsConfig.class)).thenReturn(config);

        service.update();

        Mockito.verifyZeroInteractions(client);
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(DynDnsConfig.class));
    }

    @Test
    public void testUpdateFailure() {
        DynDnsConfig config = createConfig(true, null, null);
        Mockito.when(dataSource.get(DynDnsConfig.class)).thenReturn(config);
        Mockito.when(client.updateDynDnsIpAddress()).thenThrow(new RuntimeException());

        service.update();

        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(DynDnsConfig.class));
    }

    private DynDnsConfig createConfig(boolean enabled, String hostname, String ipAddress) {
        DynDnsConfig config = new DynDnsConfig();
        config.setEnabled(enabled);
        config.setHostname(hostname);
        config.setIpAddress(ipAddress);
        return config;
    }
}
