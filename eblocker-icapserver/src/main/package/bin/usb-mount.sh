#!/bin/bash
#
# Copyright 2026 eBlocker Open Source GmbH
#
# Licensed under the EUPL, Version 1.2 or - as soon they will be
# approved by the European Commission - subsequent versions of the EUPL
# (the "License"); You may not use this work except in compliance with
# the License. You may obtain a copy of the License at:
#
#   https://joinup.ec.europa.eu/page/eupl-text-11-12
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" basis,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
# implied. See the License for the specific language governing
# permissions and limitations under the License.
#

MOUNTPOINT="/opt/eblocker-icap/mnt"
DETECT_SERVICE="eblocker-usb-detect@"
MOUNT_OPTIONS="uid=icapd,gid=icapd"

function usage() {
    echo "usage: $0 start|stop" >&2
    exit 1
}

# Try to find an active "eblocker-usb-detect" service instance.
# Devices are enumerated from sda1 up to sdd4.
# If an active service instance is found, the corresponding device is mounted.
# If mounting fails, the search continues.
function start_mount() {
    for i in a b c d; do
        for j in 1 2 3 4; do
            DEVICE="sd$i$j"
            if systemctl is-active --quiet "$DETECT_SERVICE$DEVICE.service"; then
                echo "Trying to mount /dev/$DEVICE"
                if mount -o "$MOUNT_OPTIONS" "/dev/$DEVICE" "$MOUNTPOINT"; then
                    echo "Successfully mounted /dev/$DEVICE to $MOUNTPOINT"
                    return 0
                fi
            fi
        done
    done
    echo "Could not find a partition to mount" >&2
    return 1
}

function stop_mount() {
    umount "$MOUNTPOINT"
}

case "$1" in
    start)
        start_mount
        ;;
    stop)
        stop_mount
        ;;
    *)
        usage
        ;;
esac
