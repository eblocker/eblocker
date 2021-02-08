package org.eblocker.server.common.network.unix.firewall;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Chain {
    private String name;
    private String policy = "-";
    private List<Rule> rules = new ArrayList<>();

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

    public Chain rule(Rule rule) {
        rules.add(rule);
        return this;
    }

    public List<Rule> getRules() {
        return rules;
    }

    public List<String> getRulesAsStrings() {
        return rules.stream().map(Rule::toString).collect(Collectors.toList());
    }
}
