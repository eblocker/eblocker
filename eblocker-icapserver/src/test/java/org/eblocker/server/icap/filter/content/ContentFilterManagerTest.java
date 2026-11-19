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
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.ExternalDefinition;
import org.eblocker.server.common.blocker.Format;
import org.eblocker.server.common.blocker.Type;
import org.eblocker.server.common.blocker.UpdateInterval;
import org.eblocker.server.common.blocker.UpdateStatus;
import org.eblocker.server.icap.service.ScriptletService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

public class ContentFilterManagerTest {
    private ContentFilterManager manager;
    private ContentFilterService service;
    private DataSource dataSource;
    private ContentFilter filter;
    private Path file;
    private Path customDirectory;

    //We have a timing problem. Specifically, the file is written so quickly that the ContentFileManager cannot recognise the change. The problem has not yet been observed in production. Refactoring is also in the pipeline. There is this workaround variable so that the test works again. After refactoring, the test can then be reverted.
    private Instant currentFileInstant = Instant.now().minusSeconds(30);

    @Before
    public void setUp() throws IOException {
        service = Mockito.mock(ContentFilterService.class);
        dataSource = Mockito.mock(DataSource.class);
        Mockito.when(dataSource.isCustomElementHidingEnabled()).thenReturn(true);

        filter = new ElementHidingFilter(
                List.of(new DomainEntity("google.*")),
                ContentAction.ADD,
                ".ads");

        file = Files.createTempFile(this.getClass().getSimpleName(),".txt");
        Files.writeString(file, filter.toString());
        Files.setLastModifiedTime(file, FileTime.from(currentFileInstant));
        file.toFile().deleteOnExit();

        customDirectory = Files.createTempDirectory("element-hiding-test");
        manager = new ContentFilterManager(file.toString(), customDirectory.toString(), service, dataSource);
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
    public void testAppendCustomRulePreservesExistingRulesAndIgnoresDuplicates() throws IOException {
        manager.setCustomRules(List.of("example.org##.existing"));
        Assert.assertTrue(manager.appendCustomRule("www.example.org##.new-ad"));
        Assert.assertFalse(manager.appendCustomRule("www.example.org##.new-ad"));
        Assert.assertEquals(List.of("example.org##.existing", "www.example.org##.new-ad"), manager.getCustomRules());
    }

    @Test
    public void testAppendCustomRuleRejectsInvalidRule() throws IOException {
        manager.setCustomRules(List.of("example.org##.existing"));
        try {
            manager.appendCustomRule("other.example##script[onclick=evil]");
            Assert.fail("Invalid selector accepted");
        } catch (IllegalArgumentException expected) {
            Assert.assertEquals(List.of("example.org##.existing"), manager.getCustomRules());
        }
    }

    @Test
    public void testCustomRulesWorkWithoutBundledFilter() throws IOException {
        List<String> rules = manager.setCustomRules(List.of("example.org##.ad-slot"));
        Assert.assertEquals(List.of("example.org##.ad-slot"), rules);
        Assert.assertEquals(rules, manager.getCustomRules());
        Mockito.verify(service).setFilterList(new ContentFilterList(List.of(
                new ElementHidingFilter(List.of(new Domain("example.org")), ContentAction.ADD, ".ad-slot")
        )));
    }

    @Test
    public void testExternalContentListIsInjectedAndCanBeDisabled() throws IOException {
        Path external = Files.createTempFile("external-content", ".txt");
        Files.writeString(external, "example.org##.external-ad\n");
        ExternalDefinition definition = new ExternalDefinition(1000, "External content", "", Category.CONTENT,
                Type.CONTENT, 1000, Format.EASYLIST, null, UpdateInterval.NEVER, UpdateStatus.READY,
                null, external.toString(), true, "content");
        Mockito.when(dataSource.getAll(ExternalDefinition.class)).thenReturn(List.of(definition));
        ContentFilterService realService = new ContentFilterService(
                Mockito.mock(ScriptletService.class), "display: none;", 50);
        ContentFilterManager realManager = new ContentFilterManager(
                file.toString(), customDirectory.toString(), realService, dataSource);

        Assert.assertTrue(realService.getHtmlToInject("www.example.org").contains(".external-ad"));
        definition.setEnabled(false);
        realManager.refresh();
        Assert.assertEquals("", realService.getHtmlToInject("www.example.org"));
        Files.deleteIfExists(external);
    }

    @Test
    public void testPickerIdSelectorIsAccepted() throws IOException {
        String rule = "www.bild.de###harzsgqwhvs53mgvf7y0afy7cn3y0ewwyk";
        Assert.assertEquals(List.of(rule), manager.setCustomRules(List.of(rule)));
        Assert.assertEquals(List.of(rule), manager.getCustomRules());
    }

    @Test
    public void testMultipleExactSelectorsOnOneDomain() throws IOException {
        String rule = "www.bild.de###first,#second,#third";
        Assert.assertEquals(List.of(rule), manager.setCustomRules(List.of(rule)));
        Assert.assertEquals(List.of(rule), manager.getCustomRules());
    }

    @Test
    public void testIdPrefixSelectorIsAccepted() throws IOException {
        String rule = "www.bild.de##[id^=\"harzsgqwhvs53mgvf7y0afy7cn3y0eww\"]";
        Assert.assertEquals(List.of(rule), manager.setCustomRules(List.of(rule)));
        Assert.assertEquals(List.of(rule), manager.getCustomRules());
    }

    @Test
    public void testCustomRulesCanBeDisabledWithoutDeletion() throws IOException {
        ContentFilterService realService = new ContentFilterService(
                Mockito.mock(ScriptletService.class), "display: none;", 50);
        ContentFilterManager realManager = new ContentFilterManager(
                file.toString(), customDirectory.toString(), realService, dataSource);
        realManager.setCustomRules(List.of("example.org###banner"));
        Assert.assertFalse(realService.getHtmlToInject("www.example.org").isEmpty());

        Mockito.when(dataSource.isCustomElementHidingEnabled()).thenReturn(false);
        realManager.setCustomElementHidingEnabled(false);
        Assert.assertEquals("", realService.getHtmlToInject("www.example.org"));
        Assert.assertEquals(List.of("example.org###banner"), realManager.getCustomRules());

        Mockito.when(dataSource.isCustomElementHidingEnabled()).thenReturn(true);
        realManager.setCustomElementHidingEnabled(true);
        Assert.assertFalse(realService.getHtmlToInject("www.example.org").isEmpty());
    }

    @Test
    public void testSavedRuleIsInjectedOnlyOnMatchingDomain() throws IOException {
        ContentFilterService realService = new ContentFilterService(
                Mockito.mock(ScriptletService.class), "display: none;", 50);
        ContentFilterManager realManager = new ContentFilterManager(
                file.toString(), customDirectory.toString(), realService, dataSource);
        realManager.setCustomRules(List.of("example.org##.advertisement"));

        Assert.assertEquals("<style>\n.advertisement {display: none;}\n</style>\n",
                realService.getHtmlToInject("www.example.org"));
        Assert.assertEquals("", realService.getHtmlToInject("www.example.com"));

        realManager.setCustomRules(List.of());
        Assert.assertEquals("", realService.getHtmlToInject("www.example.org"));
    }

    @Test
    public void testRejectsScriptletsAndCssInjection() {
        for (String rule : List.of("example.org##+js(foo)", "example.org##</style>", "example.org##.a##.b", "example.org##.a,,.b", "example.org##[id^=\"short\"]")) {
            try {
                manager.setCustomRules(List.of(rule));
                Assert.fail("Accepted unsafe rule");
            } catch (IllegalArgumentException expected) {
                // expected
            } catch (IOException e) {
                Assert.fail(e.getMessage());
            }
        }
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
        currentFileInstant = currentFileInstant.plusSeconds(2);
        Files.setLastModifiedTime(file, FileTime.from(currentFileInstant));

        manager.updateIfModified();
        inOrder.verify(service).setFilterList(ContentFilterList.emptyList());
        inOrder.verify(service).setFilterList(new ContentFilterList(List.of(filter)));
        inOrder.verify(service).setFilterList(new ContentFilterList(List.of(updatedFilter)));
        inOrder.verifyNoMoreInteractions();
    }
}