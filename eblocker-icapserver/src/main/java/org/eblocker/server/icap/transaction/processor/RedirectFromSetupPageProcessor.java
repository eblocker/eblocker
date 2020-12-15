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
package org.eblocker.server.icap.transaction.processor;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.registration.RegistrationState;
import org.eblocker.server.http.service.DnsService;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.core.UriBuilder;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

@Singleton
public class RedirectFromSetupPageProcessor implements TransactionProcessor {
    private static final Logger log = LoggerFactory.getLogger(RedirectFromSetupPageProcessor.class);

    private DeviceRegistrationProperties deviceRegistrationProperties;
    private String setupUrlPrefix;
    private Set<String> setupUrlPaths;
    private String dashboardUrl;
    private final String connectionCheckRoutingTestUrl;
    private DnsService dnsService;
    private NetworkServices networkServices;

    @Inject
    public RedirectFromSetupPageProcessor(@Named("my.eblocker.localredirect.setup.url-prefix") String setupUrlPrefix,
                                          @Named("my.eblocker.localredirect.setup.path") String setupUrlPaths,
                                          @Named("network.dashboard.host") String dashboardHost,
                                          @Named("connection.test.routingtest.url") String connectionCheckRoutingTestUrl,
                                          DeviceRegistrationProperties deviceRegistrationProperties,
                                          DnsService dnsService,
                                          NetworkServices networkServices) {
        this.dashboardUrl = buildUrl(dashboardHost, "");
        this.connectionCheckRoutingTestUrl = connectionCheckRoutingTestUrl;
        this.deviceRegistrationProperties = deviceRegistrationProperties;
        this.dnsService = dnsService;
        this.setupUrlPaths = new HashSet<>(Arrays.asList(setupUrlPaths.split("\\s")));
        this.setupUrlPrefix = setupUrlPrefix;
        this.networkServices = networkServices;
    }

    private String getRedirectUrlPath(String url) {
        if (url.startsWith(setupUrlPrefix)) {
            URI uri;

            try {
                uri = new URI(url);
            } catch (URISyntaxException e) {
                return null;
            }

            for (String path : setupUrlPaths) {
                if (path.equals(uri.getPath())) {
                    UriBuilder toUrl = UriBuilder.fromUri("");
                    toUrl.host(networkServices.getCurrentNetworkConfiguration().getIpAddress());
                    toUrl.scheme("http");
                    String lang = uri.getPath().split("/")[1];

                    return toUrl.build().toString() + "/setup/#!/" + lang;
                }
            }
        }
        return null;
    }

    @Override
    public boolean process(Transaction transaction) {
        String url = transaction.getUrl();
        String redirectUrl = getRedirectUrlPath(url);

        if (transaction.isRequest() && url.endsWith(connectionCheckRoutingTestUrl)) {
            transaction.noContent();
            return false;
        }

        if (redirectUrl == null) {
            return true;
        }

        if (deviceRegistrationProperties.getRegistrationState() == RegistrationState.NEW) {
            transaction.redirect(redirectUrl);
        } else {
            if (dnsService.isEnabled()) {
                transaction.redirect(dashboardUrl);
            } else {
                transaction.redirect(buildUrl(networkServices.getCurrentNetworkConfiguration().getIpAddress(), "dashboard"));
            }
        }

        return false;
    }

    private String buildUrl(String host, String path) {
        try {
            UriBuilder url = UriBuilder.fromUri("");
            url.host(host);
            url.scheme("http");
            url.path(path);
            return url.toString();
        } catch (Exception e) {
            log.error("Could not build URL from host {}", host, e);
        }
        return null;
    }
}
