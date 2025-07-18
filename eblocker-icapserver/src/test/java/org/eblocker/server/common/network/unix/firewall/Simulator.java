/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network.unix.firewall;

import org.eblocker.server.common.data.Ip6Address;
import org.eblocker.server.common.util.Ip4Utils;
import org.eblocker.server.common.util.Ip6Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Simulates passing a TCP or UDP packet through a chain.
 * The chain can contain sub-chains.
 *
 * Note: Not all rule features are implemented, yet.
 */
public class Simulator {
    private final Chain chain;
    private String input, output;
    private List<Chain> subChains = new ArrayList();

    public Simulator(Chain chain) {
        this.chain = chain;
    }

    public void addSubChain(Chain subChain) {
        subChains.add(subChain);
    }

    public Action tcpPacket(String sourceIp, String destinationIp, int destinationPort) {
        return tcpPacket(sourceIp, destinationIp, destinationPort, Rule.State.NEW);
    }

    public Action udpPacket(String sourceIp, String destinationIp, int destinationPort) {
        return udpPacket(sourceIp, destinationIp, destinationPort, Rule.State.NEW);
    }

    public Action tcpPacket(String sourceIp, String destinationIp, int destinationPort, Rule.State state) {
        return tcpPacket(sourceIp, destinationIp, destinationPort, state, 0);
    }

    public Action udpPacket(String sourceIp, String destinationIp, int destinationPort, Rule.State state) {
        return udpPacket(sourceIp, destinationIp, destinationPort, state, 0);
    }

    public Action tcpPacket(String sourceIp, String destinationIp, int destinationPort, Rule.State state, int mark) {
        return getAction(new Packet(Protocol.TCP, sourceIp, destinationIp, destinationPort, state, mark));
    }

    public Action udpPacket(String sourceIp, String destinationIp, int destinationPort, Rule.State state, int mark) {
        return getAction(new Packet(Protocol.UDP, sourceIp, destinationIp, destinationPort, state, mark));
    }

    private Action getAction(Packet packet) {
        return streamOfActionsMatching(chain, packet)
                .findFirst()
                .orElse(Action.returnFromChain()); // packet passes through
    }

    private Stream<Action> includeSubChainActions(Action action, Packet packet) {
        return subChains.stream()
                .filter(subChain -> action.equals(Action.jumpToChain(subChain.getName())))
                .findFirst()
                .map(subChain -> streamOfActionsMatching(subChain, packet))
                .orElse(Stream.of(action)); // action was not to jump into another chain, so return the action itself
    }

    private Stream<Action> streamOfActionsMatching(Chain chain, Packet packet) {
        return chain.getRules().stream()
                .filter(rule -> matches(rule, packet))
                .map(Rule::getAction)
                .flatMap(action -> includeSubChainActions(action, packet));
    }

    private boolean matches(Rule rule, Packet packet) {
        if (!matchInterface(rule.getInput(), input)) {
            return false;
        }
        if (!matchInterface(rule.getOutput(), output)) {
            return false;
        }
        if (rule.getProtocol() != null && rule.getProtocol() != packet.protocol) {
            return false;
        }
        if (!matchIp(rule.getSourceIp(), packet.sourceIp)) {
            return false;
        }
        if (!matchIp(rule.getDestinationIp(), packet.destinationIp)) {
            return false;
        }
        if (!matchPort(rule.getDestinationPort(), packet.destinationPort)) {
            return false;
        }
        if (!matchState(rule.getStates(), packet.state)) {
            return false;
        }
        if (!matchMark(rule.getMark(), packet.mark)) {
            return false;
        }
        return true;
    }

    private boolean matchMark(int ruleMark, int packetMark) {
        if (ruleMark == 0) { // all marks allowed
            return true;
        }
        return ruleMark == packetMark;
    }

    private boolean matchState(Rule.States ruleStates, Rule.State packetState) {
        if (ruleStates == null) { // all states allowed
            return true;
        }
        return ruleStates.match(packetState);
    }

    private boolean matchIp(String ruleIp, String packetIp) {
        if (ruleIp == null) { // all IPs allowed
            return true;
        }
        if (Ip4Utils.isIpRange(ruleIp)) {
            return isInIp4Range(ruleIp, packetIp);
        }
        if (Ip6Utils.isIp6Range(ruleIp)) {
            return isInIp6Range(ruleIp, packetIp);
        }
        return packetIp.equals(ruleIp);
    }

    private boolean matchPort(Integer rulePort, int packetPort) {
        if (rulePort == null) { // all ports allowed
            return true;
        }
        return rulePort == packetPort;
    }

    private boolean isInIp4Range(String ipRange, String ip) {
        int[] network = Ip4Utils.convertIpRangeToIpNetmask(ipRange);
        return Ip4Utils.isInSubnet(Ip4Utils.convertIpStringToInt(ip), network[0], network[1]);
    }

    private boolean isInIp6Range(String ipRange, String ip) {
        String[] parts = ipRange.split("/");
        return Ip6Utils.isInNetwork(Ip6Address.parse(ip), Ip6Address.parse(parts[0]), Integer.parseInt(parts[1]));
    }

    private boolean matchInterface(String ruleInterface, String packetInterface) {
        if (ruleInterface == null) { // all interfaces allowed
            return true;
        }
        return ruleInterface.equals(packetInterface);
    }

    public void setInput(String input) {
        this.input = input;
    }

    public void setOutput(String output) {
        this.output = output;
    }

    // source port not supported yet
    private class Packet {
        Protocol protocol;
        String sourceIp;
        String destinationIp;
        int destinationPort;
        Rule.State state;
        int mark;

        private Packet(Protocol protocol, String sourceIp, String destinationIp, int destinationPort, Rule.State state, int mark) {
            this.protocol = protocol;
            this.sourceIp = sourceIp;
            this.destinationIp = destinationIp;
            this.destinationPort = destinationPort;
            this.state = state;
            this.mark = mark;
        }
    }
}
