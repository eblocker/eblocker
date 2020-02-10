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

import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.AccessRestriction;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.parentalcontrol.Category;
import org.eblocker.server.common.data.parentalcontrol.ParentalControlFilterMetaData;
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.common.transaction.TransactionContext;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.resources.OnePixelImage;
import org.eblocker.server.common.network.BaseURLs;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponse;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.stream.Collectors;

@ChannelHandler.Sharable
@Singleton
public class AccessDeniedRequestHandler extends SimpleChannelInboundHandler<HttpRequest> {
    private static final Logger log = LoggerFactory.getLogger(AccessDeniedRequestHandler.class);

    static final AttributeKey<String> SCHEME_KEY = AttributeKey.newInstance("SCHEME");

    private final BaseURLs baseURLs;
    private final DeviceService deviceService;
    private final DomainBlockingService domainBlockingService;
    private final MalwareFilterService malwareFilterService;
    private final ParentalControlFilterListsService listsService;
    private final SessionStore sessionStore;
    private final UserService userService;

    private final String parentalControlRedirectPage;
    private final ParentalControlAccessRestrictionsService restrictionsService;
    private final String adsTrackerRedirectPage;
    private final String malwareRedirectPage;
    private final String whitelistedRedirectPage;

    @Inject
    public AccessDeniedRequestHandler(BaseURLs baseURLs,
                                      DeviceService deviceService,
                                      DomainBlockingService domainBlockingService,
                                      MalwareFilterService malwareFilterService,
                                      ParentalControlFilterListsService listsService,
                                      ParentalControlAccessRestrictionsService restrictionsService,
                                      SessionStore sessionStore,
                                      UserService userService,
                                      @Named("parentalControl.redirectPage") String parentalControlRedirectPage,
                                      @Named("adsTracker.redirectPage") String adsTrackerRedirectPage,
                                      @Named("malware.redirectPage") String malwareRedirectPage,
                                      @Named("whitelisted.redirectPage") String whitelistedRedirectPage) {
        this.baseURLs = baseURLs;
        this.deviceService = deviceService;
        this.domainBlockingService = domainBlockingService;
        this.malwareFilterService = malwareFilterService;
        this.listsService = listsService;
        this.sessionStore = sessionStore;
        this.userService = userService;

        this.parentalControlRedirectPage = parentalControlRedirectPage;
        this.restrictionsService = restrictionsService;
        this.adsTrackerRedirectPage = adsTrackerRedirectPage;
        this.malwareRedirectPage = malwareRedirectPage;
        this.whitelistedRedirectPage = whitelistedRedirectPage;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpRequest request) {
        if (!request.decoderResult().isSuccess()) {
            Throwable cause = request.decoderResult().cause();
            log.info("Decoding http request failed", cause);
            ctx.close();
            return;
        }

        IpAddress remoteAddress = getRemoteAddress(ctx);
        Device device = deviceService.getDeviceByIp(remoteAddress);
        if (device == null) {
            return;
        }

        String url = getUrl(ctx.channel(), request);
        HttpResponse response;
        if (!restrictionsService.isAccessPermitted(device)) {
            response = handleAccessRestrictions(device, url, request);
        } else {
            response = handleBlockedDomain(device, remoteAddress, url, request);
        }
        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }

    private HttpResponse handleAccessRestrictions(Device device, String url, HttpRequest request) {
        String baseUrl = baseURLs.selectURLForPage(url);
        String userId = String.valueOf(device.getOperatingUser());
        UserModule user = userService.getUserById(device.getOperatingUser());
        String profileId = user != null && user.getAssociatedProfileId() != null ? String.valueOf(user.getAssociatedProfileId()) : "";
        String location = baseUrl
            + parentalControlRedirectPage
            + "?target=" + UrlUtils.urlEncode(url)
            + "&restrictions=" + UrlUtils.urlEncode(getRestrictions(device))
            + "&profileId=" + profileId
            + "&userId=" + userId;
        return redirectSvg(location, request);
    }

    private String getRestrictions(Device device) {
        return restrictionsService.getAccessRestrictions(device).stream()
            .map(AccessRestriction::toString)
            .collect(Collectors.joining(","));
    }

