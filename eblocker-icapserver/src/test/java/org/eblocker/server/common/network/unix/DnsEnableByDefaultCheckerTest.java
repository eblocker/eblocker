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
package org.eblocker.server.common.network.unix;

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.dns.DnsCheckDone;
import org.eblocker.server.common.network.NetworkServices;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;

public class DnsEnableByDefaultCheckerTest {

    private NetworkConfiguration networkConfiguration;
    private DataSource dataSource;
    private NetworkServices networkServices;
    private Path disableFile;

    @Before
    public void setup() throws IOException {
        networkConfiguration = new NetworkConfiguration();
        networkConfiguration.setDnsServer(false);
        networkServices = Mockito.mock(NetworkServices.class);
        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfiguration);

        dataSource = Mockito.mock(DataSource.class);
        disableFile = Files.createTempFile("dns-disable-by-default", ".flag");
    }

    @After
    public void tearDown() throws IOException {
        Files.delete(disableFile);
    }

    @Test
    public void testCheckDefaultEnableInitialCheck() {
        DnsEnableByDefaultChecker checker = new DnsEnableByDefaultChecker(true, "/does/not/exist", dataSource, networkServices);
        checker.check();

        Mockito.verify(networkServices).configureEblockerDns(networkConfiguration);
        Mockito.verify(networkServices).applyNetworkConfiguration(networkConfiguration);
        Assert.assertTrue(networkConfiguration.isDnsServer());

        ArgumentCaptor<DnsCheckDone> captor = ArgumentCaptor.forClass(DnsCheckDone.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertTrue(captor.getValue().isResult());
    }

    @Test
    public void testCheckDefaultEnableSubquentCheck() {
        Mockito.when(dataSource.get(DnsCheckDone.class)).thenReturn(new DnsCheckDone(new Date(), true));

        DnsEnableByDefaultChecker checker = new DnsEnableByDefaultChecker(true, "/does/not/exist", dataSource, networkServices);
        checker.check();

        Mockito.verify(networkServices, Mockito.never()).configureEblockerDns(Mockito.any());
        Assert.assertFalse(networkConfiguration.isDnsServer());
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(DnsCheckDone.class));
    }

    @Test
    public void testCheckDefaultEnableOverrideInitialCheck() {
        DnsEnableByDefaultChecker checker = new DnsEnableByDefaultChecker(true, disableFile.toString(), dataSource, networkServices);
        checker.check();

        Mockito.verify(networkServices, Mockito.never()).configureEblockerDns(Mockito.any());
        Assert.assertFalse(networkConfiguration.isDnsServer());

        ArgumentCaptor<DnsCheckDone> captor = ArgumentCaptor.forClass(DnsCheckDone.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertFalse(captor.getValue().isResult());
    }

    @Test
    public void testCheckDefaultEnableOverrideSubsequentCheck() {
        Mockito.when(dataSource.get(DnsCheckDone.class)).thenReturn(new DnsCheckDone(new Date(), true));

        DnsEnableByDefaultChecker checker = new DnsEnableByDefaultChecker(true, disableFile.toString(), dataSource, networkServices);
        checker.check();

        Mockito.verify(networkServices, Mockito.never()).configureEblockerDns(Mockito.any());
        Assert.assertFalse(networkConfiguration.isDnsServer());
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(DnsCheckDone.class));
    }

    @Test
    public void testCheckDefaultDisableInitialCheck() {
        DnsEnableByDefaultChecker checker = new DnsEnableByDefaultChecker(false, "/does/not/exist", dataSource, networkServices);
        checker.check();

        Mockito.verify(networkServices, Mockito.never()).configureEblockerDns(Mockito.any());
        Assert.assertFalse(networkConfiguration.isDnsServer());

        ArgumentCaptor<DnsCheckDone> captor = ArgumentCaptor.forClass(DnsCheckDone.class);
        Mockito.verify(dataSource).save(captor.capture());
        Assert.assertFalse(captor.getValue().isResult());
    }

    @Test
    public void testCheckDefaultDisableSubsequentCheck() {
        Mockito.when(dataSource.get(DnsCheckDone.class)).thenReturn(new DnsCheckDone(new Date(), false));

        DnsEnableByDefaultChecker checker = new DnsEnableByDefaultChecker(false, "/does/not/exist", dataSource, networkServices);
        checker.check();

        Mockito.verify(networkServices, Mockito.never()).configureEblockerDns(Mockito.any());
        Assert.assertFalse(networkConfiguration.isDnsServer());
        Mockito.verify(dataSource, Mockito.never()).save(Mockito.any(DnsCheckDone.class));
    }
}
