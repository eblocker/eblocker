package org.eblocker.server.common.network.unix.firewall;

import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Rule {
    public static final int HTTP_PORT = 80;
    public static final int HTTPS_PORT = 443;
    public static final int SSH_PORT = 22;
    public static final int DNS_PORT = 53;

    private Action action;

    private String inputInterface, outputInterface;
    private String sourceIp, destinationIp;
    private Integer destinationPort;
    private Protocol protocol;

    private MatchSet matchSet;
    private States states;
    private MultiPorts multiPorts;
    private OwnerModule ownerModule;
    private String comment;

    private static Pattern allowedCommentCharacters = Pattern.compile("[ a-zA-Z0-9]+");

    /**
     * Copy constructor
     * @param template
     */
    public Rule(Rule template) {
            action = template.action;
            inputInterface = template.inputInterface;
            outputInterface = template.outputInterface;
            sourceIp = template.sourceIp;
            destinationIp = template.destinationIp;
            destinationPort = template.destinationPort;
            protocol = template.protocol;
            matchSet = template.matchSet;
            states = template.states;
            multiPorts = template.multiPorts;
            ownerModule = template.ownerModule;
            comment = template.comment;
    }

    public Rule() {
    }

    public Rule input(String inputInterface) {
        this.inputInterface = inputInterface;
        return this;
    }

    public Rule output(String outputInterface) {
        this.outputInterface = outputInterface;
        return this;
    }

    public Rule sourceIp(String ip) {
        this.sourceIp = ip;
        return this;
    }

    public Rule destinationIp(String ip) {
        this.destinationIp = ip;
        return this;
    }

    public Rule tcp() {
        this.protocol = Protocol.TCP;
        return this;
    }

    public Rule udp() {
        this.protocol = Protocol.UDP;
        return this;
    }

    public Rule http() {
        return tcp().destinationPort(HTTP_PORT);
    }

    public Rule https() {
        return tcp().destinationPort(HTTPS_PORT);
    }

    public Rule http3() {
        return udp().destinationPort(HTTPS_PORT);
    }

    public Rule dns() {
        return udp().destinationPort(DNS_PORT);
    }

    public Rule destinationPort(int port) {
        this.destinationPort = port;
        return this;
    }

    public Rule destinationPorts(boolean match, Integer... ports) {
        this.multiPorts = new MultiPorts(match, MultiPortsType.DESTINATION, ports);
        return this;
    }

    public Rule matchSet(boolean match, String name, String... flags) {
        this.matchSet = new MatchSet(match, name, flags);
        return this;
    }

    public Rule states(boolean match, State... states) {
        this.states = new States(match, states);
        return this;
    }

    public Rule ownerUid(boolean match, int uid) {
        this.ownerModule = new OwnerModule(match, uid);
        return this;
    }

    public Rule redirectTo(String targetIp, int targetPort) {
        this.action = Action.redirectTo(targetIp, targetPort);
        return this;
    }

    public Rule mark(int value) {
        this.action = Action.mark(value);
        return this;
    }

    public Rule drop() {
        this.action = Action.drop();
        return this;
    }

    public Rule accept() {
        this.action = Action.accept();
        return this;
    }

    public Rule reject() {
        this.action = Action.reject();
        return this;
    }

    public Rule returnFromChain() {
        this.action = Action.returnFromChain();
        return this;
    }

    public Rule masquerade() {
        this.action = Action.masquerade();
        return this;
    }

    public Rule jumpToChain(String chainName) {
        this.action = Action.jumpToChain(chainName);
        return this;
    }

    public Rule comment(String comment) {
        if (! allowedCommentCharacters.matcher(comment).matches()) {
            throw new IllegalArgumentException("Illegal characters in firewall rule comment");
        }
        this.comment = comment;
        return this;
    }

    public Action getAction() {
        return action;
    }

    public String getSourceIp() {
        return sourceIp;
    }

    public String getDestinationIp() {
        return destinationIp;
    }

    public Integer getDestinationPort() {
        return destinationPort;
    }

    public Protocol getProtocol() {
        return protocol;
    }

    public String getInput() {
        return inputInterface;
    }

    public String getOutput() {
        return outputInterface;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        if (inputInterface != null) {
            appendOption(result, "-i", inputInterface);
        }
        if (outputInterface != null) {
            appendOption(result, "-o", outputInterface);
        }
        if (sourceIp != null) {
            appendOption(result, "-s", sourceIp);
        }
        if (destinationIp != null) {
            appendOption(result, "-d", destinationIp);
        }
        if (protocol != null) {
            appendOption(result, "-p", protocol.getLabel());
        }
        if (destinationPort != null) {
            if (protocol == null) {
                throw new IllegalArgumentException("Option destinationPort needs option protocol to be set");
            }
            appendOption(result, "-m", protocol.getLabel());
            appendOption(result, "--dport", Integer.toString(destinationPort));
        }
        if (matchSet != null) {
            ensureSpace(result);
            result.append(matchSet.toString());
        }
        if (states != null) {
            ensureSpace(result);
            result.append(states.toString());
        }
        if (multiPorts != null) {
            ensureSpace(result);
            result.append(multiPorts.toString());
        }
        if (ownerModule != null) {
            ensureSpace(result);
            result.append(ownerModule.toString());
        }
        if (action == null) {
            throw new IllegalArgumentException("A firewall rule needs an action");
        }
        ensureSpace(result);
        result.append(action.toString());

        if (comment != null) {
            result.append(" -m comment --comment ");
            result.append('"');
            result.append(comment);
            result.append('"');
        }
        return result.toString();
    }

    private void appendOption(StringBuilder sb, String option, String argument) {
        ensureSpace(sb);
        sb.append(option);
        sb.append(" ");
        sb.append(argument);
    }

    private void ensureSpace(StringBuilder sb) {
        if (sb.length() != 0) {
            sb.append(" ");
        }
    }

    public enum Protocol {
        TCP("tcp"),
        UDP("udp");

        private final String label;

        Protocol(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum State {
        INVALID, ESTABLISHED, NEW, RELATED, UNTRACKED;
    }

    public class States {
        private boolean match;
        private State[] states;

        public States(boolean match, State... states) {
            this.match = match;
            this.states = states;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("-m state ");
            if (!match) {
                result.append("! ");
            }
            result.append("--state ");
            result.append(Arrays.stream(states).map(State::name).collect(Collectors.joining(",")));
            return result.toString();
        }
    }

    public class MatchSet {
        private String name;
        private boolean match;
        private String[] flags;

        public MatchSet(boolean match, String name, String... flags) {
            this.match = match;
            this.name = name;
            this.flags = flags;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("-m set ");
            if (!match) {
                result.append("! ");
            }
            result.append("--match-set ");
            result.append(name);
            result.append(" ");
            result.append(Arrays.stream(flags).collect(Collectors.joining(",")));
            return result.toString();
        }
    }

    public enum MultiPortsType {
        SOURCE("sports"), DESTINATION("dports"), ANY("ports");

        private final String label;

        MultiPortsType(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public class MultiPorts {
        private MultiPortsType type;
        private boolean match;
        private Integer[] ports;

        public MultiPorts(boolean match, MultiPortsType type, Integer[] ports) {
            this.type = type;
            this.match = match;
            this.ports = ports;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("-m multiport ");
            if (!match) {
                result.append("! ");
            }
            result.append("--");
            result.append(type.getLabel());
            result.append(" ");
            result.append(Arrays.stream(ports).map(p -> p.toString()).collect(Collectors.joining(",")));
            return result.toString();
        }
    }

    public class OwnerModule {
        private boolean match;
        private int uid;

        public OwnerModule(boolean match, int uid) {
            this.match = match;
            this.uid = uid;
        }

        public String toString() {
            StringBuilder result = new StringBuilder("-m owner ");
            if (!match) {
                result.append("! ");
            }
            result.append("--uid-owner ");
            result.append(uid);
            return result.toString();
        }
    }
}
