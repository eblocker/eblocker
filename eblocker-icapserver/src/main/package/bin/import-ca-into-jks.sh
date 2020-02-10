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

JKS=/opt/eblocker-icap/keys/ssl/eblocker.jks
CERT=/opt/eblocker-icap/keys/ssl/eblocker.cert
KEY=/opt/eblocker-icap/keys/ssl/eblocker.key
TMP=/tmp/eblocker.p12
PASSWORD=dgtb86eQ1GIKVmN9FaDC


# ensure key and certificate is writable by icapserver
chown icapd:icapd $KEY
chown icapd:icapd $CERT

if [ ! -f $JKS -a -f $CERT -a -f $KEY ]; then
  # import previous ca
  openssl pkcs12 -export -in $CERT -inkey $KEY -out /tmp/eblocker.p12 -name root -password pass:$PASSWORD
  keytool -importkeystore -deststorepass $PASSWORD -destkeypass $PASSWORD -destkeystore $JKS -srckeystore $TMP -srcstoretype PKCS12 -alias root -srcstorepass $PASSWORD

  chown icapd:icapd $JKS
fi
