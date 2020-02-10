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

public class RecordedSSLAppData {
    private static final int LOGLINE_IP_POSITION = 0;
    private static final int LOGLINE_IPV6_POSITION = 1;
    private static final int LOGLINE_TCPSTREAMNUMBER_POSITION = 2;

    private final String ip;
    private final int tcpStreamNumber;

    public RecordedSSLAppData(String ip, int tcpStreamNumber) {
        this.ip = ip;
        this.tcpStreamNumber = tcpStreamNumber;
    }

    public static RecordedSSLAppData parse(String logLine) {

        // split input line into fields
        String[] splitted = logLine.split("\\t");
        /*
         * Fields are: 1. Destination IP 2. Destination IPv6 3. TCP Stream
         * Number
         */

        // assert line contains enough data
        if (splitted.length < 3) {
            return null;
        }

        // store data in attributes
        String ip;
        if (!"".equals(splitted[LOGLINE_IP_POSITION])) {
            ip = splitted[0];
        } else {
            ip = splitted[LOGLINE_IPV6_POSITION];
        }
        int tcpStreamNumber = Integer.parseInt(splitted[LOGLINE_TCPSTREAMNUMBER_POSITION]);
        return new RecordedSSLAppData(ip, tcpStreamNumber);
    }

    public String getIp() {
        return this.ip;
    }

    public int getTCPStreamNumber() {
        return this.tcpStreamNumber;
    }
}
