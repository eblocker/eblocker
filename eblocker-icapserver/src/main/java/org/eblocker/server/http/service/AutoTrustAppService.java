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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.data.Device;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static java.util.function.Predicate.not;

/**
 * Possible improvements:
 * * Never whitelist a domain name for which a pattern blocker URL exists
 * ____+ prevents whitelisting when a popular tracker URL leads to random connection errors
 * ____- if the domain of a blocker URL is rather generic (i.e. amazon.com), ATA will never whitelist it
 * ____? Check the logs how many whitelisting of tracker domains this could prevent
 * * Immediately whitelist (after tracking check) for all cert related errors
 * ___+ grep "Processing " /var/log/eblocker/eblocker-system.log* | grep -v "No error" | grep -v "reset by peer" | grep -v "Broken pipe" | cut -d "[" -f 5 | cut -d "]" -f 1 | sort | uniq
 * ____+ faster whitelisting in case of certificate pinning
 * ____+ Tracker check is still in place, so this does not affect handling of known evil domains
 * ____+ Danger is adding
 * ____+ (hopefully) no additional whitelisting of favorite websites as they don't produce these kinds of errors
 * ____+ (hopefully) no additional whitelisting of tracking domains as they don't produce these kinds of errors and the tracking check would still happen
 * <p>
 * crtvd:27:X509_V_ERR_CERT_UNTRUSTED
 * ssl:1:error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate
 * ssl:1:error:14094416:SSL routines:ssl3_read_bytes:sslv3 alert certificate unknown
 * ssl:1:error:14094418:SSL routines:ssl3_read_bytes:tlsv1 alert unknown ca
 * ssl:1:error:14209102:SSL routines:tls_early_post_process_client_hello:unsupported protocol
 * ssl:6:error:00000000:lib(0):func(0):reason(0)
 * <p>
 * ____- there seem to be cases where cert-related errors stem from browsing (e.g. datenschutz-zwecklos.de for benne).
 * ____? Check in how many cases this would speed up whitelisting for certificate pinning
 * ____? Check in how many cases this would put favorite websites on the list IN ADDITION
 * <p>
 * * * When recording successful SSL connections, also record the device
 * ____+ Spotify seems to work fine on iOS but complains about dealer.spotify.com on macOS
 * ____- Blows up the recoding of successful domains
 * ____- Device A cannot benefit from the successful SSL connections on device B
 * <p>
 * * * Why does My own DTBL work even though it is enabled???
 */
@Singleton
@SubSystemService(value = SubSystem.SERVICES)
public class AutoTrustAppService implements SquidWarningService.FailedConnectionsListener {

    enum KnownError {
        SELF_SIGNED_CERT_IN_CHAIN("crtvd:19:X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN", true),
        DEPTH_ZERO_SELF_SIGNED_CERT("crtvd:18:X509_V_ERR_DEPTH_ZERO_SELF_SIGNED_CERT", true),
        CERT_UNTRUSTED("crtvd:27:X509_V_ERR_CERT_UNTRUSTED", true), // TODO: also seen for 5thmarket.info, which is a tracker, and for bzz.ch (web only)
        BAD_CERTIFICATE("ssl:1:error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate", true), // TODO seen for lookaside.facebook.com, i.instagram.com, graph.instagram.com, graph.facebook.com so is this really a isCertError?
        CERTIFICATE_UNKNOWN("ssl:1:error:14094416:SSL routines:ssl3_read_bytes:sslv3 alert certificate unknown", true), // TODO seen for www.srf.ch
        UNKNOWN_CA("ssl:1:error:14094418:SSL routines:ssl3_read_bytes:tlsv1 alert unknown ca", true),
        UNSUPPORTED_PROTOCOL("ssl:1:error:14209102:SSL routines:tls_early_post_process_client_hello:unsupported protocol", true),
        CONNECTION_RESET("io:104:(104) Connection reset by peer", false),
        NO_ERROR("io:0:(0) No error.", false),
        BROKEN_PIPE("io:32:(32) Broken pipe", false),
        SOME_UNKNOWN_SSL("ssl:6:error:00000000:lib(0):func(0):reason(0)", false), // Don't know what this means
        DOMAIN_MISMATCH_ERROR("crtvd:-1:SQUID_X509_V_ERR_DOMAIN_MISMATCH", false);
        private final String message;
        private final boolean isCertError;

        KnownError(String message, boolean isCertError) {
            this.message = message;
            this.isCertError = isCertError;
        }

