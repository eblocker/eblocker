package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.ssl.AppWhitelistModule;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@SubSystemService(value = SubSystem.SERVICES)
public class AutoTrustAppService implements SquidWarningService.FailedConnectionsListener {

    private final AppModuleService appModuleService;
    private final DomainBlockingService domainBlockingService;
    private final Map<String, Instant> pendingDomains = new ConcurrentHashMap<>();

    @Inject
    public AutoTrustAppService(SquidWarningService squidWarningService,
                               AppModuleService appModuleService,
                               DomainBlockingService domainBlockingService) {
        this.appModuleService = appModuleService;
        this.domainBlockingService = domainBlockingService;

        squidWarningService.addListener(this);
    }

    @Override
    public void onChange(List<FailedConnection> failedConnections) {
        updateFromAllFailedConnections(failedConnections);
    }

    private void updateFromAllFailedConnections(List<FailedConnection> failedConnections) {
        AppWhitelistModule autoTrustAppModule = getAutoTrustAppModule();
        List<String> existingWhitelistedDomains = autoTrustAppModule.getWhitelistedDomains();

        List<SSLWhitelistUrl> newWhiteListUrls = new LinkedList<>();
        failedConnections.forEach(fc -> fc.getDomains().forEach(domain -> {
            if (pendingDomains.containsKey(domain)) {
                handleAlreadyPending(domain, fc.getLastOccurrence(), newWhiteListUrls);
            } else {
                handleNotPending(domain, fc.getLastOccurrence(), existingWhitelistedDomains);
            }
        }));

        if (!newWhiteListUrls.isEmpty()) {
            appModuleService.addDomainsToModule(newWhiteListUrls, autoTrustAppModule.getId());
        }
    }

    private void handleAlreadyPending(String domain, Instant lastSeen, List<SSLWhitelistUrl> newWhiteListUrls) {
        if (pendingDomains.get(domain).isBefore(lastSeen)) {
            pendingDomains.remove(domain);
            newWhiteListUrls.add(new SSLWhitelistUrl("", domain));
        }
    }

    private void handleNotPending(String domain, Instant lastSeen, List<String> existingWhitelistedDomains) {
        if (!existingWhitelistedDomains.contains(domain) && !domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters(domain).isBlocked()) {
            pendingDomains.put(domain, lastSeen);
        }
    }

    @Override
    public void onReset() {
        AppWhitelistModule autoTrustAppModule = getAutoTrustAppModule();
        autoTrustAppModule.setWhitelistedDomains(Collections.emptyList());
        appModuleService.update(autoTrustAppModule, autoTrustAppModule.getId());
    }

    private AppWhitelistModule getAutoTrustAppModule() {
        return appModuleService.getAutoSslAppModule();
    }
}
