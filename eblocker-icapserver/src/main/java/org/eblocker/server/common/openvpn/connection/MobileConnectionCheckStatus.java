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
package org.eblocker.server.common.openvpn.connection;

public class MobileConnectionCheckStatus {

    public enum State { NOT_STARTED, PENDING_REQUESTS, PENDING_RESULTS, SUCCESS, FAILURE, TIMEOUT_REQUESTS, TIMEOUT_RESULTS, CANCELED, ERROR }

    private State state;
    private int tcpMessagesSent;
    private int tcpMessagesReceived;
    private int udpMessagesSent;
    private int udpMessagesReceived;

    public MobileConnectionCheckStatus(State state,
                                       int tcpMessagesSent, int tcpMessagesReceived,
                                       int udpMessagesSent, int udpMessagesReceived) {
        this.state = state;
        this.udpMessagesSent = udpMessagesSent;
        this.udpMessagesReceived = udpMessagesReceived;
        this.tcpMessagesSent = tcpMessagesSent;
        this.tcpMessagesReceived = tcpMessagesReceived;
    }

    public State getState() {
        return state;
    }

    public int getTcpMessagesReceived() {
        return tcpMessagesReceived;
    }

    public int getTcpMessagesSent() {
        return tcpMessagesSent;
    }

    public int getUdpMessagesReceived() {
        return udpMessagesReceived;
    }

    public int getUdpMessagesSent() {
        return udpMessagesSent;
    }
}
