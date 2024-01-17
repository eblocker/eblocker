package org.eblocker.server.common.session;

import org.eblocker.server.common.data.IpAddress;
import org.junit.Assert;
import org.junit.Test;

public class SessionIdUtilTest {

    @Test
    public void getSessionId() {
        String deviceId = "device:abcdef012345";
        String userAgent = "SomeBrowser/1.0";
        Integer userId = 42;
        String sessionId = SessionIdUtil.getSessionId(deviceId, userAgent, userId);
        Assert.assertEquals(256/8*2, sessionId.length());
        Assert.assertEquals(sessionId, SessionIdUtil.getSessionId(deviceId, userAgent, userId));
        Assert.assertNotEquals(sessionId, SessionIdUtil.getSessionId("device:cafecafecafe", userAgent, userId));
        Assert.assertNotEquals(sessionId, SessionIdUtil.getSessionId(deviceId, "OtherBrowser/1.0", userId));
        Assert.assertNotEquals(sessionId, SessionIdUtil.getSessionId(deviceId, userAgent, 43));
    }

    @Test
    public void normalizeUserAgent() {
        Assert.assertNotNull(SessionIdUtil.normalizeUserAgent(null));
        String userAgent = "SomeBrowser/1.0";
        Assert.assertEquals(userAgent, SessionIdUtil.normalizeUserAgent(userAgent));
    }

    @Test
    public void normalizeIp() {
        IpAddress clientIp = IpAddress.parse("192.168.0.11");
        IpAddress eblockerIp = IpAddress.parse("192.168.0.42");
        IpAddress localhostIp4 = IpAddress.parse("127.0.0.1");
        IpAddress localhostIp6 = IpAddress.parse("::1");
        IpAddress localhostLinkLocalIp6 = IpAddress.parse("fe80::1");
        Assert.assertEquals(clientIp, SessionIdUtil.normalizeIp(clientIp, eblockerIp));
        Assert.assertEquals(localhostIp4, SessionIdUtil.normalizeIp(eblockerIp, eblockerIp));
        Assert.assertEquals(localhostIp4, SessionIdUtil.normalizeIp(localhostIp6, eblockerIp));
        Assert.assertEquals(localhostIp4, SessionIdUtil.normalizeIp(localhostLinkLocalIp6, eblockerIp));
    }
}