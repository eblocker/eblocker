/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.http.service.AutoTrustAppService.KnownError;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static java.time.Instant.now;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutoTrustAppServiceTest {

    private static final String DEVICE_ID = "device:000000000000";
    private AutoTrustAppService autoTrustAppService;
    private final int autoTrustAppModuleId = 9997;

    @Mock
    private SquidWarningService squidWarningService;

    @Mock
    private AppModuleService appModuleService;

    @Mock
    private DomainBlockingService domainBlockingService;

    @Mock
    private DeviceService deviceService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        Device d = new Device();
        d.setEnabled(true);
        d.setSslEnabled(true);
        when(deviceService.getDeviceById(DEVICE_ID)).thenReturn(d);
        autoTrustAppService = new AutoTrustAppService(squidWarningService, appModuleService, domainBlockingService, deviceService);
    }

    @Test
    public void testNothingHappensForEmptyFailedConnections() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        autoTrustAppService.onChange(Collections.emptyList());

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls(), autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainIsNotAddedTheFirstTime() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("bar.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(failedConnection(now(), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(now(), "bar.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("bar.com"), autoTrustAppModuleId);
    }

    @Test
    public void testUnrelatedErrorIsNotAdded() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.DOMAIN_MISMATCH_ERROR, "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"), autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainWithCertificateErrorAddedTheFirstTime() {
        AppWhitelistModule sslCollectingAppModule = collectingModule("alreadyexisting.com");
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("cert.error.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("bad.com")).thenReturn(blocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("alreadyexisting.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("too.old.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.CERT_UNTRUSTED, "cert.error.com")));
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.CERT_UNTRUSTED, "bad.com")));
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.CERT_UNTRUSTED, "alreadyexisting.com")));
        Instant tooOldFromNow = now().minus(AutoTrustAppService.TOO_OLD).minusSeconds(5);
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(tooOldFromNow, KnownError.CERT_UNTRUSTED, "too.old.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("cert.error.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("bad.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("alreadyexisting.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("too.old.com"), autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainStartingWithAPIAddedTheFirstTime() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("api.somewhere.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("api.bad.com")).thenReturn(blocked());

        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "api.somewhere.com")));
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "api.bad.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("api.somewhere.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("api.bad.com"), autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainStartingWithWWWAddedOnThirdOccurence() {
        AppWhitelistModule sslCollectingAppModule = collectingModule("alreadyexisting.com");
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("www.somewhere.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("www.bad.com")).thenReturn(blocked());

        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "www.somewhere.com")));
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "www.bad.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("www.somewhere.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("www.bad.com"), autoTrustAppModuleId);

        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "www.somewhere.com")));
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "www.bad.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("www.somewhere.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("www.bad.com"), autoTrustAppModuleId);

        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "www.somewhere.com")));
        autoTrustAppService.onChange(newArrayList(getFailedConnectionWithError(now(), KnownError.BROKEN_PIPE, "www.bad.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("www.somewhere.com"), autoTrustAppModuleId);
        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("www.bad.com"), autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainIsNotAddedWithSameLastOccurence() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        Instant lastOccurrence = now();
        autoTrustAppService.onChange(newArrayList(failedConnection(lastOccurrence, "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(lastOccurrence, "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainIsAddedTheSecondTime() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(failedConnection(now().minusSeconds(10), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(now(), "foo.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainIsNotAddedWhenTooOld() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        Instant tooOldFromNow = now().minus(AutoTrustAppService.TOO_OLD).minusSeconds(5);
        autoTrustAppService.onChange(newArrayList(failedConnection(tooOldFromNow.minusSeconds(10), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(tooOldFromNow, "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainIsNotAddedWithOldLastOccurence() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(failedConnection(now().minus(AutoTrustAppService.FAILED_CONECTIONS_CUT_OFF_DURATION.plusMinutes(1)), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(now(), "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testEblockerOrgIsNotAdded() {
        assertDomainIsNotAdded("eblocker.org");
    }

    private void assertDomainIsNotAdded(String domain) {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters(domain)).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(failedConnection(now().minusSeconds(10), domain)));
        autoTrustAppService.onChange(newArrayList(failedConnection(now(), domain)));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls(domain), autoTrustAppModuleId);
    }

    @Test
    public void testYoutubeIsNotAdded() {
        assertDomainIsNotAdded("youtube.com");
    }

    @Test
    public void testNewDomainIsAddedWithOldLastOccurenceAndTwoNewers() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(newArrayList(failedConnection(now().minus(AutoTrustAppService.FAILED_CONECTIONS_CUT_OFF_DURATION.plusMinutes(1)), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(now().minus(AutoTrustAppService.FAILED_CONECTIONS_CUT_OFF_DURATION.minusMinutes(3)), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(now(), "foo.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testNoReAddOfKnownDomain() {
        AppWhitelistModule sslCollectingAppModule = collectingModule("foo.com");
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now().minusSeconds(10), "foo.com")));
        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now(), "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testNoAddOfDomainWithSuccessfulSSL() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.recordSuccessfulSSL("foo.com");

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now().minusSeconds(10), "foo.com")));
        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now(), "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
                autoTrustAppModuleId);
    }

    @Test
    public void testBlockedDomainNotAdded() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("bad.com")).thenReturn(blocked());

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now().minusSeconds(10), "bad.com")));
        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now(), "bad.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("bar"), autoTrustAppModuleId);
    }

    @Test
    public void testMultipleDomains() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("bar.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(now().minusSeconds(10), "foo.com", "bar.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(now(), "foo.com", "bar.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("foo.com", "bar.com"), autoTrustAppModuleId);
    }

    @Test
    public void testOnResetDeletesApp() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        autoTrustAppService.onReset();
        assertEquals(Collections.emptyList(), sslCollectingAppModule.getWhitelistedDomains());
        verify(appModuleService).update(sslCollectingAppModule, sslCollectingAppModule.getId());
    }

    private List<SSLWhitelistUrl> whitelistUrls(String... domains) {
        return Arrays.stream(domains).map(d -> new SSLWhitelistUrl("", d)).collect(Collectors.toList());
    }

    private DomainBlockingService.Decision notBlocked() {
        return new DomainBlockingService.Decision(false, null, 0, 0, 0, null);
    }

    private DomainBlockingService.Decision blocked() {
        return new DomainBlockingService.Decision(true, null, 0, 0, 0, null);
    }

    private AppWhitelistModule collectingModule(String... whiteListedDomains) {
        return new AppWhitelistModule(autoTrustAppModuleId, null, null,
                newArrayList(whiteListedDomains), null, null, null, null, null, null, null,
                null, null, null);
    }

    private FailedConnection failedConnection(Instant lastOccurrence, String... domains) {
        return getFailedConnectionWithError(lastOccurrence, KnownError.BROKEN_PIPE, domains);
    }

    private FailedConnection getFailedConnectionWithError(Instant lastOccurrence, KnownError error, String... domains) {
        return new FailedConnection(newArrayList(DEVICE_ID), newArrayList(domains),
                newArrayList(error.getMessage()), lastOccurrence);
    }
}
