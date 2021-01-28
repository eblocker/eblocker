package org.eblocker.server.common.network.unix.firewall;

import org.junit.Assert;
import org.junit.Test;

public class RuleTest {
    @Test
    public void testDestinationNat() {
        Rule rule = new Rule()
                .input("eth0")
                .sourceIp("192.168.0.106")
                .tcp()
                .destinationPort(80)
                .redirectTo("192.168.0.2", 3128);

        Assert.assertEquals("-i eth0 -s 192.168.0.106 -p tcp -m tcp --dport 80 -j DNAT --to-destination 192.168.0.2:3128", rule.toString());
    }

    @Test
    public void testMark() {
        Rule rule = new Rule()
                .sourceIp("192.168.0.22")
                .mark(1);
        Assert.assertEquals("-s 192.168.0.22 -j MARK --set-mark 1", rule.toString());
    }

    @Test
    public void testStates() {
        Rule rule = new Rule().states(false, Rule.State.INVALID, Rule.State.UNTRACKED).drop();
        Assert.assertEquals("-m state ! --state INVALID,UNTRACKED -j DROP", rule.toString());
    }

    @Test
    public void testMultiPorts() {
        Rule rule = new Rule().tcp().destinationPorts(true, 22, 80, 443).accept();
        Assert.assertEquals("-p tcp -m multiport --dports 22,80,443 -j ACCEPT", rule.toString());
    }

    @Test
    public void testOwnerUid() {
        Rule rule = new Rule().ownerUid(false, 23).reject();
        Assert.assertEquals("-m owner ! --uid-owner 23 -j REJECT", rule.toString());
    }

    @Test
    public void testShortcuts() {
        Assert.assertEquals("-p tcp -m tcp --dport 80 -j DROP", new Rule().http().drop().toString());
        Assert.assertEquals("-p tcp -m tcp --dport 443 -j DROP", new Rule().https().drop().toString());
        Assert.assertEquals("-p udp -m udp --dport 53 -j DROP", new Rule().dns().drop().toString());
    }

    @Test
    public void testTemplate() {
        Rule template = new Rule().tcp().returnFromChain();

        Rule rule1 = new Rule(template).input("eth0").destinationIp("192.168.1.2");
        Rule rule2 = new Rule(template).input("eth33").destinationIp("10.8.0.1");
        Rule rule3 = new Rule(template).drop();

        Assert.assertEquals("-i eth0 -d 192.168.1.2 -p tcp -j RETURN", rule1.toString());
        Assert.assertEquals("-i eth33 -d 10.8.0.1 -p tcp -j RETURN", rule2.toString());
        Assert.assertEquals("-p tcp -j DROP", rule3.toString());
    }

    @Test
    public void testComment() {
        Assert.assertEquals("-j DROP -m comment --comment \"Just a comment\"", new Rule().comment("Just a comment").drop().toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testIllegalComment() {
        new Rule().comment("hehe\"\n-j RETURN");
    }
}
