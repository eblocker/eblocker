#!/bin/sh
#
# Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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

# This script is started by "reboot_after_udpate_start.sh".
# It must run in the background.
# Its purpose is to reboot the machine when apt-get has finished.

LOGFILE=/var/log/eblocker/reboot_after_update.log
UPDATES_RUNNING=/opt/eblocker-icap/scripts/updates-running
RECOVERY_RUNNING=/opt/eblocker-icap/scripts/updates-recovery-running

echo "reboot_after_udpate started" > $LOGFILE
date >> $LOGFILE

while $UPDATES_RUNNING || $RECOVERY_RUNNING; do
    echo "updates (or recovery) are still running. Waiting for them to finish..." >> $LOGFILE
    sleep 3
done

echo "updates are done. Rebooting..." >> $LOGFILE

reboot
