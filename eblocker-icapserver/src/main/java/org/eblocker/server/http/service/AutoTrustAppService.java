package org.eblocker.server.http.service;

import com.google.inject.Inject;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
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
 *
 * * * Why does My own DTBL work even though it is enabled???
 */
@SubSystemService(value = SubSystem.SERVICES)
public class AutoTrustAppService implements SquidWarningService.FailedConnectionsListener {

    enum KnownError {
        SELF_SIGNED_CERT_IN_CHAIN("crtvd:19:X509_V_ERR_SELF_SIGNED_CERT_IN_CHAIN", true),
        CERT_UNTRUSTED("crtvd:27:X509_V_ERR_CERT_UNTRUSTED", true), // TODO: also seen for 5thmarket.info, which is a tracker, and for bzz.ch (web only)
        BAD_CERTIFICATE("ssl:1:error:14094412:SSL routines:ssl3_read_bytes:sslv3 alert bad certificate", true), // TODO seen for lookaside.facebook.com, i.instagram.com, graph.instagram.com, graph.facebook.com so is this really a isCertError?
        CERTIFICATE_UNKNOWN("ssl:1:error:14094416:SSL routines:ssl3_read_bytes:sslv3 alert certificate unknown", true),
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

    public static final Duration MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS = Duration.ofMinutes(30);
    private final AppModuleService appModuleService;
    private final DomainBlockingService domainBlockingService;
    private final DeviceService deviceService;
    private final Map<String, Instant> pendingDomains = new ConcurrentHashMap<>();
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
                                log.warn("Unknown error in failed connection: " + fc.getErrors());
                            }
                        })
                        .flatMap(fc ->
                                fc.getDomains().stream()
                                        .filter(not(existingWhitelistedDomains::contains))
                                        .map(domain -> processDomain(domain, fc))
                                        .flatMap(Optional::stream))
                        .distinct()
                        .peek(domain -> log.debug("Whitelisting " + domain))
                        .map(domain -> new SSLWhitelistUrl("", domain))
                        .collect(Collectors.toList());

        if (!newWhiteListUrls.isEmpty()) {
            appModuleService.addDomainsToModule(newWhiteListUrls, autoTrustAppModule.getId());
        }
    }

    private Optional<String> processDomain(String domain, FailedConnection fc) {
        logFailedConnection(fc);
        if (!happendOnSSLDevice(fc)) {
            return Optional.empty();
        } else if (!isDomainEligibleForAutoTrustApp(domain)) {
            log.trace("Not eligible for AutoTrustApp: " + domain);
            pendingDomains.remove(domain); // border case: just became not eligible
            return Optional.empty();
        } else if (hasNonCertificateError(fc)) {
            log.debug("Skipping. No cert error " + domain);
            // also remove from pending domains?
            return Optional.empty();
        } else if (hasObviousRootCertificateProblemError(fc)) {
            log.debug("hasObviousRootCertificateProblemError" + domain);
            return processCertificateError(domain);
        } else if (isPending(domain)) {
            return processAlreadyPending(domain, fc.getLastOccurrence());
        } else {
            return processNotPending(domain, fc.getLastOccurrence());
        }
    }

    private void logFailedConnection(FailedConnection fc) {
        if (Instant.now().minus(Duration.ofMinutes(61)).isBefore(fc.getLastOccurrence())) {
            log.debug("Processing failed connection " + fc.getDomains() + " " + fc.getErrors() + " " + fc.getLastOccurrence() + " " + fc.getDeviceIds().stream().map(deviceService::getDeviceById).filter(Objects::nonNull).collect(Collectors.toList()));
        } else {
            log.trace("Processing failed connection " + fc.getDomains() + " " + fc.getErrors() + " " + fc.getLastOccurrence() + " " + fc.getDeviceIds().stream().map(deviceService::getDeviceById).filter(Objects::nonNull).collect(Collectors.toList()));
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

    private boolean isPending(String domain) {
        return pendingDomains.containsKey(domain);
    }

    private Optional<String> processCertificateError(String domain) {
        return Optional.of(domain);
    }

    private Optional<String> processAlreadyPending(String domain, Instant lastSeen) {
        Instant pendingSeen = pendingDomains.get(domain);
        if (lastSeen.isAfter(pendingSeen)) {
            if (lastSeen.minus(MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS).isBefore(pendingSeen)) {
                pendingDomains.remove(domain);
                return Optional.of(domain);
            } else {
                // overwrite with newer occurrence
                pendingDomains.put(domain, lastSeen);
                log.debug("Overwriting " + domain + " in pendingDomains with newer occurence");
                return Optional.empty();
            }
        } else {
            return Optional.empty();
        }
    }

    private Optional<String> processNotPending(String domain, Instant lastSeen) {
        if (Instant.now().minus(MAX_RANGE_BETWEEN_TWO_FAILED_CONECTIONS).isBefore(lastSeen)) {
            pendingDomains.put(domain, lastSeen);
            log.debug("Adding " + domain + " to pendingDomains");
        } else {
            log.trace("FailedConnection too old to add domain " + domain);
        }
        return Optional.empty();
    }

    private boolean isDomainEligibleForAutoTrustApp(String domain) {
        return !isBlocked(domain) &&
                !isDomainAlreadySucessful(domain);
    }

    private boolean isBlocked(String domain) {
        boolean blocked = domainBlockingService.isDomainBlockedByMalwareAdsTrackersFilters(domain).isBlocked();
        if (blocked) {
            log.trace("Domain is blocked: " + domain);
        }
        return blocked;
    }

    private boolean isDomainAlreadySucessful(String domain) {
        boolean domainAlreadySucessful = successfulSSLDomains.isDomainAlreadySucessful(domain);
        if (domainAlreadySucessful) {
            log.debug("Domain already successful: " + domain);
        }
        return domainAlreadySucessful;
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

    public void recordSuccessfulSSL(String domain) {
        successfulSSLDomains.recordDomain(domain);
    }
}