        private static final List<KnownError> certErrors =
                Arrays.stream(KnownError.values()).filter(ke -> ke.isCertError).collect(Collectors.toList());

        private boolean matchesError(String error) {
            return error.contains(message);
        }

        public static boolean hasCertError(FailedConnection fc) {
            return fc.getErrors().stream()
                    .anyMatch(err -> certErrors.stream().anyMatch(ce -> ce.matchesError(err)));
        }

        public static boolean hasUnknownError(FailedConnection fc) {
            return !fc.getErrors().stream()
                    .allMatch(err -> Arrays.stream(KnownError.values()).anyMatch(ke -> ke.matchesError(err)));
        }

        public String getMessage() {
            return message;
        }
    }

    private static final Logger log = LoggerFactory.getLogger(AutoTrustAppService.class);

    static final Duration FAILED_CONECTIONS_CUT_OFF_DURATION = Duration.ofMinutes(30);
    static final Duration TOO_OLD = Duration.ofMinutes(30);
    private final AppModuleService appModuleService;
    private final DomainBlockingService domainBlockingService;
    private final DeviceService deviceService;
    private final Map<String, List<Instant>> pendingDomains = new ConcurrentHashMap<>();
    private final SuccessfulSSLDomains successfulSSLDomains = new SuccessfulSSLDomains(1000, 100, 0.001);

    @Inject
    public AutoTrustAppService(SquidWarningService squidWarningService,
                               AppModuleService appModuleService,
                               DomainBlockingService domainBlockingService,
                               DeviceService deviceService) {
        this.appModuleService = appModuleService;
        this.domainBlockingService = domainBlockingService;
        this.deviceService = deviceService;

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
                        .peek(fc -> {
                            if (KnownError.hasUnknownError(fc)) {
                                log.warn("Unknown error in failed connection {} for domains {}", fc.getErrors(), fc.getDomains());
                            }
                        })
                        .flatMap(fc ->
                                fc.getDomains().stream()
                                        .filter(not(existingWhitelistedDomains::contains))
                                        .map(domain -> processDomain(domain, fc))
                                        .flatMap(Optional::stream))
                        .distinct()
                        .peek(domain -> log.debug("Whitelisting {}", domain))
                        .map(domain -> new SSLWhitelistUrl("", domain))
                        .collect(Collectors.toList());

        if (!newWhiteListUrls.isEmpty()) {
            appModuleService.addDomainsToModule(newWhiteListUrls, autoTrustAppModule.getId());
        }

        pruneTooOldPendingDomains();
    }

    private Optional<String> processDomain(String domain, FailedConnection fc) {
        logFailedConnection(fc);
        if (!happendOnSSLDevice(fc)) {
            return Optional.empty();
        } else if (tooOld(fc)) {
            return Optional.empty();
        } else if (!isDomainEligibleForAutoTrustApp(domain)) {
            log.trace("Not eligible for AutoTrustApp: {}", domain);
            pendingDomains.remove(domain); // border case: just became not eligible
            return Optional.empty();
        } else if (hasNonCertificateError(fc)) {
            log.debug("Skipping. No cert error {}", domain);
            // also remove from pending domains?
            return Optional.empty();
        } else if (hasObviousRootCertificateProblemError(fc)) {
            log.debug("hasObviousRootCertificateProblemError {}", domain);
            return processCertificateError(domain);
        } else {
            return processWithPendings(domain, fc.getLastOccurrence());
        }
    }

    private boolean tooOld(FailedConnection fc) {
        return tooOld(fc.getLastOccurrence());
    }

    private boolean tooOld(Instant instant) {
        return instant.isBefore(Instant.now().minus(TOO_OLD));
    }

    private void logFailedConnection(FailedConnection fc) {
        if (Instant.now().minus(Duration.ofMinutes(61)).isBefore(fc.getLastOccurrence())) {
            log.debug("Processing failed connection {} {} {} {}", fc.getDomains(), fc.getErrors(), fc.getLastOccurrence(), fc.getDeviceIds().stream().map(deviceService::getDeviceById).filter(Objects::nonNull).collect(Collectors.toList()));
        } else {
            log.trace("Processing failed connection {} {} {} {}", fc.getDomains(), fc.getErrors(), fc.getLastOccurrence(), fc.getDeviceIds().stream().map(deviceService::getDeviceById).filter(Objects::nonNull).collect(Collectors.toList()));
        }
    }

    private boolean happendOnSSLDevice(FailedConnection fc) {
        return fc.getDeviceIds().stream()
                .map(deviceService::getDeviceById)
                .filter(Objects::nonNull)
                .filter(Device::isEnabled)
                .anyMatch(Device::isSslEnabled);
    }

