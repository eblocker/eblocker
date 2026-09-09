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

import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.network.NetworkServices;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

class NetworkBackupProviderTest extends BackupProviderTestBase {
    private NetworkBackupProvider provider;
    private NetworkServices networkServices;

    @BeforeEach
    void setUp() {
        networkServices = Mockito.mock(NetworkServices.class);
        NetworkConfiguration networkConfiguration = new NetworkConfiguration();
        Mockito.when(networkServices.getCurrentNetworkConfiguration()).thenReturn(networkConfiguration);
        provider = new NetworkBackupProvider(networkServices);
    }

    @Test
    void roundTrip() throws IOException {
        // Although the import does not do anything in eOS 3, the backup must be importable:
        exportVerifyImport(provider);
    }
}