    private HttpResponse handleBlockedDomain(Device device, IpAddress remoteAddress, String url, HttpRequest request) {
        DomainBlockingService.Decision decision = domainBlockingService.isBlocked(device, request.headers().get("Host"));
        if (decision.isBlocked()) {
            Category category = getCategory(decision.getListId());
            if (category == Category.ADS || category == Category.TRACKERS || category == Category.CUSTOM || category == Category.MALWARE) {
                incrementBlockCounter(category, request, remoteAddress);
            }
            String location = getBlockedDomainPageLocation(category, decision, url);
            return redirectSvg(location, request);
        } else {
            String location = getWhitelistedDomainPageLocation(url);
            return redirect(location);
        }
    }

    private IpAddress getRemoteAddress(ChannelHandlerContext ctx) {
        InetSocketAddress remoteAddress = (InetSocketAddress) ctx.channel().remoteAddress();
        return IpAddress.of(remoteAddress.getAddress());
    }

    private Category getCategory(Integer listId) {
        if (listId == null) {
            return null;
        }

        ParentalControlFilterMetaData metaData = listsService.getParentalControlFilterMetaData(listId);
        if (metaData == null) {
            return null;
        }

        return metaData.getCategory();
    }

    private String getBlockedDomainPageLocation(Category category, DomainBlockingService.Decision decision, String url) {
        switch (category) {
            case ADS:
            case CUSTOM:
            case TRACKERS:
                return getAdsTrackerLocation(category, decision, url);
            case MALWARE:
                List<String> entries = malwareFilterService.getMalwareByUrl(url);
                return getMalwareLocation(decision, entries, url);
            default:
                return getParentalControlLocation(decision, url);
        }
    }

    private String getUrl(Channel channel, HttpRequest request) {
        String scheme = channel.attr(SCHEME_KEY).get();
        return scheme + "://" + request.headers().get("Host") + request.uri();
    }

    private String getAdsTrackerLocation(Category category, DomainBlockingService.Decision decision, String url) {
        return baseURLs.selectURLForPage(url)
            + adsTrackerRedirectPage
            + "?target=" + UrlUtils.urlEncode(url)
            + "&category=" + category
            + "&domain=" + decision.getDomain();
    }

    public String getMalwareLocation(DomainBlockingService.Decision decision, List<String> malware, String url) {
        return baseURLs.selectURLForPage(url)
            + malwareRedirectPage
            + "?target=" + UrlUtils.urlEncode(url)
            + "&domain=" + decision.getDomain()
            + "&malware=" + UrlUtils.urlEncode(String.join(",", malware));
    }

    private String getParentalControlLocation(DomainBlockingService.Decision decision, String url) {
        return baseURLs.selectURLForPage(url)
            + parentalControlRedirectPage
            + "?target=" + UrlUtils.urlEncode(url)
            + "&listId=" + decision.getListId()
            + "&domain=" + decision.getDomain()
            + "&profileId=" + decision.getProfileId()
            + "&userId=" + decision.getUserId();
    }

    private String getWhitelistedDomainPageLocation(String url) {
        return baseURLs.selectURLForPage(url)
            + whitelistedRedirectPage
            + "?target=" + UrlUtils.urlEncode(url);
    }

    private FullHttpResponse redirectSvg(String target, HttpRequest request) {
        byte[] content = OnePixelImage.get(target);
        FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, Unpooled.wrappedBuffer(content));
        response.headers()
            .set("Access-Control-Allow-Origin", selectAccessControlAllowOrigin(request))
            .set("Content-Length", content.length)
            .set("Content-Type", "image/svg+xml");
        return response;
    }

    private String selectAccessControlAllowOrigin(HttpRequest request) {
        String header = request.headers().get("Origin");
        return Strings.isNullOrEmpty(header) ? "*" : header;
    }

    private void incrementBlockCounter(Category category, HttpRequest request, IpAddress remoteAddress) {
        TransactionIdentifier id = new HttpTransactionIdentifier(request, remoteAddress);
        Session session = sessionStore.getSession(id);
        TransactionContext context = new HttpTransactionContext(session, request);

        switch (category) {
            case ADS:
                session.incrementBlockedAds(context);
                break;
            case TRACKERS:
                session.incrementBlockedTrackings(context);
                break;
            default:
                log.debug("can not increment ads / trackers for category {}", category);
        }
    }

    private HttpResponse redirect(String location) {
        HttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.SEE_OTHER);
        response.headers().set("Location", location);
        return response;
    }
}
