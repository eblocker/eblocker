package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.DoctorDiagnosisResult;
import org.eblocker.server.common.data.DoctorDiagnosisResult.Tag;
import org.eblocker.server.common.data.FilterMode;
import org.eblocker.server.common.data.NetworkConfiguration;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.UserProfileModule.InternetAccessRestrictionMode;
import org.eblocker.server.common.data.dns.DnsRating;
import org.eblocker.server.common.data.dns.DnsResolvers;
import org.eblocker.server.common.data.dns.NameServerStats;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.ProblematicRouterDetection;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.update.AutomaticUpdater;
import org.eblocker.server.common.update.SystemUpdater;
import org.eblocker.server.http.security.SecurityService;
import org.eblocker.server.http.ssl.AppWhitelistModule;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.FilterStoreConfiguration;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Audience.EVERYONE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Audience.EXPERT;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Audience.NOVICE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.ANORMALY;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.FAILED_PROBE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.GOOD;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.HINT;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.RECOMMENDATION_NOT_FOLLOWED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ADMIN_PASSWORD_NOT_SET;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ALL_DEVICES_USE_AUTO_BLOCK_MODE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ALL_DEVICES_USE_AUTO_CONTROLBAR;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ALL_DEVICES_USE_MALWARE_FILTER;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ALL_DNS_SERVERS_GOOD;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ATA_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ATA_HAS_WWW_DOMAINS;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.ATA_NOT_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.AUTOMATIC_NETWORK_MODE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.AUTOMATIC_UPDATES_DISABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.AUTOMATIC_UPDATES_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.CHILDREN_WITHOUT_RESTRICTIONS;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.COOKIE_CONSENT_FILTER_DISABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.COOKIE_CONSENT_FILTER_ENABLED_OVERBLOCKING;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DDG_FILTER_DISABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DDG_FILTER_ENABLED_OVERBLOCKING;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DEVICES_MALWARE_FILTER_DISABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DEVICES_WITHOUT_AUTO_BLOCK_MODE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DEVICES_WITHOUT_AUTO_CONTROLBAR;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DNS_TOR_MAY_LEAD_TO_ERRORS;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.DNS_TOR_NOT_DISABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_DISABLED_FOR_NEW_DEVICES;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_DNS_RESOLVE_BROKEN;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_DNS_RESOLVE_WORKING;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_ENABLED_FOR_NEW_DEVICES;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_IP4_PING_BROKEN;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_IP4_PING_WORKING;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_IP6_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.EBLOCKER_NO_IP6;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.GOOD_NETWORK_MODE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.HTTPS_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.HTTPS_NOT_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.LAST_SYSTEM_UPDATE_OLDER_2_DAYS;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.NON_GOOD_DNS_SERVER;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.NO_DONOR_LICENSE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.PROBLEMATIC_ROUTER;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.STANDARD_PATTERN_FILTER_DISABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.STANDARD_PATTERN_FILTER_ENABLED;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.SYSTEM_UPDATE_NEVER_RAN;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Tag.SYSTEM_UPDATE_RAN;

@Singleton
public class DoctorService {

    private final NetworkServices networkServices;
    private final SslService sslService;
    private final AutomaticUpdater automaticUpdater;
    private final SystemUpdater systemUpdater;
    private final DnsStatisticsService dnsStatisticsService;
    private final DnsService dnsService;
    private final DeviceFactory deviceFactory;
    private final DeviceService deviceService;
    private final ParentalControlService parentalControlService;
    private final ProductInfoService productInfoService;
    private final AppModuleService appModuleService;
    private final FilterManager filterManager;
    private final SecurityService securityService;
    private Optional<Boolean> isProblematicRouter = Optional.empty();

    @Inject
    public DoctorService(NetworkServices networkServices, SslService sslService, AutomaticUpdater automaticUpdater, SystemUpdater systemUpdater, DnsStatisticsService dnsStatisticsService, DnsService dnsService,
                         DeviceFactory deviceFactory,
                         DeviceService deviceService, ParentalControlService parentalControlService, ProblematicRouterDetection problematicRouterDetection,
                         ProductInfoService productInfoService, AppModuleService appModuleService, FilterManager filterManager,
                         SecurityService securityService) {
        this.networkServices = networkServices;
        this.sslService = sslService;
        this.automaticUpdater = automaticUpdater;
        this.systemUpdater = systemUpdater;
        this.dnsStatisticsService = dnsStatisticsService;
        this.dnsService = dnsService;
        this.deviceFactory = deviceFactory;
        this.deviceService = deviceService;
        this.parentalControlService = parentalControlService;
        this.productInfoService = productInfoService;
        this.appModuleService = appModuleService;
        this.filterManager = filterManager;
        this.securityService = securityService;
        problematicRouterDetection.addObserver((observable, arg) -> {
            if (observable instanceof ProblematicRouterDetection && arg instanceof Boolean) {
                isProblematicRouter = Optional.of((Boolean) arg);
            }
        });
    }