    private boolean hasNonCertificateError(FailedConnection fc) {
        return fc.getErrors().stream()
                .anyMatch(KnownError.DOMAIN_MISMATCH_ERROR::matchesError);
        // maybe even automatically remove all subdomains from the list as it can cause various problems? (see armakeup.com and https://community.letsencrypt.org/t/high-server-load-and-longer-time-to-produce-certificates/67760/29)
    }

    private boolean hasObviousRootCertificateProblemError(FailedConnection fc) {
        return KnownError.hasCertError(fc);
    }

    private Optional<String> processCertificateError(String domain) {
        return pendingSuccess(domain);
    }

    private Optional<String> processWithPendings(String domain, Instant lastSeen) {
        List<Instant> previousSeens = pendingDomains.get(domain);
        NavigableSet<Instant> allOccurences = previousSeens == null ? new TreeSet<>() : new TreeSet<>(previousSeens);
        allOccurences.add(lastSeen);

        pruneToRange(allOccurences);

        if (allOccurences.isEmpty()) {
            pendingDomains.remove(domain);
        }

        if (domain.startsWith("api.")) {
            return pendingSuccess(domain);
        } else if (domain.startsWith("www.")) {
            if (allOccurences.size() > 2) {
                return pendingSuccess(domain);
            } else {
                return stillPending(domain, allOccurences);
            }
        } else {
            if (allOccurences.size() == 1) {
                return stillPending(domain, allOccurences);
            } else {
                return pendingSuccess(domain);
            }
        }
    }

    private Optional<String> pendingSuccess(String domain) {
        pendingDomains.remove(domain);
        return Optional.of(domain);
    }

    private Optional<String> stillPending(String domain, NavigableSet<Instant> allOccurences) {
        pendingDomains.put(domain, new ArrayList<>(allOccurences));
        log.debug("Updating {} in pendingDomains with newer occurence", domain);
        return Optional.empty();
    }

    private void pruneToRange(Set<Instant> previousSeens) {
        Instant minimum = Instant.now().minus(FAILED_CONECTIONS_CUT_OFF_DURATION);
        //what 's the difference between the two constants?'
        // TODO: add test for this (needs tooOld to not already filter these)
        previousSeens.removeIf(i -> i.isBefore(minimum));
    }

    private boolean isDomainEligibleForAutoTrustApp(String domain) {
        return !isBlocked(domain) &&
                !isDomainAlreadySucessful(domain) &&
                !isExplicitlyExcluded(domain);
    }

    private boolean isBlocked(String domain) {
        boolean blocked = domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters(domain).isBlocked();
        if (blocked) {
            log.trace("Domain is blocked: {}", domain);
        }
        return blocked;
    }

    private boolean isDomainAlreadySucessful(String domain) {
        boolean domainAlreadySucessful = successfulSSLDomains.isDomainAlreadySucessful(domain);
        if (domainAlreadySucessful) {
            log.debug("Domain already successful: {}", domain);
        }
        return domainAlreadySucessful;
    }

    private boolean isExplicitlyExcluded(String domain) {
        return domain.endsWith("eblocker.org") || domain.contains("youtube.");
    }

    private void pruneTooOldPendingDomains() {
        Iterator<Map.Entry<String, List<Instant>>> iterator = pendingDomains.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, List<Instant>> e = iterator.next();
            List<Instant> pruned = e.getValue().stream().filter(not(this::tooOld)).collect(Collectors.toList());
            if (pruned.isEmpty()) {
                log.debug("Removing {} from pending domains", e.getKey());
                iterator.remove();
            } else {
                e.setValue(pruned);
            }
        }
    }

    @Override
    public void onReset() {
        AppWhitelistModule autoTrustAppModule = getAutoTrustAppModule();
        autoTrustAppModule.setWhitelistedDomains(Collections.emptyList());
        appModuleService.update(autoTrustAppModule, autoTrustAppModule.getId());
        pendingDomains.clear();
    }

    private AppWhitelistModule getAutoTrustAppModule() {
        return appModuleService.getAutoSslAppModule();
    }

    public void setAutoTrustAppEnabled(boolean enabled) {
        appModuleService.storeAndActivateEnabledState(getAutoTrustAppModule().getId(), enabled);
    }

    public boolean isAutoTrustAppEnabled() {
        return getAutoTrustAppModule().isEnabled();
    }

    public void recordSuccessfulSSL(String domain) {
        successfulSSLDomains.recordDomain(domain);
    }
}
