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

import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.http.service.DeviceScanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;

class GeneralSettingsBackupProviderTest extends BackupProviderTestBase {
    GeneralSettingsBackupProvider provider;
    private DeviceScanningService deviceScanningService;
    private DeviceFactory deviceFactory;
    private SquidWarningService squidWarningService;

    @BeforeEach
    void setUp() {
        deviceScanningService = Mockito.mock(DeviceScanningService.class);
        deviceFactory = Mockito.mock(DeviceFactory.class);
        squidWarningService = Mockito.mock(SquidWarningService.class);
        provider = new GeneralSettingsBackupProvider(deviceScanningService, deviceFactory, squidWarningService);
        Mockito.when(deviceScanningService.getScanningInterval()).thenReturn(600L);
        Mockito.when(deviceFactory.isAutoEnableNewDevices()).thenReturn(true);
        Mockito.when(squidWarningService.isEnabled()).thenReturn(true);
    }

    @Test
    void testRoundtrip() throws IOException {
        exportVerifyImport(provider);
        Mockito.verify(deviceScanningService).setScanningInterval(600L);
        Mockito.verify(deviceFactory).setAutoEnableNewDevices(true);
        Mockito.verify(squidWarningService).setRecordingFailedConnectionsEnabled(true);
    }
}