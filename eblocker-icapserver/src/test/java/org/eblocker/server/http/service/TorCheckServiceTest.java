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

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.Before;
import org.junit.Test;

import org.eblocker.server.common.data.TorCheckSite;

public class TorCheckServiceTest {
    private TorCheckService service;

    @Before
    public void setUp() throws Exception {
        service = new TorCheckService("classpath:tor-check-sites.json");
    }

    @Test
    public void testHttpOnly() {
        List<TorCheckSite> result = service.getSites(false);
        assertEquals(Arrays.asList("http://torcheck.xenobite.eu/"), getUrls(result));
    }

    @Test
    public void testWithSSL() {
        List<TorCheckSite> result = service.getSites(true);
        assertEquals(
                Arrays.asList("http://torcheck.xenobite.eu/", "https://torcheck.xenobite.eu/", "https://check.torproject.org/"),
                getUrls(result));
    }

    private List<String> getUrls(List<TorCheckSite> sites) {
        return sites.stream()
            .map(TorCheckSite::getUrl)
            .collect(Collectors.toList());
    }
}
