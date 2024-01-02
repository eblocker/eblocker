package org.eblocker.server.icap.service;

public class SurrogateRule {
    public String regexRule;
    public String surrogate;

    public String action;

    public SurrogateRule() {

    }

    public SurrogateRule(String regexRule, String surrogate) {
        this.regexRule = regexRule;
        this.surrogate = surrogate;
    }
}
