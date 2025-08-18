#!/bin/sh
#
# Copyright 2025 eBlocker Open Source UG (haftungsbeschraenkt)
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

# eBlocker factory reset / update recovery / status report

# Load defaults:
. /etc/default/eblocker-reset

console_log() {
    local message="$1"
    echo "$message" > $LOGGING_CONSOLE
}

perform_factory_reset() {
    /opt/eblocker-icap/scripts/factory_reset

    date >> $EBLOCKER_RESET_FILE
    echo "eBlocker factory reset done." >> $EBLOCKER_RESET_FILE
}

perform_update_recovery() {
    console_log "*** PERFORMING UPDATE RECOVERY ***"

    date >> $EBLOCKER_UPDATERECOVERY_FILE
    dpkg --force-confold --force-confdef --configure -a &>> $EBLOCKER_UPDATERECOVERY_FILE
    RETVAL=$?

    if [ $RETVAL -eq 0 ]
    then
        echo "Update recovery - Finished without error." >> $EBLOCKER_UPDATERECOVERY_FILE
    else
        echo "Update recovery - Finished with error ($RETVAL)." >> $EBLOCKER_UPDATERECOVERY_FILE
    fi
    poweroff
}

perform_status_report() {
    IPADDRS=$(ip addr list eth0)
    DATE=$(date)

    cat <<EOF > $EBLOCKER_STATUS_FILE
<!DOCTYPE html>
<html>
  <head>
    <title>eBlocker Status</title>
    <style>
      body {
        font-family: 'Helvetica Neue', Helvetica, Arial, sans-serif;
      }
      h1 {
        margin: 12px;
      }
      div {
        margin: 12px;
        padding: 12px;
      }
      .info {
        background-color: #f0f0f0;
      }
      .ok {
        background-color: #00ff00;
      }
      .warning {
        background-color: #ffff00;
      }
      .error {
        color: #ffffff;
        background-color: #ff0000;
      }
    </style>
  </head>
  <body>
    <h1>eBlocker Status</h1>
    <div class="info">eBlocker has booted</div>
    <div class="info">IP addresses:<pre>$IPADDRS</pre></div>
    <div class="warning">eBlocker console has not been started (yet)</div>
    <div class="info">Report created at $DATE</div>
  </body>
</html>
EOF
    # Make sure the file is on disk:
    sync
}

if [ ! -e $THUMBDRIVE_DEVICE ]; then
    echo "eBlocker reset functions unavailable: $THUMBDRIVE_DEVICE does not exist."
    exit 0
fi

if findmnt -rno SOURCE $THUMBDRIVE_DEVICE; then
    echo "eBlocker reset functions unavailable: $THUMBDRIVE_DEVICE is already mounted."
    exit 0
fi

if mount $THUMBDRIVE_DEVICE $MOUNT_DIR; then
    if [ -e $EBLOCKER_RESET_FILE ]; then
        perform_factory_reset
    elif [ -e $EBLOCKER_UPDATERECOVERY_FILE ]; then
        perform_update_recovery
    else
        perform_status_report
    fi
fi
