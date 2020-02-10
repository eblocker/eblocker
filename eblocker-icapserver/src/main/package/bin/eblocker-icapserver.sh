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

BASEDIR=/opt/eblocker-icap
LOG4JCONF=file://$BASEDIR/conf/icapserver-log4j.properties
ARGS=""

SELF_CHECK=0

# Load defaults for eblocker-reset script (defines $EBLOCKER_SELFCHECK_FILE)
EBLOCKER_RESET_CONFIG=/etc/default/eblocker-reset
if [ -r $EBLOCKER_RESET_CONFIG ]; then
    . $EBLOCKER_RESET_CONFIG

    if [ -e "$EBLOCKER_SELFCHECK_FILE" ]; then
	SELF_CHECK=1
    fi
fi


if [ $SELF_CHECK -eq 1 ]; then
    # Run the self-check:
    exec java -cp $BASEDIR/lib/${project.build.finalName}.jar org.eblocker.server.app.SelfCheckApp
else
    # Run the ICAP server:
    exec java -Xmx384m -Dlog4j.configuration=$LOG4JCONF -jar $BASEDIR/lib/${project.build.finalName}.jar
fi
