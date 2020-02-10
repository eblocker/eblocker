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

PCAP_FILE=/tmp/recording.pcap
LOG_FILE=/tmp/processing.log

HANDSHAKEFILE=/tmp/recording_ssl_handshakes
APPDATAFILE=/tmp/recording_ssl_data

tshark -r $PCAP_FILE \
    -R "ssl.handshake" \
    -2 \
    -T fields \
    -e ip.dst \
    -e ipv6.dst \
    -e ssl.handshake.extensions_server_name \
    -e tcp.stream > $HANDSHAKEFILE 2>$LOG_FILE

tshark -r $PCAP_FILE \
    -R "ssl.app_data" \
    -2 \
    -T fields \
    -e ip.dst \
    -e ipv6.dst \
    -e tcp.stream > $APPDATAFILE 2>>$LOG_FILE
