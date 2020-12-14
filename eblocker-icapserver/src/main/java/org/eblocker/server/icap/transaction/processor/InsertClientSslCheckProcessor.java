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
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.RequireFeature;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.ssl.SslCertificateClientInstallationTracker;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.util.StringReplacer;
import org.eblocker.server.common.util.UrlUtils;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.server.icap.transaction.Transaction;
import org.eblocker.server.icap.transaction.TransactionProcessor;

@RequireFeature(ProductFeature.PRO)
@Singleton
public class InsertClientSslCheckProcessor implements TransactionProcessor {

    private static final String TAG_TEST_CURRENT = "@TEST_CURRENT@";
    private static final String TAG_TEST_RENEWAL = "@TEST_RENEWAL@";
    private static final String TAG_URL_CURRENT_CA = "@URL_CURRENT_CA@";
    private static final String TAG_URL_RENEWAL_CA = "@URL_RENEWAL_CA@";
    private static final String TAG_URL_REPORT_ERROR_CURRENT = "@URL_REPORT_ERROR_CURRENT@";
    private static final String TAG_URL_REPORT_ERROR_RENEWAL = "@URL_REPORT_ERROR_RENEWAL@";

    private final BaseURLs baseURLs;
    private final NetworkInterfaceWrapper networkInterface;
    private final SslService sslService;
    private final SslCertificateClientInstallationTracker tracker;
    private final String scriptTemplate;
    private final int currentPort;
    private final int renewalPort;

    @Inject
    public InsertClientSslCheckProcessor(@Named("ssl.test.client.script.template") String scriptTemplate,
                                         @Named("ssl.test.ca.current.endpoint.port") int currentPort,
                                         @Named("ssl.test.ca.renewal.endpoint.port") int renewalPort,
                                         BaseURLs baseURLs,
                                         NetworkInterfaceWrapper networkInterface,
                                         SslService sslService,
                                         SslCertificateClientInstallationTracker tracker) {
        this.currentPort = currentPort;
        this.renewalPort = renewalPort;
        this.baseURLs = baseURLs;
        this.networkInterface = networkInterface;
        this.sslService = sslService;
        this.tracker = tracker;
        String template = ResourceHandler.load(new SimpleResource(scriptTemplate));
        this.scriptTemplate = "<script id=\"eblocker-ssl-script\" type=\"text/javascript\">" + template + "</script>";
    }

    @Override
    public boolean process(Transaction transaction) {
        if (!isHTML(transaction)) {
            return true;
        }

        Session session = transaction.getSession();

        if (session.getUserAgentInfo().isMsie()) {
            // Do not insert SSL test script into MSIE browser pages, because MSIE opens error dialog box
            return true;
        }
        if (!session.getUserAgentInfo().isBrowser()) {
            // If the client is no standard browser, do not insert the SSL test
            return true;
        }

        if (!sslService.isCaAvailable() && !sslService.isRenewalCaAvailable()) {
            return true;
        }

        if (isCaCheckNecessary(session) || isRenewalCaCheckNecessary(session)) {
            transaction.getInjections().inject(generateScript(getUrl(transaction), session.getDeviceId()));
        }

        return true;
    }

    private String getUrl(Transaction transaction) {
        return baseURLs.selectURLForPage(transaction.getBaseUrl());
    }

    // TODO: same method as in InsertToolBarProcessor...
    private boolean isHTML(Transaction transaction) {
        String contentType = transaction.getContentType();
        return contentType != null && (contentType.contains("text/html") || contentType.contains("text/xhtml"));
    }

    private String generateScript(String baseUrl, String deviceId) {
        String ipAddress = networkInterface.getFirstIPv4Address().toString();
        String urlEncodedDeviceId = UrlUtils.urlEncode(deviceId);

        String url = "https://%s:%d/%s";
        StringReplacer replacer = new StringReplacer()
            .add(TAG_TEST_CURRENT, Boolean.toString(sslService.isCaAvailable()))
            .add(TAG_TEST_RENEWAL, Boolean.toString(sslService.isRenewalCaAvailable()))
            .add(TAG_URL_CURRENT_CA, String.format(url, ipAddress, currentPort, urlEncodedDeviceId))
            .add(TAG_URL_RENEWAL_CA, String.format(url, ipAddress, renewalPort, urlEncodedDeviceId));
        if (sslService.isCaAvailable()) {
            replacer.add(TAG_URL_REPORT_ERROR_CURRENT, baseUrl + "/ssl/test/" + sslService.getCa().getCertificate().getSerialNumber());
        }
        if (sslService.isRenewalCaAvailable()) {
            replacer.add(TAG_URL_REPORT_ERROR_RENEWAL, baseUrl + "/ssl/test/" + sslService.getRenewalCa().getCertificate().getSerialNumber());
        }
        return replacer.replace(scriptTemplate);
    }

    private boolean isCaCheckNecessary(Session session) {
        return sslService.isCaAvailable() && tracker.isCaCertificateInstalled(session.getDeviceId(), session.getUserAgent()) != SslCertificateClientInstallationTracker.Status.INSTALLED;
    }

    private boolean isRenewalCaCheckNecessary(Session session) {
        return sslService.isRenewalCaAvailable() && tracker.isFutureCaCertificateInstalled(session.getDeviceId(), session.getUserAgent()) != SslCertificateClientInstallationTracker.Status.INSTALLED;
    }
}
