package org.eblocker.server.http.service;

import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
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
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AutoTrustAppServiceTest {

    private AutoTrustAppService autoTrustAppService;
    private final int autoTrustAppModuleId = 9997;

    @Mock
    private SquidWarningService squidWarningService;

    @Mock
    private AppModuleService appModuleService;

    @Mock
    private DomainBlockingService domainBlockingService;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        autoTrustAppService = new AutoTrustAppService(squidWarningService, appModuleService, domainBlockingService);
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

        autoTrustAppService.onChange(newArrayList(failedConnection(Instant.now(), "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
            autoTrustAppModuleId);
    }

    @Test
    public void testNewDomainIsNotAddedWithSameLastOccurence() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        Instant lastOccurrence = Instant.now();
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

        autoTrustAppService.onChange(newArrayList(failedConnection(Instant.now().minusSeconds(10), "foo.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(Instant.now(), "foo.com")));

        verify(appModuleService).addDomainsToModule(whitelistUrls("foo.com"),
            autoTrustAppModuleId);
    }

    @Test
    public void testNoReAddOfKnownDomain() {
        AppWhitelistModule sslCollectingAppModule = collectingModule("foo.com");
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(Instant.now().minusSeconds(10), "foo.com")));
        autoTrustAppService.onChange(Collections.singletonList(failedConnection(Instant.now(), "foo.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("foo.com"),
            autoTrustAppModuleId);
    }

    @Test
    public void testBlockedDomainNotAdded() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("bad.com")).thenReturn(blocked());

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(Instant.now().minusSeconds(10), "bad.com")));
        autoTrustAppService.onChange(Collections.singletonList(failedConnection(Instant.now(), "bad.com")));

        verify(appModuleService, never()).addDomainsToModule(whitelistUrls("bar"), autoTrustAppModuleId);
    }

    @Test
    public void testMultipleDomains() {
        AppWhitelistModule sslCollectingAppModule = collectingModule();
        when(appModuleService.getAutoSslAppModule()).thenReturn(sslCollectingAppModule);

        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("foo.com")).thenReturn(notBlocked());
        when(domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters("bar.com")).thenReturn(notBlocked());

        autoTrustAppService.onChange(Collections.singletonList(failedConnection(Instant.now().minusSeconds(10), "foo.com", "bar.com")));
        autoTrustAppService.onChange(newArrayList(failedConnection(Instant.now(), "foo.com", "bar.com")));

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
        return domainBlockingService.new Decision(false, null, 0, 0, 0, null);
    }

    private DomainBlockingService.Decision blocked() {
        return domainBlockingService.new Decision(true, null, 0, 0, 0, null);
    }

    private AppWhitelistModule collectingModule(String... whiteListedDomains) {
        return new AppWhitelistModule(autoTrustAppModuleId, null, null,
            newArrayList(whiteListedDomains), null, null, null, null, null, null, null,
            null, null, null);
    }

    private FailedConnection failedConnection(Instant lastOccurrence, String... domains) {
        return new FailedConnection(newArrayList("device:000000000000"), newArrayList(domains),
            newArrayList("error:0"), lastOccurrence);
    }
}
