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
package org.eblocker.server.common.blacklist;

import io.netty.channel.embedded.EmbeddedChannel;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.service.DomainRecordingService;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import java.util.Collections;

public class RequestHandlerTest {

    private final String BLOCKED_DOMAIN = "www.pouet.net";

    private RequestHandler requestHandler;
    private BlockedDomainLog blockedDomainLog;
    private UserService userService;
    private DomainBlockingService domainBlockingService;
    private DeviceService deviceService;
    private FilterStatisticsService filterStatisticsService;
    private DomainRecordingService domainRecordingService;
    private EmbeddedChannel embeddedChannel;

    @Before
    public void setup() {
        blockedDomainLog = Mockito.mock(BlockedDomainLog.class);

        userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getUserById(ArgumentMatchers.anyInt())).thenAnswer(
                invocation -> new UserModule(
                        invocation.getArgument(0),
                        invocation.getArgument(0),
                        "name",
                        "nameKey",
                        null,
                        null,
                        false,
                        null,
                        Collections.emptyMap(),
                        null,
                        null,
                        null));

        Device device = createMockDevice("device:10101099", "10.10.10.99", 1);
        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDeviceByIp(IpAddress.parse("10.10.10.99"))).thenReturn(device);

        domainBlockingService = Mockito.mock(DomainBlockingService.class);
        Mockito.when(domainBlockingService.isBlocked(Mockito.any(Device.class), Mockito.anyString())).then(im -> {
            String domain = im.getArgument(1);
            return domainBlockingService.new Decision(BLOCKED_DOMAIN.equals(domain), domain, 1, 100, 1, "target");
        });

        filterStatisticsService = Mockito.mock(FilterStatisticsService.class);
        domainRecordingService = Mockito.mock(DomainRecordingService.class);

        requestHandler = new RequestHandler(blockedDomainLog, domainBlockingService, deviceService, filterStatisticsService, domainRecordingService);

        embeddedChannel = new EmbeddedChannel(requestHandler);
    }

    @Test
    public void testNonBlockedDomain() {
        String response = request("10.10.10.99 http xkcd.org -");
        Assert.assertEquals("ERR", response);
        Mockito.verifyNoInteractions(filterStatisticsService);
    }

    @Test
    public void testNonBlockedDomainSni() {
        String response = request("10.10.10.99 https - xkcd.org");
        Assert.assertEquals("ERR", response);
        Mockito.verifyNoInteractions(filterStatisticsService);
    }

    @Test
    public void testBlockedDomain() {
        String response = request("10.10.10.99 http www.pouet.net -");
        Assert.assertEquals("OK message=1,100,www.pouet.net,1,target", response);
        Mockito.verify(filterStatisticsService).countQuery("pattern", IpAddress.parse("10.10.10.99"));
        Mockito.verify(filterStatisticsService).countBlocked("pattern", IpAddress.parse("10.10.10.99"), "100");
    }

    @Test
    public void testBlockedDomainSniWithProtocol() {
        String response = request("10.10.10.99 https - www.pouet.net");
        Assert.assertEquals("OK message=1,100,www.pouet.net,1,target", response);
        Mockito.verifyNoInteractions(filterStatisticsService);
    }

    @Test
    public void testBlockedDomainSniWithoutProtocol() {
        String response = request("10.10.10.99 - - www.pouet.net");
        Assert.assertEquals("OK message=1,100,www.pouet.net,1,target", response);
        Mockito.verify(filterStatisticsService).countQuery("pattern", IpAddress.parse("10.10.10.99"));
        Mockito.verify(filterStatisticsService).countBlocked("pattern", IpAddress.parse("10.10.10.99"), "100");
    }

    @Test
    public void testMissingSniWithProtocol() {
        String response = request("10.10.10.90 - www.pouet.net -");
        Assert.assertEquals("ERR", response);
        Mockito.verifyNoInteractions(filterStatisticsService);
    }

    @Test
    public void testMissingSniWithoutProtocol() {
        String response = request("10.10.10.90 https www.pouet.net -");
        Assert.assertEquals("ERR", response);
        Mockito.verifyNoInteractions(filterStatisticsService);
    }

    @Test
    public void testDeviceWithoutProfile() {
        String response = request("10.10.10.90 http www.pouet.net -");
        Assert.assertEquals("ERR", response);
    }

    @Test
    public void testMalformedRequest() {
        String response = request("127.0.0.1");
        Assert.assertEquals("BH", response);
        Assert.assertTrue(embeddedChannel.isActive());
    }

    @Test
    public void testBlockedDomainIsLogged() {
        String response = request("10.10.10.99 http www.pouet.net -");
        Assert.assertEquals("OK message=1,100,www.pouet.net,1,target", response);
        Mockito.verify(blockedDomainLog).addEntry("device:10101099", "www.pouet.net", 100);
    }

    @Test
    public void testNonBlockedDomainIsNotLogged() {
        String response = request("10.10.10.99 http xkcd.org -");
        Assert.assertEquals("ERR", response);
        Mockito.verifyNoInteractions(blockedDomainLog);
    }

    private String request(String query) {
        embeddedChannel.writeInbound(query);
        embeddedChannel.checkException();
        String response = embeddedChannel.readOutbound();
        if (response == null) {
            return null;
        }
        Assert.assertTrue(response.endsWith("\n"));
        return response.substring(0, response.length() - 1);
    }

    private Device createMockDevice(String id, String ip, int userId) {
        Device device = new Device();
        device.setId(id);
        if (ip != null) {
            device.setIpAddresses(Collections.singletonList(IpAddress.parse(ip)));
        }
        device.setOperatingUser(userId);
        return device;
    }

}
