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
package org.eblocker.server.common.data;

import java.util.List;

/**
 * Represents a disk device or partition as produced by the Linux utility "lsblk" with option "--json".
 */
public class BlockDevice {
    public final static String PARTTYPE_EFI = "c12a7328-f81f-11d2-ba4b-00a0c93ec93b";
    public final static String FSTYPE_VFAT = "vfat";
    public final static String FSTYPE_EXFAT = "exfat";

    private String path, name, model, label, mountpoint, fstype, parttype;
    private boolean hotplug;
    private List<BlockDevice> children;

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getMountpoint() {
        return mountpoint;
    }

    public void setMountpoint(String mountpoint) {
        this.mountpoint = mountpoint;
    }

    /**
     * Returns the file system type of a partition, e.g. "ext4", "swap", "vfat", "exfat", etc.
     */
    public String getFstype() {
        return fstype;
    }

    public void setFstype(String fstype) {
        this.fstype = fstype;
    }

    /**
     * Returns the partition type. This is either a hex code or a UUID.
     */
    public String getParttype() {
        return parttype;
    }

    public void setParttype(String parttype) {
        this.parttype = parttype;
    }

    /**
     * Returns true if the device is a removable device, e.g. USB
     */
    public boolean isHotplug() {
        return hotplug;
    }

    public void setHotplug(boolean hotplug) {
        this.hotplug = hotplug;
    }

    public List<BlockDevice> getChildren() {
        return children;
    }

    public void setChildren(List<BlockDevice> children) {
        this.children = children;
    }

    /**
     * Returns true if the partition contains a FAT file system.
     * Always returns false for disk devices.
     */
    public boolean isFat() {
        return FSTYPE_VFAT.equals(fstype) || FSTYPE_EXFAT.equals(fstype);
    }

    /**
     * Returns true if the partition is an EFI partition.
     * Always returns false for disk devices.
     */
    public boolean isEfi() {
        return PARTTYPE_EFI.equals(parttype);
    }
}
