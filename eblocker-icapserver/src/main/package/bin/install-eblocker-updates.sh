#!/bin/bash
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

# Minimal path needed for apt-get
export PATH=/usr/sbin:/usr/bin:/sbin:/bin

# We have no terminal
export DEBIAN_FRONTEND=noninteractive

# Change file permission if running on Debian Stretch and above
id -u _apt > /dev/null 2>&1
if [ $? -eq 0 ]; then
    chown _apt /opt/eblocker-icap/keys/license.cert
    chown _apt /opt/eblocker-icap/keys/license.key
fi

apt-get -y update || {
    echo "ERROR: Could not run apt-get update" >&2
    exit 1
}

# Security check: do not remove Icapserver or UI (because of some held back packages)
CHECK_CMD='apt-get -y --just-print dist-upgrade'
$CHECK_CMD | egrep '^Remv\s+(eblocker-icapserver|eblocker-ui)'
if [ $? -ne 1 ]; then
    echo "ERROR: Could not update. Cannot allow removal of eblocker-icapserver or eblocker-ui." >&2
    echo "Output of '$CHECK_CMD':" >&2
    $CHECK_CMD >&2
    exit 1
fi

# Perform the upgrade
apt-get -y dist-upgrade && apt-get clean && sleep 30
