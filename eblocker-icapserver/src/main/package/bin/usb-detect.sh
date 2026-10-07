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

ACTION="$1"
DEVICE="$2"

function usage() {
    echo "usage: $0 start|stop <device>" >&2
    exit 1
}

function start_detect() {
    echo "eblocker-usb-detect service is started for device $DEVICE"
}

function stop_detect() {
    if findmnt "$DEVICE"; then
        echo "Device $DEVICE is mounted. Stopping eblocker-usb-mount.service"
        systemctl stop eblocker-usb-mount.service
    fi
}

if [ $# -ne 2 ]; then
    usage
fi

case "$ACTION" in
    start)
        start_detect
        ;;
    stop)
        stop_detect
        ;;
    *)
        usage
        ;;
esac
