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
package org.eblocker.server.http.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.BlockDevice;
import org.eblocker.server.common.data.BlockDevicesList;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Singleton
public class DiskInfoService {
    private static final Logger LOG = LoggerFactory.getLogger(DiskInfoService.class);
    private final ScriptRunner scriptRunner;
    private final String listBlockDevicesCommand;
    private final String mountPoint;
    private final ObjectMapper objectMapper;

    @Inject
    public DiskInfoService(ScriptRunner scriptRunner,
                           @Named("list.block-devices.command") String listBlockDevicesCommand,
                           @Named("external.disk.mountpoint") String mountPoint) {
        this.scriptRunner = scriptRunner;
        this.listBlockDevicesCommand = listBlockDevicesCommand;
        this.mountPoint = mountPoint;
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Returns the partition that is mounted at the configured mountpoint or null if no partition is mounted.
     * Currently, this method only supports two levels: disks / partitions, logical volumes are not supported.
     * To provide the user with a friendly name of the USB drive, the disk's model field is copied to the
     * partition object.
     *
     * @return BlockDevice object representing the partition or null
     * @throws IOException
     */
    public BlockDevice getMountedPartition() throws IOException {
        BlockDevicesList blockDevices = getBlockDevices();
        List<BlockDevice> disks = blockDevices.getBlockdevices();
        for (BlockDevice disk : disks) {
            List<BlockDevice> partitions = disk.getChildren();
            if (partitions != null) {
                for (BlockDevice partition : partitions) {
                    if (mountPoint.equals(partition.getMountpoint())) {
                        // Copy model from disk to partition (for user feedback):
                        partition.setModel(disk.getModel());
                        return partition;
                    }
                }
            }
        }
        return null;
    }

    private BlockDevicesList getBlockDevices() throws IOException {
        File outFile = File.createTempFile("DiskMountService", ".json");
        try {
            scriptRunner.runScript(listBlockDevicesCommand, outFile.getAbsolutePath());
            return objectMapper.readValue(outFile, BlockDevicesList.class);
        } catch (IOException | InterruptedException e) {
            String msg = "Could not list block devices";
            LOG.error(msg, e);
            throw new IOException(msg, e);
        } finally {
            outFile.delete();
        }
    }
}
