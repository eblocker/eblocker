package org.eblocker.server.common.network.unix.firewall;

import org.eblocker.server.common.util.IpUtils;

/**
 * Simulates passing a TCP or UDP packet through a chain.
 *
 * Note: Not all rule features are implemented, yet.
 */
public class Simulator {
    private final Chain chain;
    private String input, output;

    public Simulator(Chain chain) {
        this.chain = chain;
    }

    public Action tcpPacket(String sourceIp, String destinationIp, int destinationPort) {
        return packet(Rule.Protocol.TCP, sourceIp, destinationIp, destinationPort);
    }

    public Action udpPacket(String sourceIp, String destinationIp, int destinationPort) {
        return packet(Rule.Protocol.UDP, sourceIp, destinationIp, destinationPort);
    }

    private Action packet(Rule.Protocol protocol, String sourceIp, String destinationIp, int destinationPort) {
        return chain.getRules().stream()
                .filter(rule -> matches(rule, protocol, sourceIp, destinationIp, destinationPort))
                .findFirst()
                .map(Rule::getAction)
                .orElse(Action.returnFromChain()); // packet passes through
    }

    private boolean matches(Rule rule, Rule.Protocol protocol, String sourceIp, String destinationIp, int destinationPort) {
        if (!matchInterface(rule.getInput(), input)) {
            return false;
        }
        if (!matchInterface(rule.getOutput(), output)) {
            return false;
        }
        if (rule.getProtocol() != null && rule.getProtocol() != protocol) {
            return false;
        }
        if (!matchIp(rule.getSourceIp(), sourceIp)) {
            return false;
        }
        if (!matchIp(rule.getDestinationIp(), destinationIp)) {
            return false;
        }
        if (!matchPort(rule.getDestinationPort(), destinationPort)) {
            return false;
        }
        return true;
    }

    private boolean matchIp(String ruleIp, String packetIp) {
        if (ruleIp == null) { // all IPs allowed
            return true;
        }
        if (IpUtils.isIpRange(ruleIp)) {
            return isInIpRange(ruleIp, packetIp);
        }
        return packetIp.equals(ruleIp);
    }

    private boolean matchPort(Integer rulePort, int packetPort) {
        if (rulePort == null) { // all ports allowed
            return true;
        }
        return rulePort == packetPort;
    }

    private boolean isInIpRange(String ipRange, String ip) {
        int[] network = IpUtils.convertIpRangeToIpNetmask(ipRange);
        return IpUtils.isInSubnet(IpUtils.convertIpStringToInt(ip), network[0], network[1]);
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
}
