package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.ssl.AppWhitelistModule;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@SubSystemService(value = SubSystem.SERVICES)
public class AutoTrustAppService implements SquidWarningService.FailedConnectionsListener {

    public static final Duration MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS = Duration.ofMinutes(30);
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
        List<FailedConnection> sortedFailed = failedConnections.stream()
                .sorted(Comparator.comparing(FailedConnection::getLastOccurrence))
                .collect(Collectors.toList());
        AppWhitelistModule autoTrustAppModule = getAutoTrustAppModule();
        List<String> existingWhitelistedDomains = autoTrustAppModule.getWhitelistedDomains();

        List<SSLWhitelistUrl> newWhiteListUrls = new LinkedList<>();
        sortedFailed.forEach(fc -> fc.getDomains().forEach(domain -> {
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
        Instant pendingSeen = pendingDomains.get(domain);
        if (lastSeen.isAfter(pendingSeen) && lastSeen.minus(MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS).isBefore(pendingSeen)) {
            pendingDomains.remove(domain);
            newWhiteListUrls.add(new SSLWhitelistUrl("", domain));
        } else {
            // overwrite with newer occurrence
            pendingDomains.put(domain, lastSeen);
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
