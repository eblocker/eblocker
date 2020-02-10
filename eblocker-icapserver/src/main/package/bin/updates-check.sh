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

# Checks whether one or more updates are available
# Returns:
# 0: Updates available
# 1: No Updates available
# >1: Error codes

echo "Executing update-check ..."

# Change license file permission if running on Debian Stretch and above
id -u _apt > /dev/null 2>&1
if [ $? -eq 0 ]; then
    chown _apt /opt/eblocker-icap/keys/license.cert
    chown _apt /opt/eblocker-icap/keys/license.key
fi

apt-get -y update
apt-get -y --just-print dist-upgrade | egrep ^Inst
result=$?

echo "... returns: " $result
exit $result

