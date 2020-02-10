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

BASEDIR=/opt/eblocker-network/certificate-validator/
LOG4JCONF=file://$BASEDIR/bin/certificate-validator-log4j.properties

#exec java -Dcom.sun.management.jmxremote -Dcom.sun.management.jmxremote.port=9000 -Dcom.sun.management.jmxremote.local.only=false -Dcom.sun.management.jmxremote.authenticate=false -Dcom.sun.management.jmxremote.ssl=false -Xmx192m -Dlog4j.configuration=$LOG4JCONF -jar $BASEDIR/lib/${project.build.finalName}.jar true
exec java -Xmx192m -Dlog4j.configuration=$LOG4JCONF -jar $BASEDIR/lib/${project.build.finalName}.jar true
