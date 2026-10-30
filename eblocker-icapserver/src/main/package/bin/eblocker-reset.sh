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
    console_log "*** PERFORMING FACTORY RESET ***"
    mv "$EBLOCKER_RESET_FILE" "$EBLOCKER_RESET_LOG"
    date >> $EBLOCKER_RESET_LOG
    echo "Starting eBlocker factory reset." >> $EBLOCKER_RESET_LOG

    /opt/eblocker-icap/scripts/factory_reset
}

perform_update_recovery() {
    console_log "*** PERFORMING UPDATE RECOVERY ***"
    mv "$EBLOCKER_UPDATERECOVERY_FILE" "$EBLOCKER_UPDATERECOVERY_LOG"
    date >> $EBLOCKER_UPDATERECOVERY_LOG

    dpkg --force-confold --force-confdef --configure -a &>> $EBLOCKER_UPDATERECOVERY_LOG
    RETVAL=$?

    if [ $RETVAL -eq 0 ]
    then
        echo "Update recovery - Finished without error." >> $EBLOCKER_UPDATERECOVERY_LOG
    else
        echo "Update recovery - Finished with error ($RETVAL)." >> $EBLOCKER_UPDATERECOVERY_LOG
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

echo "eBlocker waiting for up to 3 seconds for USB drives"
if ! /opt/eblocker-icap/scripts/mountpoint_wait $MOUNT_DIR 3000; then
    echo "eBlocker reset functions unavailable: $MOUNT_DIR is not mounted."
    exit 0
fi
echo "eBlocker waiting for USB drives: done"

if [ -e $EBLOCKER_BACKUP_FILE ]; then
    echo "Backup file found. Skipping other eblocker-reset functions."
elif [ -e $EBLOCKER_RESET_FILE ]; then
	  perform_factory_reset
elif [ -e $EBLOCKER_UPDATERECOVERY_FILE ]; then
    perform_update_recovery
else
    perform_status_report
fi
