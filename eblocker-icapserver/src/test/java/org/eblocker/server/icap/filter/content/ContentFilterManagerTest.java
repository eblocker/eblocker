/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.icap.filter.content;

import org.eblocker.server.common.data.DataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class ContentFilterManagerTest {
    private ContentFilterManager manager;
    private ContentFilterService service;
    private DataSource dataSource;
    private ContentFilter filter;
    private Path file;

    @Before
    public void setUp() throws IOException {
        service = Mockito.mock(ContentFilterService.class);
        dataSource = Mockito.mock(DataSource.class);

        filter = new ElementHidingFilter(
                List.of(new DomainEntity("google.*")),
                ContentAction.ADD,
                ".ads");

        file = Files.createTempFile(this.getClass().getSimpleName(),".txt");
        Files.writeString(file, filter.toString());
        file.toFile().deleteOnExit();

        manager = new ContentFilterManager(file.toString(), service, dataSource);
    }

    @Test
    public void testEnable() {
        Assert.assertEquals(0, manager.getLastUpdate());

        Mockito.when(dataSource.isContentFilterEnabled()).thenReturn(true);
        InOrder inOrder = Mockito.inOrder(service);
        manager.setEnabled(true);
        Mockito.verify(dataSource).setContentFilterEnabled(true);
        inOrder.verify(service).setFilterList(ContentFilterList.emptyList());
        inOrder.verify(service).setFilterList(new ContentFilterList(List.of(filter)));
        inOrder.verifyNoMoreInteractions();
        Assert.assertTrue(manager.getLastUpdate() > 0);
    }

    @Test
    public void testUpdateNotModified() {
        Mockito.when(dataSource.isContentFilterEnabled()).thenReturn(true);
        InOrder inOrder = Mockito.inOrder(service);
        manager.setEnabled(true);
        manager.updateIfModified();
        inOrder.verify(service).setFilterList(ContentFilterList.emptyList());
        inOrder.verify(service).setFilterList(new ContentFilterList(List.of(filter)));
        inOrder.verifyNoMoreInteractions();
    }

    @Test
    public void testUpdateModified() throws IOException {
        Mockito.when(dataSource.isContentFilterEnabled()).thenReturn(true);
        InOrder inOrder = Mockito.inOrder(service);
        manager.setEnabled(true);

        ContentFilter updatedFilter = new ElementHidingFilter(
                List.of(new Domain("example.com")),
                ContentAction.ADD,
                ".adbanner"
        );
        Files.writeString(file, updatedFilter.toString());

        manager.updateIfModified();
        inOrder.verify(service).setFilterList(ContentFilterList.emptyList());
        inOrder.verify(service).setFilterList(new ContentFilterList(List.of(filter)));
        inOrder.verify(service).setFilterList(new ContentFilterList(List.of(updatedFilter)));
        inOrder.verifyNoMoreInteractions();
    }
}