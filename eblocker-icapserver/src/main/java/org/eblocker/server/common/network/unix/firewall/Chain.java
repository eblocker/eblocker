package org.eblocker.server.common.network.unix.firewall;

import java.util.ArrayList;
import java.util.List;

public class Chain {
    private String name;
    private String policy = "-";
    private List<String> rules = new ArrayList<>();

    public Chain(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String getPolicy() {
        return policy;
    }

    public Chain accept() {
        policy = "ACCEPT";
        return this;
    }

    public List<String> getRules() {
        return rules;
    }

    public Chain rule(String rule) {
        rules.add(rule);
        return this;
    }

    public Chain rule(String rule, Object... options) {
        rules.add(String.format(rule, options));
        return this;
    }
}
