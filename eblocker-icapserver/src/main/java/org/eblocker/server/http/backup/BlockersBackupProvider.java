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

import com.google.inject.Inject;
import org.eblocker.server.common.blocker.Blocker;
import org.eblocker.server.common.blocker.BlockerIdTypeIdCache;
import org.eblocker.server.common.blocker.BlockerService;
import org.eblocker.server.common.blocker.Category;
import org.eblocker.server.common.blocker.TypeId;
import org.eblocker.server.common.service.FeatureServicePublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.stream.Collectors;

/**
 * Backs up and restores ad/tracker blockers that are not custom black-/whitelists and not parental control blockers.
 */
public class BlockersBackupProvider extends BackupProvider {
    private static final Logger LOG = LoggerFactory.getLogger(BlockersBackupProvider.class);
    public static final String BLOCKERS_ENTRY = "eblocker-config/blockers.json";

    private final BlockerIdTypeIdCache idCache;
    private final BlockerService blockerService;
    private final FeatureServicePublisher featureService;

    @Inject
    public BlockersBackupProvider(BlockerService blockerService,
                                  BlockerIdTypeIdCache idCache,
                                  FeatureServicePublisher featureService
    ) {
        this.blockerService = blockerService;
        this.idCache = idCache;
        this.featureService = featureService;
    }

    @Override
    public void exportConfiguration(JarOutputStream outputStream) throws IOException {
        BlockersBackup backup = createBackup();
        byte[] backupBytes = objectMapper.writeValueAsBytes(backup);
        writeNextEntry(outputStream, BLOCKERS_ENTRY, backupBytes);
    }

    private BlockersBackup createBackup() {
        BlockersBackup backup = new BlockersBackup();
        List<Blocker> blockers = getCurrentBlockers();

        backup.setBlockers(blockers);

        // IDs of blockers are only temporary. So we need to map them to (Type, ID) tuples.
        Map<Integer, TypeId> typeIds = blockers.stream()
                .map(Blocker::getId)
                .filter(id -> idCache.getTypeId(id) != null)
                .collect(Collectors.toMap(Function.identity(), id -> idCache.getTypeId(id)));

        backup.setTypeIds(typeIds);

        backup.setCaptivePortalRedirectorState(featureService.getGoogleCaptivePortalRedirectorState());
        backup.setCompressionMode(featureService.getCompressionMode());
        backup.setDntHeaderState(featureService.getDntHeaderState());
        backup.setHttpReferrerRemovingState(featureService.getHTTPRefererRemovingState());
        backup.setWebRtcBlockingState(featureService.getWebRTCBlockingState());

        return backup;
    }

    private List<Blocker> getCurrentBlockers() {
        List<Blocker> blockers = blockerService.getBlockers();

        // Ignore: custom and parental control blockers are stored in the UsersBackup
        return blockers.stream()
                .filter(blocker -> blocker.getCategory() != Category.CUSTOM && blocker.getCategory() != Category.PARENTAL_CONTROL)
                .collect(Collectors.toList());
    }

    @Override
    public void importConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, false);
    }

    @Override
    public void verifyConfiguration(JarInputStream inputStream, int schemaVersion) throws IOException {
        importConfiguration(inputStream, schemaVersion, true);
    }

    private void importConfiguration(JarInputStream inputStream, int schemaVersion, boolean dryRun) throws IOException {
        getNextEntry(inputStream, BLOCKERS_ENTRY);
        BlockersBackup backup = objectMapper.readValue(inputStream, BlockersBackup.class);
        if (backup == null) {
            throw new CorruptedBackupException("Deserialized backup object is null");
        }

        if (!dryRun) {
            restoreBackup(backup);
        }
    }

    private void restoreBackup(BlockersBackup backup) throws IOException {
        enableBuiltinBlockers(backup);
        deleteUserDefinedBlockers();
        createUserDefinedBlockers(backup);
        featureService.setGoogleCaptivePortalRedirectorState(backup.getCaptivePortalRedirectorState());
        featureService.setCompressionMode(backup.getCompressionMode());
        featureService.setDntHeaderState(backup.getDntHeaderState());
        featureService.setHTTPRefererRemovingState(backup.getHttpReferrerRemovingState());
        featureService.setWebRTCBlockingState(backup.getWebRtcBlockingState());
    }

    private void deleteUserDefinedBlockers() {
        List<Blocker> userDefinedBlockers = getCurrentBlockers().stream()
                .filter(blocker -> !blocker.isProvidedByEblocker())
                .collect(Collectors.toList());
        for (Blocker blocker: userDefinedBlockers) {
            blockerService.deleteBlocker(blocker.getId());
        }
    }

    private void createUserDefinedBlockers(BlockersBackup backup) {
        List<Blocker> userDefinedBlockers = backup.getBlockers().stream()
                .filter(blocker -> !blocker.isProvidedByEblocker())
                .collect(Collectors.toList());
        for (Blocker blocker: userDefinedBlockers) {
            blockerService.createBlockerSynchronously(blocker);
        }
    }

    private void enableBuiltinBlockers(BlockersBackup backup) {
        List<Blocker> builtinBlockers = backup.getBlockers().stream()
                .filter(Blocker::isProvidedByEblocker)
                .collect(Collectors.toList());

        Map<Integer, TypeId> typeIds = backup.getTypeIds();

        Map<TypeId, Blocker> blockersByTypeId = getCurrentBlockers().stream()
                .filter(blocker -> idCache.getTypeId(blocker.getId()) != null)
                .collect(Collectors.toMap(blocker -> idCache.getTypeId(blocker.getId()), Function.identity()));

        for (Blocker blocker: builtinBlockers) {
            TypeId typeId = typeIds.get(blocker.getId());
            Blocker currentBlocker = blockersByTypeId.get(typeId);
            if (currentBlocker != null) {
                // be very conservative: just enable/disable the blocker, because that's the only thing the user can edit currently
                currentBlocker.setEnabled(blocker.isEnabled());
            }
            if (blockersByTypeId.containsKey(typeId)) {
                blockerService.updateBlocker(blockersByTypeId.get(typeId));
            } else {
                LOG.warn("Could not set enabled state of built-in blocker {}", blocker.getName());
            }
        }
    }
}
