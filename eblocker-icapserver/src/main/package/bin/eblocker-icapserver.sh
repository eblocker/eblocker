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

if [ "$SYSTEM_MEMORY_IN_MB" -gt 2048 ]; then
    MAX_JVM_HEAP_SIZE_IN_MB=$((SYSTEM_MEMORY_IN_MB / 2))
    MAX_DIRECT_MEMORY_IN_MB=200
    NUM_ARENAS=8
elif [ "$SYSTEM_MEMORY_IN_MB" -gt 1500 ]; then
    MAX_JVM_HEAP_SIZE_IN_MB=768
    MAX_DIRECT_MEMORY_IN_MB=200
    NUM_ARENAS=8
else
    MAX_JVM_HEAP_SIZE_IN_MB=384
    MAX_DIRECT_MEMORY_IN_MB=100
    NUM_ARENAS=4
fi

MEMORY_SETTINGS="\
-Xmx${MAX_JVM_HEAP_SIZE_IN_MB}m \
-XX:MaxDirectMemorySize=${MAX_DIRECT_MEMORY_IN_MB}m \
-Dio.netty.allocator.numDirectArenas=${NUM_ARENAS} \
-Dio.netty.allocator.numHeapArenas=${NUM_ARENAS}"

# Run the ICAP server:
exec java $MEMORY_SETTINGS -Dlog4j2.configurationFile=$LOG4JCONF -jar $BASEDIR/lib/${project.build.finalName}.jar
