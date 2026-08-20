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
import com.google.inject.name.Named;
import org.eblocker.server.common.data.BlockDevice;
import org.eblocker.server.common.data.BlockDevicesList;
import org.eblocker.server.common.system.ScriptRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Optional;

public class DiskMountService {
    private static final Logger LOG = LoggerFactory.getLogger(DiskMountService.class);
    public static final String FSTYPE_VFAT = "vfat";
    private final ScriptRunner scriptRunner;
    private final String listBlockDevicesCommand;
    private final String mountCommand;
    private final String unmountCommand;
    private final String mountPoint;
    private final ObjectMapper objectMapper;

    public DiskMountService(ScriptRunner scriptRunner,
                            @Named("list.block-devices.command") String listBlockDevicesCommand,
                            @Named("external.disk.mount.command") String mountCommand,
                            @Named("external.disk.unmount.command") String unmountCommand,
                            @Named("external.disk.mountpoint") String mountPoint
                            ) {
        this.scriptRunner = scriptRunner;
        this.listBlockDevicesCommand = listBlockDevicesCommand;
        this.mountCommand = mountCommand;
        this.unmountCommand = unmountCommand;
        this.mountPoint = mountPoint;
        this.objectMapper = new ObjectMapper();
    }

    public boolean isExternalDiskMounted() throws IOException {
        BlockDevicesList blockDevices = getBlockDevices();
        return blockDevices.getBlockdevices().stream()
                .filter(disk -> disk.getChildren() != null)
                // Currently this only supports two levels: disks / partitions, logical volumes are not supported.
                .flatMap(disk -> disk.getChildren().stream())
                .filter(partition -> FSTYPE_VFAT.equals(partition.getFstype()))
                .filter(partition -> mountPoint.equals(partition.getMountpoint()))
                .count() > 0;
    }

    /**
     * Finds the first partition of type "vfat" that is not mounted.
     * @return path of the partition (e.g. "/dev/sda1") or null
     */
    public String getFirstUnmountedVfatPartition() throws IOException {
        BlockDevicesList blockDevices = getBlockDevices();
        Optional<BlockDevice> unmountedPartition = blockDevices.getBlockdevices().stream()
                .filter(disk -> disk.getChildren() != null)
                // Currently this only supports two levels: disks / partitions, logical volumes are not supported.
                .flatMap(disk -> disk.getChildren().stream())
                .filter(partition -> FSTYPE_VFAT.equals(partition.getFstype()))
                .filter(partition -> partition.getMountpoint() == null)
                .findFirst();
        if (unmountedPartition.isPresent()) {
            return unmountedPartition.get().getPath();
        } else {
            return null;
        }
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

    public void mountExternalDisk(String partition) throws IOException {
        try {
            int result = scriptRunner.runScript(mountCommand, partition, mountPoint);
            if (result != 0) {
                throw new IOException("Script '" + mountCommand + "' returned error code " + result);
            }
        } catch (IOException | InterruptedException e) {
            String msg = "Could not mount partition '" + partition + "' at '" + mountPoint + "'";
            LOG.error(msg, e);
            throw new IOException(msg, e);
        }
    }

    public void unmountExternalDisk() throws IOException {
        try {
            int result = scriptRunner.runScript(unmountCommand, mountPoint);
            if (result != 0) {
                throw new IOException("Script '" + unmountCommand + "' returned error code " + result);
            }
        } catch (IOException | InterruptedException e) {
            String msg = "Could not unmount '" + mountPoint + "'";
            LOG.error(msg, e);
            throw new IOException(msg, e);
        }
    }
}
