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

import org.eblocker.server.common.squid.SquidConfigController;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SSLWhitelistServiceTest {

    private Path domainsPath;
    private Path ipsPath;
    private Path usersDomainPath;
    private Path usersIpPath;

    private SquidConfigController squidConfigController;
    private AppModuleService appModuleService;

    private SSLWhitelistService sslWhitelistService;

    @Before
    public void setUp() throws IOException {
        domainsPath = Files.createTempFile("domains", ".acl");
        ipsPath = Files.createTempFile("ips", ".acl");
        usersDomainPath = Files.createTempFile("user-domains", ".acl");
        usersIpPath = Files.createTempFile("user-ips", ".acl");

        squidConfigController = Mockito.mock(SquidConfigController.class);
        appModuleService = Mockito.mock(AppModuleService.class);
        sslWhitelistService = new SSLWhitelistService(domainsPath.toString(), ipsPath.toString(), usersDomainPath.toString(), usersIpPath.toString(), squidConfigController, appModuleService);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(domainsPath);
        Files.deleteIfExists(ipsPath);
        Files.deleteIfExists(usersDomainPath);
        Files.deleteIfExists(usersIpPath);
    }

    @Test
    public void update() throws IOException {
        Mockito.when(appModuleService.getAllUrlsFromEnabledModules()).thenReturn(Arrays.asList("eblockER.com", "api.eblocker.com", "xkcd.org", "images.eblocker.com", "audio.eBlOcKeR.com"));
        Mockito.when(appModuleService.getAllIPsFromEnabledModules()).thenReturn(Arrays.asList("1.2.3.4", "2.3.4.5", "2.3.4.5", "7.7.7.7"));

        sslWhitelistService.update(appModuleService, Collections.emptyList());

        List<String> domains = Files.readAllLines(domainsPath);
        Assert.assertEquals(2, domains.size());
        Assert.assertTrue(domains.contains(".eblocker.com"));
        Assert.assertTrue(domains.contains(".xkcd.org"));

        List<String> ips = Files.readAllLines(ipsPath);
        Assert.assertEquals(3, ips.size());
        Assert.assertTrue(ips.contains("1.2.3.4"));
        Assert.assertTrue(ips.contains("2.3.4.5"));
        Assert.assertTrue(ips.contains("7.7.7.7"));
    }
}
