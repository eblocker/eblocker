#!/bin/bash
#
# Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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

ERROR_TAG="eBlocker_Update_Recovery_Error"

# Minimal path needed for apt-get
export PATH=/usr/sbin:/usr/bin:/sbin:/bin

# We have no terminal
export DEBIAN_FRONTEND=noninteractive

echo "Attempting update recovery"
date

dpkg --force-confold --force-confdef --configure -a || {
    echo "$ERROR_TAG: Running 'dpkg --configure -a' failed." >&2
    exit 1
}

# Recovery seems to have been successful, remove update error log:
rm -f /var/log/eblocker/install-eblocker-updates.errors.log

# A reboot ensures that all services (which might have been re-configured) are restarted:
sync
reboot
