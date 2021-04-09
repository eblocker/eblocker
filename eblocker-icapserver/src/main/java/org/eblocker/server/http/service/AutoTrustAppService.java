package org.eblocker.server.http.service;

import com.google.inject.Inject;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.SSLWhitelistUrl;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.squid.FailedConnection;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

@SubSystemService(value = SubSystem.SERVICES)
public class AutoTrustAppService implements SquidWarningService.FailedConnectionsListener {

    private static final Logger log = LoggerFactory.getLogger(AutoTrustAppService.class);

    public static final Duration MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS = Duration.ofMinutes(30);
    public static final String CERT_UNTRUSTED_ERROR = "X509_V_ERR_CERT_UNTRUSTED";
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

        List<SSLWhitelistUrl> newWhiteListUrls =
                failedConnections.stream()
                        .sorted(Comparator.comparing(FailedConnection::getLastOccurrence))
                        .flatMap(fc ->
                                fc.getDomains().stream()
                                        .filter(not(existingWhitelistedDomains::contains))
                                        .map(domain -> processDomain(domain, fc))
                                        .flatMap(Optional::stream))
                        .distinct()
                        .map(domain -> new SSLWhitelistUrl("", domain))
                        .collect(Collectors.toList());

        if (!newWhiteListUrls.isEmpty()) {
            appModuleService.addDomainsToModule(newWhiteListUrls, autoTrustAppModule.getId());
        }
    }

    private Optional<String> processDomain(String domain, FailedConnection fc) {
        log.debug("Processing failed connection " + fc.getDomains() + " " + fc.getErrors() + " " + fc.getLastOccurrence());
        if (hasCertUntrustedError(fc)) {
            return processCertificateError(domain);
        } else if (pendingDomains.containsKey(domain)) {
            return processAlreadyPending(domain, fc.getLastOccurrence());
        } else {
            return processNotPending(domain, fc.getLastOccurrence());
        }
    }

    private boolean hasCertUntrustedError(FailedConnection fc) {
        return fc.getErrors().stream().anyMatch(s -> s.contains(CERT_UNTRUSTED_ERROR));
    }

    private Optional<String> processCertificateError(String domain) {
        if (isDomainEligibleForAutoTrustApp(domain)) {
            return Optional.of(domain);
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> processAlreadyPending(String domain, Instant lastSeen) {
        Instant pendingSeen = pendingDomains.get(domain);
        log.debug("pendingSeen for " + domain + " is " + pendingSeen);
        if (lastSeen.isAfter(pendingSeen) && lastSeen.minus(MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS).isBefore(pendingSeen)) {
            log.debug("Adding " + domain + " to AutoTrustApp");
            pendingDomains.remove(domain);
            return Optional.of(domain);
        } else {
            // overwrite with newer occurrence
            pendingDomains.put(domain, lastSeen);
            log.debug("Overwriting FC in pendingDomains");
            return Optional.empty();
        }
    }

    private Optional<String> processNotPending(String domain, Instant lastSeen) {
        if (isDomainEligibleForAutoTrustApp(domain)) {
            pendingDomains.put(domain, lastSeen);
            log.debug("Adding FC to pendingDomains");
        }
        return Optional.empty();
    }

    private boolean isDomainEligibleForAutoTrustApp(String domain) {
        return !domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters(domain).isBlocked();
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
