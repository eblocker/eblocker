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

import org.eblocker.server.common.blocker.Blocker;
import org.eblocker.server.common.blocker.BlockerIdTypeIdCache;
import org.eblocker.server.common.blocker.BlockerService;
import org.eblocker.server.common.blocker.BlockerType;
import org.eblocker.server.common.blocker.BlockerUtils;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.TypeId;
import org.eblocker.server.common.service.FeatureServicePublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.List;
import java.util.Map;

public class BlockersBackupProviderTest extends BackupProviderTestBase {
    private BlockersBackupProvider exportProvider, importProvider;
    private BlockerService exportBlockerService, importBlockerService;
    private FeatureServicePublisher exportFeatureService, importFeatureService;
    private Blocker builtinBlocker, userDefinedBlocker, otherBlocker;

    @BeforeEach
    void setUp() {
        exportBlockerService = Mockito.mock(BlockerService.class);
        importBlockerService = Mockito.mock(BlockerService.class);
        exportFeatureService = Mockito.mock(FeatureServicePublisher.class);
        importFeatureService = Mockito.mock(FeatureServicePublisher.class);
        BlockerIdTypeIdCache exportIdCache = new BlockerIdTypeIdCache();
        BlockerIdTypeIdCache importIdCache = new BlockerIdTypeIdCache();
        exportProvider = new BlockersBackupProvider(exportBlockerService, exportIdCache, exportFeatureService);
        importProvider = new BlockersBackupProvider(importBlockerService, importIdCache, importFeatureService);

        builtinBlocker = new Blocker(0, Map.of("en", "Builtin blocker"), null, BlockerType.DOMAIN, Category.ADS, null, true, null, null, null, null, null, null, true, null);
        userDefinedBlocker = new Blocker(1000, Map.of("en", "User-defined blocker"), null, BlockerType.DOMAIN, Category.ADS, null, false, null, "ads.com", null, null, null, null, true, null);
        otherBlocker = new Blocker(1007, Map.of("en", "Other blocker, to be deleted"), null, BlockerType.DOMAIN, Category.ADS, null, false, null, "more-ads.com", null, null, null, null, true, null);
        List<Blocker> exportBlockers = List.of(builtinBlocker, userDefinedBlocker);
        List<Blocker> importBlockers = List.of(builtinBlocker, otherBlocker);

        fillCache(exportIdCache, exportBlockers);
        fillCache(importIdCache, importBlockers);

        Mockito.when(exportBlockerService.getBlockers()).thenReturn(exportBlockers);
        Mockito.when(importBlockerService.getBlockers()).thenReturn(importBlockers);

        Mockito.when(exportFeatureService.getDntHeaderState()).thenReturn(true);
    }

    // In production code, BlockerService.getBlockers() fills the cache. Here we must do it "manually":
    private void fillCache(BlockerIdTypeIdCache idCache, List<Blocker> blockers) {
        blockers.forEach(blocker -> idCache.getId(new TypeId(BlockerUtils.mapBlockerType(blocker.getType()), blocker.getId())));
    }

    @Test
    public void testRoundtrip() throws IOException {
        byte[] backup = exportBackup(exportProvider);
        verifyBackup(backup, importProvider);
        importBackup(backup, importProvider);

        Mockito.verify(importBlockerService).createBlockerSynchronously(blockerWithNameOf(userDefinedBlocker));
        Mockito.verify(importBlockerService).updateBlocker(blockerWithNameOf(builtinBlocker));
        Mockito.verify(importBlockerService).deleteBlocker(1007);
        Mockito.verify(importFeatureService).setDntHeaderState(true);
    }

    private Blocker blockerWithNameOf(Blocker blocker) {
        return Mockito.argThat(b -> blocker.getName().equals(b.getName()));
    }
}