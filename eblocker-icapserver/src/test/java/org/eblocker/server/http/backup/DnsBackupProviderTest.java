/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.http.backup;

import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.http.service.DnsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;

class DnsBackupProviderTest extends BackupProviderTestBase {
    DnsBackupProvider provider;
    DnsService dnsService;

    @BeforeEach
    void setUp() {
        dnsService = Mockito.mock(DnsService.class);
        provider = new DnsBackupProvider(dnsService);

        DnsResolvers resolvers = new DnsResolvers();
        resolvers.setCustomNameServers(List.of("8.8.8.8", "9.9.9.9"));
        Mockito.when(dnsService.getDnsResolvers()).thenReturn(resolvers);
    }

    @Test
    void testRoundtrip() throws IOException {
        exportVerifyImport(provider);
        Mockito.verify(dnsService).setDnsResolvers(Mockito.argThat(resolvers -> resolvers.getCustomNameServers().equals(List.of("8.8.8.8", "9.9.9.9"))));
    }
}