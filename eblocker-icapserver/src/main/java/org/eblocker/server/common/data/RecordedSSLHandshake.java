/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package org.eblocker.server.common.data;

public class RecordedSSLHandshake {
    private static final int LOGLINE_IP_POSITION = 0;
    private static final int LOGLINE_IPV6_POSITION = 1;
    private static final int LOGLINE_SERVERNAME_POSITION = 2;
    private static final int LOGLINE_TCPSTREAMNUMBER_POSITION = 3;
    private String ip;
    private String servername;
    private int tcpStreamNumber;
    private boolean correspondingAppDataRecorded = false;

    public RecordedSSLHandshake() {
    }

    public static RecordedSSLHandshake parse(String logLine) {
        RecordedSSLHandshake recordedSSLHandshake = new RecordedSSLHandshake();
        // split input line into fields
        String[] splitted = logLine.split("\\t");
        /*
         * Fields are: 1. Destination IP 2. Destination IPv6 3. Server Name 4.
         * TCP Stream Number
         */

        // assert line contains enough data
        if (splitted.length < 4) {
            return null;
        }

        // store data in attributes
        if (!splitted[LOGLINE_IP_POSITION].equals("")) {
            recordedSSLHandshake.ip = splitted[0];
        } else {
            recordedSSLHandshake.ip = splitted[LOGLINE_IPV6_POSITION];
        }
        recordedSSLHandshake.servername = splitted[LOGLINE_SERVERNAME_POSITION];
        recordedSSLHandshake.tcpStreamNumber = Integer.valueOf(splitted[LOGLINE_TCPSTREAMNUMBER_POSITION]);
        return recordedSSLHandshake;
    }

    public void updateWithAppData(RecordedSSLAppData appData) {
        if (this.tcpStreamNumber != appData.getTCPStreamNumber() || (!this.ip.equals(appData.getIp()))) {
            return;
        }
        this.correspondingAppDataRecorded = true;
    }

    public String getIP() {
        return this.ip;
    }

    public String getServername() {
        return this.servername;
    }

    public int getTCPStreamNumber() {
        return this.tcpStreamNumber;
    }

    public boolean isCorrespondingAppDataRecorded() {
        return this.correspondingAppDataRecorded;
    }
}
