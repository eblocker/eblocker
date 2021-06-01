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
package org.eblocker.server.http.service;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.DecoderResult;
import io.netty.handler.codec.TooLongFrameException;
import io.netty.handler.codec.http.DefaultFullHttpRequest;
import io.netty.handler.codec.http.DefaultHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.AccessRestriction;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class AccessDeniedRequestHandlerTest {

    private static final Pattern SVG_REDIRECT_PATTERN = Pattern.compile("document.location = '([^']*)'");
    private static final String USER_AGENT = "Mozilla/5.0 Unit-Test/1.14";

    private static final int PROFILE_ID_PARENTAL = 5;
    private static final int PROFILE_ID_BLOCKING = 1;

    private static final int LIST_ID_PARENTAL = 23;
    private static final int LIST_ID_ADS = 1;
    private static final int LIST_ID_TRACKERS = 2;
    private static final int LIST_ID_MALWARE = 3;
    private static final int LIST_ID_CUSTOM_BLACKLIST = 1111;

    private static final String PARENTAL_CONTROL_REDIRECT_PAGE = "/access-denied.html";
    private static final String ADS_TRACKERS_REDIRECT_PAGE = "/blocked-ads-trackers.html";
    private static final String MALWARE_REDIRECT_PAGE = "/blocked-malware.html";
    private static final String WHITELISTED_REDIRECT_PAGE = "/blocked-whitelisted.html";

    private DomainBlockingService domainBlockingService;
    private DeviceService deviceService;
    private BaseURLs baseURLs;
    private AccessDeniedRequestHandler handler;
    private MalwareFilterService malwareFilterService;
    private ParentalControlFilterListsService listsService;
    private ParentalControlAccessRestrictionsService restrictionsService;
    private SessionStore sessionStore;
    private UserService userService;

    private Device device;
    private Session session;
    private UserModule user;

    @Before
    public void setup() {
        baseURLs = Mockito.mock(BaseURLs.class);

        Mockito.when(baseURLs.selectURLForPage(Mockito.anyString())).then(im -> ((String) im.getArgument(0)).startsWith("https") ? "https://dashboard.eblocker" : "http://dashboard.eblocker");

        device = createMockDevice("192.168.9.15");
        deviceService = Mockito.mock(DeviceService.class);
        Mockito.when(deviceService.getDeviceByIp(IpAddress.parse("192.168.9.15"))).thenReturn(device);

        domainBlockingService = Mockito.mock(DomainBlockingService.class);

        malwareFilterService = Mockito.mock(MalwareFilterService.class);
        Mockito.when(malwareFilterService.getMalwareByUrl("https://random.blocked.page/abc/def?id=abc")).thenReturn(Arrays.asList("malware A", "malware B"));

        listsService = Mockito.mock(ParentalControlFilterListsService.class);
        Mockito.when(listsService.getParentalControlFilterMetaData(LIST_ID_PARENTAL)).thenReturn(createMockMetaData(LIST_ID_PARENTAL, Category.PARENTAL_CONTROL));
        Mockito.when(listsService.getParentalControlFilterMetaData(LIST_ID_ADS)).thenReturn(createMockMetaData(LIST_ID_ADS, Category.ADS));
        Mockito.when(listsService.getParentalControlFilterMetaData(LIST_ID_TRACKERS)).thenReturn(createMockMetaData(LIST_ID_TRACKERS, Category.TRACKERS));
        Mockito.when(listsService.getParentalControlFilterMetaData(LIST_ID_MALWARE)).thenReturn(createMockMetaData(LIST_ID_MALWARE, Category.MALWARE));
        Mockito.when(listsService.getParentalControlFilterMetaData(LIST_ID_CUSTOM_BLACKLIST)).thenReturn(createMockMetaData(LIST_ID_CUSTOM_BLACKLIST, Category.CUSTOM));

        restrictionsService = Mockito.mock(ParentalControlAccessRestrictionsService.class);
        Mockito.when(restrictionsService.isAccessPermitted(Mockito.any())).thenReturn(true);

        session = Mockito.mock(Session.class);
        Mockito.when(session.getSessionId()).thenReturn("mock-session");
        sessionStore = Mockito.mock(SessionStore.class);
        Mockito.when(sessionStore.getSession(Mockito.any(TransactionIdentifier.class))).thenReturn(session);

        user = new UserModule(5, 5, null, null, null, null, false, null, null, null, null, null);
        userService = Mockito.mock(UserService.class);
        Mockito.when(userService.getUserById(5)).thenReturn(user);

        handler = new AccessDeniedRequestHandler(baseURLs, deviceService, domainBlockingService, malwareFilterService, listsService, restrictionsService, sessionStore, userService, PARENTAL_CONTROL_REDIRECT_PAGE, ADS_TRACKERS_REDIRECT_PAGE,
                MALWARE_REDIRECT_PAGE, WHITELISTED_REDIRECT_PAGE);
    }

    @Test
    public void testRedirectHttp() {
        setupFilterMock(PROFILE_ID_PARENTAL, true, LIST_ID_PARENTAL, 5);

        FullHttpResponse response = doRequest("http", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        assertSvgRedirect("http://dashboard.eblocker/access-denied.html?target=http%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&listId=23&domain=random.blocked.page&profileId=5&userId=5", response.content());
    }

    @Test
    public void testRedirectHttps() {
        setupFilterMock(PROFILE_ID_PARENTAL, true, LIST_ID_PARENTAL, 5);

        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        assertSvgRedirect("https://dashboard.eblocker/access-denied.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&listId=23&domain=random.blocked.page&profileId=5&userId=5", response.content());
    }

    @Test
    public void testNotBlocked() {
        setupFilterMock(PROFILE_ID_BLOCKING, false, LIST_ID_ADS, 5);

        HttpResponse response = doRequest("https", "random.whitelisted.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.SEE_OTHER, response.status());
        Assert.assertEquals("https://dashboard.eblocker/blocked-whitelisted.html?target=https%3A%2F%2Frandom.whitelisted.page%2Fabc%2Fdef%3Fid%3Dabc", response.headers().get("Location"));
    }

    @Test
    public void testBlockingAds() {
        setupFilterMock(PROFILE_ID_BLOCKING, true, LIST_ID_ADS, 5);

        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        Assert.assertEquals("*", response.headers().get("Access-Control-Allow-Origin"));
        assertSvgRedirect("https://dashboard.eblocker/blocked-ads-trackers.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&category=ADS&domain=random.blocked.page", response.content());

        ArgumentCaptor<TransactionIdentifier> idCaptor = ArgumentCaptor.forClass(TransactionIdentifier.class);
        Mockito.verify(sessionStore).getSession(idCaptor.capture());
        Assert.assertEquals(USER_AGENT, idCaptor.getValue().getUserAgent());
        Assert.assertEquals(IpAddress.parse("192.168.9.15"), idCaptor.getValue().getOriginalClientIP());

        ArgumentCaptor<TransactionContext> contextCaptor = ArgumentCaptor.forClass(TransactionContext.class);
        Mockito.verify(session).incrementBlockedAds(contextCaptor.capture());
        Assert.assertEquals("mock-session", contextCaptor.getValue().getSessionId());
        Assert.assertEquals("http://random.site", contextCaptor.getValue().getReferrer());
    }

    @Test
    public void testBlockingTrackers() {
        setupFilterMock(PROFILE_ID_BLOCKING, true, LIST_ID_TRACKERS, 5);

        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        Assert.assertEquals("*", response.headers().get("Access-Control-Allow-Origin"));
        assertSvgRedirect("https://dashboard.eblocker/blocked-ads-trackers.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&category=TRACKERS&domain=random.blocked.page", response.content());

        ArgumentCaptor<TransactionIdentifier> idCaptor = ArgumentCaptor.forClass(TransactionIdentifier.class);
        Mockito.verify(sessionStore).getSession(idCaptor.capture());
        Assert.assertEquals(USER_AGENT, idCaptor.getValue().getUserAgent());
        Assert.assertEquals(IpAddress.parse("192.168.9.15"), idCaptor.getValue().getOriginalClientIP());

        ArgumentCaptor<TransactionContext> contextCaptor = ArgumentCaptor.forClass(TransactionContext.class);
        Mockito.verify(session).incrementBlockedTrackings(contextCaptor.capture());
        Assert.assertEquals("mock-session", contextCaptor.getValue().getSessionId());
        Assert.assertEquals("http://random.site", contextCaptor.getValue().getReferrer());
    }

    @Test
    public void testBlockingMalware() {
        setupFilterMock(PROFILE_ID_BLOCKING, true, LIST_ID_MALWARE, 5);

        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        Assert.assertEquals("*", response.headers().get("Access-Control-Allow-Origin"));
        assertSvgRedirect("https://dashboard.eblocker/blocked-malware.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&domain=random.blocked.page&malware=malware+A%2Cmalware+B", response.content());

        ArgumentCaptor<TransactionIdentifier> idCaptor = ArgumentCaptor.forClass(TransactionIdentifier.class);
        Mockito.verify(sessionStore).getSession(idCaptor.capture());
        Assert.assertEquals(USER_AGENT, idCaptor.getValue().getUserAgent());
        Assert.assertEquals(IpAddress.parse("192.168.9.15"), idCaptor.getValue().getOriginalClientIP());

        Mockito.verify(session, Mockito.never()).incrementBlockedTrackings(Mockito.any(TransactionContext.class));
    }

    @Test
    public void testBlockingCustomBlacklist() {
        setupFilterMock(PROFILE_ID_BLOCKING, true, LIST_ID_CUSTOM_BLACKLIST, 5);

        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", null);

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        Assert.assertEquals("*", response.headers().get("Access-Control-Allow-Origin"));
        assertSvgRedirect("https://dashboard.eblocker/blocked-ads-trackers.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&category=CUSTOM&domain=random.blocked.page", response.content());

        ArgumentCaptor<TransactionIdentifier> idCaptor = ArgumentCaptor.forClass(TransactionIdentifier.class);
        Mockito.verify(sessionStore).getSession(idCaptor.capture());
        Assert.assertEquals(USER_AGENT, idCaptor.getValue().getUserAgent());
        Assert.assertEquals(IpAddress.parse("192.168.9.15"), idCaptor.getValue().getOriginalClientIP());

        Mockito.verify(session, Mockito.never()).incrementBlockedTrackings(Mockito.any(TransactionContext.class));
    }

    @Test
    public void testBlockingAdsCrossOrigin() {
        setupFilterMock(PROFILE_ID_BLOCKING, true, LIST_ID_ADS, 5);

        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", "https://www.eblocker.com");

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        Assert.assertEquals("https://www.eblocker.com", response.headers().get("Access-Control-Allow-Origin"));
        assertSvgRedirect("https://dashboard.eblocker/blocked-ads-trackers.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&category=ADS&domain=random.blocked.page", response.content());

        ArgumentCaptor<TransactionIdentifier> idCaptor = ArgumentCaptor.forClass(TransactionIdentifier.class);
        Mockito.verify(sessionStore).getSession(idCaptor.capture());
        Assert.assertEquals(USER_AGENT, idCaptor.getValue().getUserAgent());
        Assert.assertEquals(IpAddress.parse("192.168.9.15"), idCaptor.getValue().getOriginalClientIP());

        ArgumentCaptor<TransactionContext> contextCaptor = ArgumentCaptor.forClass(TransactionContext.class);
        Mockito.verify(session).incrementBlockedAds(contextCaptor.capture());
        Assert.assertEquals("mock-session", contextCaptor.getValue().getSessionId());
        Assert.assertEquals("http://random.site", contextCaptor.getValue().getReferrer());
    }

    @Test
    public void testBlockingTwoWhitelists() {
        // list ID might be null, for example if more than one whitelist is configured
        setupFilterMock(PROFILE_ID_PARENTAL, true, null, 5);
        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.15", "https://www.eblocker.com");

        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        assertSvgRedirect("https://dashboard.eblocker/access-denied.html?target=https%3A%2F%2Frandom.blocked.page%2Fabc%2Fdef%3Fid%3Dabc&listId=null&domain=random.blocked.page&profileId=5&userId=5", response.content());
    }

    @Test
    public void testUnknownDevice() {
        FullHttpResponse response = doRequest("https", "random.blocked.page", "/abc/def?id=abc", "192.168.9.9", "https://www.eblocker.com");
        Assert.assertNull(response);
    }

    @Test
    public void testAccessRestrictedDevice() {
        Mockito.when(restrictionsService.isAccessPermitted(device)).thenReturn(false);
        Mockito.when(restrictionsService.getAccessRestrictions(device)).thenReturn(Arrays.asList(AccessRestriction.USAGE_TIME_DISABLED, AccessRestriction.TIME_FRAME, AccessRestriction.MAX_USAGE_TIME));

        FullHttpResponse response = doRequest("https", "random.page", "/abc/def?id=abc", "192.168.9.15", null);
        Assert.assertEquals(HttpResponseStatus.OK, response.status());
        assertSvgRedirect("https://dashboard.eblocker/access-denied.html?target=https%3A%2F%2Frandom.page%2Fabc%2Fdef%3Fid%3Dabc&restrictions=USAGE_TIME_DISABLED%2CTIME_FRAME%2CMAX_USAGE_TIME&profileId=5&userId=5", response.content());
    }

    @Test
    public void testDecoderError() {
        HttpRequest request = new DefaultFullHttpRequest(HttpVersion.HTTP_1_0, HttpMethod.GET, "/bad-request", true);
        request.setDecoderResult(DecoderResult.failure(new TooLongFrameException()));

        EmbeddedChannel embeddedChannel = createEmbeddedChannel("http", "192.168.4.5");
        embeddedChannel.writeInbound(request);
        embeddedChannel.checkException();
        Assert.assertNull(embeddedChannel.readInbound());
    }

    private FullHttpResponse doRequest(String scheme, String host, String uri, String remoteAddr, String origin) {
        DefaultHttpRequest request = new DefaultHttpRequest(HttpVersion.HTTP_1_1, HttpMethod.GET, uri);
        request.headers()
                .set("Host", host)
                .set("Referer", "http://random.site")
                .set("User-Agent", USER_AGENT);
        if (origin != null) {
            request.headers().set("Origin", origin);
        }

        EmbeddedChannel embeddedChannel = createEmbeddedChannel(scheme, remoteAddr);
        embeddedChannel.writeInbound(request);
        embeddedChannel.checkException();
        return embeddedChannel.readOutbound();
    }

    private EmbeddedChannel createEmbeddedChannel(String scheme, String remoteAddr) {
        AddAttributeHandler addAttributeHandler = new AddAttributeHandler(Collections.singletonMap(
                AccessDeniedRequestHandler.SCHEME_KEY, scheme));
        EmbeddedChannel embeddedChannel = new EmbeddedChannel(addAttributeHandler, handler) {
            @Override
            protected SocketAddress remoteAddress0() {
                return new InetSocketAddress(remoteAddr, 12345);
            }
        };
        return embeddedChannel;
    }

    private ParentalControlFilterMetaData createMockMetaData(int id, Category category) {
        ParentalControlFilterMetaData metaData = new ParentalControlFilterMetaData(id, null, null, category, null, null, null, null, null, false, false, null, null, null);
        return metaData;
    }

    private Device createMockDevice(String ipAddress) {
        Device device = new Device();
        device.setIpAddresses(Collections.singletonList(IpAddress.parse(ipAddress)));
        device.setOperatingUser(5);
        return device;
    }

    private void setupFilterMock(int profileId, boolean blocked, Integer listId, int userId) {
        Mockito.when(domainBlockingService.isBlocked(Mockito.any(Device.class), Mockito.anyString())).then(im -> domainBlockingService.new Decision(blocked, im.getArgument(1), profileId, listId, userId, null));
    }

    private void assertSvgRedirect(String expectedTarget, ByteBuf byteBuf) {
        String target = extractRedirectUrl(byteBuf);
        Assert.assertEquals(expectedTarget, target);
    }

    private String extractRedirectUrl(ByteBuf byteBuf) {
        String response = byteBuf.toString(StandardCharsets.UTF_8);
        Matcher matcher = SVG_REDIRECT_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}
