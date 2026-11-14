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
#

BASEDIR=/opt/eblocker-icap
LOG4JCONF=file://$BASEDIR/conf/icapserver-log4j2.xml

SYSTEM_MEMORY_IN_MB=$(free -m | grep 'Mem:'  | awk '{print $2}')

MAX_JVM_HEAP_SIZE_IN_MB=500
MAX_DIRECT_MEMORY_IN_MB=200
NUM_ARENAS=8

# On large RAM systems, give the ICAP server an additional quarter of the RAM that exceeds 2 GB:
if [ "$SYSTEM_MEMORY_IN_MB" -gt 2500 ]; then
    MAX_JVM_HEAP_SIZE_IN_MB="$(( (SYSTEM_MEMORY_IN_MB - 2000) / 4 + 500))"
fi

# Keep child process creation working when a JDK update replaces jspawnhelper.
MEMORY_SETTINGS="\
-XX:+ExitOnOutOfMemoryError \
-Djdk.lang.Process.launchMechanism=vfork \
-Xmx${MAX_JVM_HEAP_SIZE_IN_MB}m \
-XX:MaxDirectMemorySize=${MAX_DIRECT_MEMORY_IN_MB}m \
-Dio.netty.allocator.numDirectArenas=${NUM_ARENAS} \
-Dio.netty.allocator.numHeapArenas=${NUM_ARENAS}"

# Run the ICAP server:
exec java $MEMORY_SETTINGS -Dlog4j2.configurationFile=$LOG4JCONF -jar $BASEDIR/lib/${project.build.finalName}.jar