    public List<DoctorDiagnosisResult> runDiagnosis() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();

        diagnoses.addAll(networkModeCheck());

        diagnoses.add(ipv4Ping());

        diagnoses.addAll(dnsLookupCheck());

        diagnoses.addAll(httpsRelatedChecks());

        diagnoses.addAll(ipv6Check());

        diagnoses.addAll(checkDevices());

        diagnoses.add(autoEnableNewDevicesCheck());

        //diagnoses.add(recommendationNotFollowedEveryone("FAKE: Malware & Phishing Blocker list is not enabled globally for Domain Blocking"));

        //diagnoses.add(recommendationNotFollowedEveryone("FAKE: Malware & Phishing Blocker list is not enabled globally for Pattern Blocking"));

        diagnoses.addAll(autoUpdateChecks());

        diagnoses.addAll(checkParentalControl());

        if (hasNonGoodNameServers()) {
            diagnoses.add(failedProbe(NON_GOOD_DNS_SERVER));
        } else {
            diagnoses.add(goodForEveryone(ALL_DNS_SERVERS_GOOD));
        }

        if (usesDnsOverTor()) {
            diagnoses.add(recommendationNotFollowed(NOVICE, DNS_TOR_NOT_DISABLED, ""));
            diagnoses.add(hintForExpert(DNS_TOR_MAY_LEAD_TO_ERRORS));
        }

