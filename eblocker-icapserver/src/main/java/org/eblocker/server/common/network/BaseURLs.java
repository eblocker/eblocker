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
package org.eblocker.server.common.network;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.IpAddress;

/**
 * Since the HTTP server listens on HTTP and HTTPS ports, we have
 * two different base URLs. This class should simplify selecting
 * the right URL.
 */
public class BaseURLs {
    private static final int DEFAULT_HTTP_PORT = 80;
    private static final int DEFAULT_HTTPS_PORT = 443;

    private int httpPort;
    private int httpsPort;
    private final String setupEblockerUrl;
    private final String controlBarHostName;
    private final NetworkInterfaceWrapper networkInterface;

    @Inject
    public BaseURLs(
        NetworkInterfaceWrapper networkInterface,
        @Named("httpPort") int httpPort,
        @Named("httpsPort") int httpsPort,
        @Named("url.setup.eblocker") String setupEblockerUrl,
        @Named("network.control.bar.host.name") String controlBarHostName) {
        this.networkInterface = networkInterface;
        this.httpPort = httpPort;
        this.httpsPort = httpsPort;
        this.setupEblockerUrl = setupEblockerUrl;
        this.controlBarHostName = controlBarHostName;
    }

    public String getHttpURL() {
        return getUrlByScheme("http", networkInterface.getFirstIPv4Address().toString());
    }

    public String getHttpsURL() {
        return getUrlByScheme("https", networkInterface.getFirstIPv4Address().toString());
    }

    public String selectURLForPage(String scheme) {
        return getUrlByScheme(scheme, controlBarHostName);
    }

    public String selectIpForPage(Boolean isVpnConnection, String scheme) {
        IpAddress ip = isVpnConnection ? networkInterface.getVpnIpv4Address() : networkInterface.getFirstIPv4Address();
        return getUrlByScheme(scheme, ip.toString());
    }

    /**
     * Returns true if the given URL matches any base URL.
     * NOTE: this method does not handle default ports 80 and 443 well.
     *
     * @param url
     * @return true if the given URL matches
     */
    public boolean matchesAny(String url) {
        return url.startsWith(getHttpURL()) || url.startsWith(getHttpsURL());
    }

    public boolean isSetupUrl(String url) {
        return url.equals(setupEblockerUrl);
    }

    private String getUrlByScheme(String scheme, String host) {
        if (scheme.startsWith("https")) {
            return getUrl("https", host, httpsPort, DEFAULT_HTTPS_PORT);
        } else {
            return getUrl("http", host, httpPort, DEFAULT_HTTP_PORT);
        }
    }

    private String getUrl(String scheme, String host, int port, int defaultPort) {
        StringBuilder sb = new StringBuilder();
        sb.append(scheme).append("://").append(host);
        if (port != defaultPort) {
            sb.append(':').append(port);
        }
        return sb.toString();
    }
}
