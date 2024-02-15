/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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