        if (!securityService.isPasswordRequired()) {
            diagnoses.add(recommendationNotFollowedEveryone(ADMIN_PASSWORD_NOT_SET));
        }
        return diagnoses;
    }

    private List<DoctorDiagnosisResult> checkParentalControl() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();
        diagnoses.addAll(checkForUnrestrictedChildren());
        return diagnoses;
    }

    private List<DoctorDiagnosisResult> checkForUnrestrictedChildren() {
        List<String> unrestrictedChildren = parentalControlService.getProfiles().stream()
                .filter(not(UserProfileModule::isBuiltin))
                .filter(this::hasNoRestrictions)
                .map(UserProfileModule::getName)
                .collect(Collectors.toList());
        if (unrestrictedChildren.isEmpty()) {
            return Collections.emptyList();
        } else {
            return Collections.singletonList(new DoctorDiagnosisResult(ANORMALY, EVERYONE, CHILDREN_WITHOUT_RESTRICTIONS, unrestrictedChildren.toString()));
        }
    }

    private boolean hasNoRestrictions(UserProfileModule child) {
        return child.getMaxUsageTimeByDay().isEmpty() &&
                InternetAccessRestrictionMode.NONE.equals(child.getInternetAccessRestrictionMode()) &&
                !child.isControlmodeMaxUsage();
    }

    private List<DoctorDiagnosisResult> networkModeCheck() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();
        NetworkConfiguration currentNetworkConfiguration = networkServices.getCurrentNetworkConfiguration();
        if (currentNetworkConfiguration.isAutomatic()) {
            if (isProblematicRouter.isPresent() && isProblematicRouter.get()) {
                diagnoses.add(failedProbe(PROBLEMATIC_ROUTER));
            }
            diagnoses.add(new DoctorDiagnosisResult(HINT, EVERYONE, AUTOMATIC_NETWORK_MODE, ""));
        } else {
            diagnoses.add(goodForEveryone(GOOD_NETWORK_MODE));
        }
        return diagnoses;
    }

    private List<DoctorDiagnosisResult> autoUpdateChecks() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();
        if (productInfoService.hasFeature(ProductFeature.AUP)) {
            if (automaticUpdater.isActivated()) {
                diagnoses.add(goodForEveryone(AUTOMATIC_UPDATES_ENABLED));
            } else {
                diagnoses.add(recommendationNotFollowedEveryone(AUTOMATIC_UPDATES_DISABLED));
            }
        } else {
            diagnoses.add(recommendationNotFollowedEveryone(NO_DONOR_LICENSE));
        }

        LocalDateTime lastUpdateTime = systemUpdater.getLastUpdateTime();
        if (lastUpdateTime == null) {
            diagnoses.add(failedProbe(SYSTEM_UPDATE_NEVER_RAN));
        } else if (lastUpdateTime.isBefore(LocalDateTime.now().minusDays(2))) {
            diagnoses.add(failedProbe(LAST_SYSTEM_UPDATE_OLDER_2_DAYS, lastUpdateTime.toString()));
        } else {
            diagnoses.add(goodForEveryone(SYSTEM_UPDATE_RAN));
        }
        return diagnoses;
    }

    private List<DoctorDiagnosisResult> httpsRelatedChecks() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();
        if (sslService.isSslEnabled()) {
            diagnoses.add(goodForEveryone(HTTPS_ENABLED));

            AppWhitelistModule ata = appModuleService.getAutoSslAppModule();
            if (ata.isEnabled()) {
                diagnoses.add(goodForEveryone(ATA_ENABLED));
                List<String> wwwDomains = ata.getWhitelistedDomains().stream()
                        .filter(domain -> domain.startsWith("www."))
                        .collect(Collectors.toList());
                if (!wwwDomains.isEmpty()) {
                    diagnoses.add(hintForEveryone(ATA_HAS_WWW_DOMAINS, stringify(wwwDomains)));
                }
            } else {
                diagnoses.add(hintForEveryone(ATA_NOT_ENABLED));
            }

            verifyPatternFilters(diagnoses);
        } else {
            diagnoses.add(new DoctorDiagnosisResult(RECOMMENDATION_NOT_FOLLOWED, EXPERT, HTTPS_NOT_ENABLED, ""));
        }
        return diagnoses;
    }

    private void verifyPatternFilters(List<DoctorDiagnosisResult> diagnoses) {
        IntStream.range(0, 5).mapToObj(filterManager::getFilterStoreConfigurationById).forEach(patternFilter -> {
            if (patternFilter.isEnabled()) {
                diagnoses.add(goodForEveryone(STANDARD_PATTERN_FILTER_ENABLED, patternFilter.getName()));
            } else {
                diagnoses.add(recommendationNotFollowedEveryone(STANDARD_PATTERN_FILTER_DISABLED, patternFilter.getName()));
            }
        });

        FilterStoreConfiguration ddgTrFilter = filterManager.getFilterStoreConfigurationById(5);
        if (ddgTrFilter.isEnabled()) {
            diagnoses.add(hintForEveryone(DDG_FILTER_ENABLED_OVERBLOCKING));
        } else {
            diagnoses.add(hintForExpert(DDG_FILTER_DISABLED));
        }

        FilterStoreConfiguration cookieConsentFilter = filterManager.getFilterStoreConfigurationById(6);
        if (cookieConsentFilter.isEnabled()) {
            diagnoses.add(hintForEveryone(COOKIE_CONSENT_FILTER_ENABLED_OVERBLOCKING));
        } else {
            diagnoses.add(hintForExpert(COOKIE_CONSENT_FILTER_DISABLED));
        }
    }

    private DoctorDiagnosisResult autoEnableNewDevicesCheck() {
        if (deviceFactory.isAutoEnableNewDevices()) {
            return recommendationNotFollowedEveryone(EBLOCKER_ENABLED_FOR_NEW_DEVICES);
        } else {
            return goodForEveryone(EBLOCKER_DISABLED_FOR_NEW_DEVICES);
        }
    }

    private List<DoctorDiagnosisResult> dnsLookupCheck() {
        try {
            //noinspection ResultOfMethodCallIgnored
            InetAddress.getByName("eblocker.org");
            return Collections.singletonList(goodForEveryone(EBLOCKER_DNS_RESOLVE_WORKING));
        } catch (UnknownHostException e) {
            return Collections.singletonList(failedProbe(EBLOCKER_DNS_RESOLVE_BROKEN));
        }
    }

    private DoctorDiagnosisResult ipv4Ping() {
        if (pingHost(4, "1.1.1.1")) {
            return goodForEveryone(EBLOCKER_IP4_PING_WORKING);
        } else {
            return failedProbe(EBLOCKER_IP4_PING_BROKEN);
        }
    }

    private List<DoctorDiagnosisResult> ipv6Check() {
        if (pingHost(6, "ipv6-test.com")) {
            return List.of(failedProbe(EBLOCKER_IP6_ENABLED));
        } else {
            return List.of(goodForEveryone(EBLOCKER_NO_IP6));
        }
    }

    private boolean hasNonGoodNameServers() {
        DnsResolvers resolvers = dnsService.getDnsResolvers();
        return "custom".equalsIgnoreCase(resolvers.getCustomResolverMode()) &&
                resolvers.getCustomNameServers().stream()
                        .map(resolver -> dnsStatisticsService.getResolverStatistics(resolver,
                                ZonedDateTime.now().minusHours(24).toInstant()).getNameServerStats())
                        .flatMap(Collection::stream)
                        .map(NameServerStats::getRating)
                        .anyMatch(rating -> !DnsRating.GOOD.equals(rating));
    }

    private boolean usesDnsOverTor() {
        DnsResolvers resolvers = dnsService.getDnsResolvers();
        return "tor".equalsIgnoreCase(resolvers.getDefaultResolver());
    }

    private boolean pingHost(int ipVersion, String hostName) {
        String pingCommand = ipVersion == 4 ? "ping" : "ping6";
        ProcessBuilder pb = new ProcessBuilder(pingCommand, "-c", "1", hostName);
        try {
            Process start = pb.start();
            boolean finished = start.waitFor(5, TimeUnit.SECONDS);
            return finished && start.exitValue() == 0;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private List<DoctorDiagnosisResult> checkDevices() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();

        diagnoses.add(checkDevicesUseAutomaticFilterMode());

        diagnoses.add(checkDevicesHaveMalwareFilterEnabled());

        diagnoses.add(checkDevicesUseControlBarAutoMode());

        return diagnoses;
    }

    private DoctorDiagnosisResult checkDevicesUseAutomaticFilterMode() {
        List<String> nonAutomaticDevices = enabledDevices()
                .filter(d -> !FilterMode.AUTOMATIC.equals(d.getFilterMode()))
                .map(Device::getName)
                .collect(Collectors.toList());
        if (nonAutomaticDevices.isEmpty()) {
            return goodForEveryone(ALL_DEVICES_USE_AUTO_BLOCK_MODE);
        } else {
            return recommendationNotFollowedEveryone(DEVICES_WITHOUT_AUTO_BLOCK_MODE, stringify(nonAutomaticDevices));
        }
    }

    private DoctorDiagnosisResult checkDevicesHaveMalwareFilterEnabled() {
        List<String> nonMalwareDevices = enabledDevices()
                .filter(not(Device::isMalwareFilterEnabled))
                .map(Device::getName)
                .collect(Collectors.toList());
        if (nonMalwareDevices.isEmpty()) {
            return goodForEveryone(ALL_DEVICES_USE_MALWARE_FILTER);
        } else {
            return recommendationNotFollowedEveryone(DEVICES_MALWARE_FILTER_DISABLED, stringify(nonMalwareDevices));
        }
    }

    private DoctorDiagnosisResult checkDevicesUseControlBarAutoMode() {
        List<String> nonAutoControlBarModeDevices = enabledDevices()
                .filter(not(Device::isControlBarAutoMode))
                .map(Device::getName)
                .collect(Collectors.toList());
        if (nonAutoControlBarModeDevices.isEmpty()) {
            return goodForEveryone(ALL_DEVICES_USE_AUTO_CONTROLBAR);
        } else {
            return recommendationNotFollowedEveryone(DEVICES_WITHOUT_AUTO_CONTROLBAR, stringify(nonAutoControlBarModeDevices));
        }
    }

    private Stream<Device> enabledDevices() {
        return deviceService.getDevices(true).stream()
                .filter(Device::isEnabled);
    }

    private String stringify(List<?> l) {
        return l.stream().map(Object::toString).collect(Collectors.joining(", "));
    }

    private DoctorDiagnosisResult failedProbe(Tag tag) {
        return failedProbe(tag, "");
    }

    private DoctorDiagnosisResult failedProbe(Tag tag, String dynamicInfo) {
        return new DoctorDiagnosisResult(FAILED_PROBE, EVERYONE, tag, dynamicInfo);
    }

    private DoctorDiagnosisResult goodForEveryone(Tag tag) {
        return goodForEveryone(tag, "");
    }

    private DoctorDiagnosisResult goodForEveryone(Tag tag, String dynamicInfo) {
        return new DoctorDiagnosisResult(GOOD, EVERYONE, tag, dynamicInfo);
    }

    private DoctorDiagnosisResult recommendationNotFollowedEveryone(Tag tag) {
        return recommendationNotFollowedEveryone(tag, "");
    }

    private DoctorDiagnosisResult recommendationNotFollowedEveryone(Tag tag, String dynamicInfo) {
        return recommendationNotFollowed(EVERYONE, tag, dynamicInfo);
    }

    private DoctorDiagnosisResult recommendationNotFollowed(DoctorDiagnosisResult.Audience audience, Tag tag, String dynamicInfo) {
        return new DoctorDiagnosisResult(RECOMMENDATION_NOT_FOLLOWED, audience, tag, dynamicInfo);
    }

    private DoctorDiagnosisResult hintForExpert(Tag tag) {
        return hintForExpert(tag, "");
    }

    private DoctorDiagnosisResult hintForExpert(Tag tag, String dynamicInfo) {
        return new DoctorDiagnosisResult(HINT, EXPERT, tag, dynamicInfo);
    }

    private DoctorDiagnosisResult hintForEveryone(Tag tag) {
        return hintForEveryone(tag, "");
    }

    private DoctorDiagnosisResult hintForEveryone(Tag tag, String dynamicInfo) {
        return new DoctorDiagnosisResult(HINT, EVERYONE, tag, dynamicInfo);
    }
}
