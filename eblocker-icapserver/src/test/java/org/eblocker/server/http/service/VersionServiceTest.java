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
import org.eblocker.server.common.data.VersionInfo;
import org.junit.Test;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

public class VersionServiceTest {

    @Test
    public void test_get() {
        // Prepare infrastructure
        DataSource dataSource = Mockito.mock(DataSource.class);
        VersionService versionService = new VersionService(dataSource);
        VersionInfo versionInfo = new VersionInfo("1.2.3-FINAL");

        // Prepare mock
        when(dataSource.get(VersionInfo.class)).thenReturn(versionInfo);

        // Execute getter
        VersionInfo loaded = versionService.get();

        // Assert correct result
        assertNotNull(versionInfo);
        assertEquals(versionInfo.getVersionEBlockerOs(), loaded.getVersionEBlockerOs());

        // Assert mock usage
        verify(dataSource).get(VersionInfo.class);
        verifyNoMoreInteractions(dataSource);
    }

    @Test
    public void test_set() {
        // Prepare infrastructure
        DataSource dataSource = Mockito.mock(DataSource.class);
        VersionService versionService = new VersionService(dataSource);
        VersionInfo versionInfo = new VersionInfo("1.2.3-FINAL");

        // Execute getter
        versionService.set(versionInfo);

        // Assert mock usage
        verify(dataSource).save(versionInfo);
        verifyNoMoreInteractions(dataSource);
    }

}
