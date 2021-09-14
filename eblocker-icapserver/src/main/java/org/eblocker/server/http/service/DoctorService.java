package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.DeviceFactory;
import org.eblocker.server.common.data.DoctorDiagnosisResult;
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
import org.eblocker.server.common.update.DebianUpdater;
import org.eblocker.server.http.ssl.AppWhitelistModule;

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
import java.util.stream.Stream;

import static java.util.function.Predicate.not;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Audience.EVERYONE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Audience.EXPERT;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.ANORMALY;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.FAILED_PROBE;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.GOOD;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.HINT;
import static org.eblocker.server.common.data.DoctorDiagnosisResult.Severity.RECOMMENDATION_NOT_FOLLOWED;

@Singleton
public class DoctorService {

    private final NetworkServices networkServices;
    private final SslService sslService;
    private final AutomaticUpdater automaticUpdater;
    private final DebianUpdater debianUpdater;
    private final DnsStatisticsService dnsStatisticsService;
    private final DnsService dnsService;
    private final DeviceFactory deviceFactory;
    private final DeviceService deviceService;
    private final ParentalControlService parentalControlService;
    private final ProductInfoService productInfoService;
    private final AppModuleService appModuleService;
    private Optional<Boolean> isProblematicRouter = Optional.empty();

    @Inject
    public DoctorService(NetworkServices networkServices, SslService sslService, AutomaticUpdater automaticUpdater, DebianUpdater debianUpdater, DnsStatisticsService dnsStatisticsService, DnsService dnsService,
                         DeviceFactory deviceFactory,
                         DeviceService deviceService, ParentalControlService parentalControlService, ProblematicRouterDetection problematicRouterDetection,
                         ProductInfoService productInfoService, AppModuleService appModuleService) {
        this.networkServices = networkServices;
        this.sslService = sslService;
        this.automaticUpdater = automaticUpdater;
        this.debianUpdater = debianUpdater;
        this.dnsStatisticsService = dnsStatisticsService;
        this.dnsService = dnsService;
        this.deviceFactory = deviceFactory;
        this.deviceService = deviceService;
        this.parentalControlService = parentalControlService;
        this.productInfoService = productInfoService;
        this.appModuleService = appModuleService;
        problematicRouterDetection.addObserver((observable, arg) -> {
            if (observable instanceof ProblematicRouterDetection && arg instanceof Boolean) {
                isProblematicRouter = Optional.of((Boolean) arg);
            }
        });
    }

    public List<DoctorDiagnosisResult> runDiagnosis() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();

        diagnoses.add(networkModeCheck());

        diagnoses.add(ipv4Ping());

        diagnoses.addAll(dnsLookupCheck());

        diagnoses.addAll(httpsRelatedChecks());

        diagnoses.addAll(checkDevices());

        diagnoses.add(autoEnableNewDevicesCheck());

        //diagnoses.add(recommendationNotFollowedEveryone("FAKE: Malware & Phishing Blocker list is not enabled globally for Domain Blocking"));

        //diagnoses.add(recommendationNotFollowedEveryone("FAKE: Malware & Phishing Blocker list is not enabled globally for Pattern Blocking"));

        diagnoses.addAll(autoUpdateChecks());

        diagnoses.addAll(checkParentalControl());

        if (hasNonGoodNameServers()) {
            diagnoses.add(failedProbe("You have name servers with non-good rating"));
        } else {
            diagnoses.add(goodForEveryone("Your name servers look good"));
        }

        if (isProblematicRouter.isPresent() && isProblematicRouter.get()) {
            diagnoses.add(failedProbe("Your router is problematic!"));
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
            return Collections.singletonList(new DoctorDiagnosisResult(ANORMALY, EVERYONE, "The following children have no restrictions at all: " + unrestrictedChildren));
        }
    }

    private boolean hasNoRestrictions(UserProfileModule child) {
        return child.getMaxUsageTimeByDay().isEmpty() &&
                InternetAccessRestrictionMode.NONE.equals(child.getInternetAccessRestrictionMode()) &&
                !child.isControlmodeMaxUsage();
    }

    private DoctorDiagnosisResult networkModeCheck() {
        NetworkConfiguration currentNetworkConfiguration = networkServices.getCurrentNetworkConfiguration();
        if (currentNetworkConfiguration.isAutomatic()) {
            return new DoctorDiagnosisResult(HINT, EVERYONE, "You are using the automatic network mode. It may cause problems.");
        } else {
            return goodForEveryone("You are using a good network mode");
        }
    }

    private List<DoctorDiagnosisResult> autoUpdateChecks() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();
        if (productInfoService.hasFeature(ProductFeature.AUP)) {
            if (automaticUpdater.isActivated()) {
                diagnoses.add(goodForEveryone("Your eBlocker is configured to automatically update itself on a daily basis"));
            } else {
                diagnoses.add(recommendationNotFollowedEveryone("Automatic updates are disabled"));
            }
        } else {
            diagnoses.add(recommendationNotFollowedEveryone("You cannot run automatic updates because you don't have donor license."));
        }

        LocalDateTime lastUpdateTime = debianUpdater.getLastUpdateTime();
        if (lastUpdateTime == null) {
            diagnoses.add(failedProbe("System updates never ran"));
        } else if (lastUpdateTime.isBefore(LocalDateTime.now().minusDays(2))) {
            diagnoses.add(failedProbe("Last system update is older than two days : " + lastUpdateTime));
        } else {
            diagnoses.add(goodForEveryone("System updates are okay"));
        }
        return diagnoses;
    }

    private List<DoctorDiagnosisResult> httpsRelatedChecks() {
        List<DoctorDiagnosisResult> diagnoses = new ArrayList<>();
        if (sslService.isSslEnabled()) {
            diagnoses.add(goodForEveryone("You have HTTPS enabled"));

            AppWhitelistModule ata = appModuleService.getAutoSslAppModule();
            if (ata.isEnabled()) {
                diagnoses.add(goodForEveryone("You have ATA enabled. It's still beta but generally a good thing to have."));
                List<String> wwwDomains = ata.getWhitelistedDomains().stream()
                        .filter(domain -> domain.startsWith("www."))
                        .collect(Collectors.toList());
                if (!wwwDomains.isEmpty()) {
                    diagnoses.add(hintForEveryone("ATA contains the following domains starting with 'www.'. This might hides the eBlocker icon on these pages " + wwwDomains));
                }
            } else {
                diagnoses.add(hintForEveryone("Please consider enabeling ATA, even though it's still beta."));
            }

            //diagnoses.add(hintForExpert("FAKE: The DDGTR blocker list is not enabled"));

            //diagnoses.add(hintForExpert("FAKE: The cookie blocker list is not enabled"));
            ipv6Check(diagnoses);
        } else {
            diagnoses.add(new DoctorDiagnosisResult(RECOMMENDATION_NOT_FOLLOWED, EXPERT, "HTTPS is not enabled. You will get better tracking protection with it"));
        }
        return diagnoses;
    }

    private DoctorDiagnosisResult autoEnableNewDevicesCheck() {
        if (deviceFactory.isAutoEnableNewDevices()) {
            return recommendationNotFollowedEveryone("eBlocker will be automatically enabled for new devices. This may cause trouble when a new device is not ready for eBlocker");
        } else {
            return goodForEveryone("eBlocker will not be automatically enabled for new devices, so you don't run into trouble during setup. Don't forget to enable new devices manually...");
        }
    }

    private List<DoctorDiagnosisResult> dnsLookupCheck() {
        try {
            //noinspection ResultOfMethodCallIgnored
            InetAddress.getByName("eblocker.org");
            return Collections.singletonList(goodForEveryone("The eBlocker itself can resolve DNS names"));
        } catch (UnknownHostException e) {
            return Collections.singletonList(failedProbe("The eBlocker itself cannot resolve DNS names. Check your DNS settings"));
        }
    }

    private DoctorDiagnosisResult ipv4Ping() {
        if (pingHost(4, "1.1.1.1")) {
            return goodForEveryone("Your eBlocker can reach the internet via ICMP/ping");
        } else {
            return failedProbe("Your eBlocker cannot reach the internet via ICMP/ping");
        }
    }

    private void ipv6Check(List<DoctorDiagnosisResult> diagnoses) {
        if (pingHost(6, "ipv6-test.com")) {
            diagnoses.add(failedProbe("Your network can access the internet via IPv6. That will bypass the tracking of eBlocker if HTTPS support is enabled."));
        } else {
            diagnoses.add(goodForEveryone("Your network cannot access the internet via IPv6. This is good news as the eBlocker does not support IPv6 yet."));
        }
    }

    private boolean hasNonGoodNameServers() {
        DnsResolvers resolvers = dnsService.getDnsResolvers();
        return resolvers.getCustomNameServers().stream()
                .map(resolver -> dnsStatisticsService.getResolverStatistics(resolver,
                        ZonedDateTime.now().minusHours(24).toInstant()).getNameServerStats())
                .flatMap(Collection::stream)
                .map(NameServerStats::getRating)
                .anyMatch(rating -> !DnsRating.GOOD.equals(rating));
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
            return goodForEveryone("All your devices are set to automatic mode");
        } else {
            return recommendationNotFollowedEveryone("The following devices are not set to automatic blocking mode: " + nonAutomaticDevices);
        }
    }

    private DoctorDiagnosisResult checkDevicesHaveMalwareFilterEnabled() {
        List<String> nonMalwareDevices = enabledDevices()
                .filter(not(Device::isMalwareFilterEnabled))
                .map(Device::getName)
                .collect(Collectors.toList());
        if (nonMalwareDevices.isEmpty()) {
            return goodForEveryone("All your devices have malware filtering enabled");
        } else {
            return recommendationNotFollowedEveryone("The following devices have malware filtering disabled: " + nonMalwareDevices);
        }
    }

    private DoctorDiagnosisResult checkDevicesUseControlBarAutoMode() {
        List<String> nonAutoControlBarModeDevices = enabledDevices()
                .filter(not(Device::isControlBarAutoMode))
                .map(Device::getName)
                .collect(Collectors.toList());
        if (nonAutoControlBarModeDevices.isEmpty()) {
            return goodForEveryone("All your devices are using the automatic ControlBar configuration");
        } else {
            return recommendationNotFollowedEveryone("The following devices are not using the automatic ControlBar configuration: " + nonAutoControlBarModeDevices);
        }
    }

    private Stream<Device> enabledDevices() {
        return deviceService.getDevices(true).stream()
                .filter(Device::isEnabled);
    }

    private DoctorDiagnosisResult failedProbe(String message) {
        return new DoctorDiagnosisResult(FAILED_PROBE, EVERYONE, message);
    }

    private DoctorDiagnosisResult goodForEveryone(String message) {
        return new DoctorDiagnosisResult(GOOD, EVERYONE, message);
    }

    private DoctorDiagnosisResult recommendationNotFollowedEveryone(String message) {
        return new DoctorDiagnosisResult(RECOMMENDATION_NOT_FOLLOWED, EVERYONE, message);
    }

    private DoctorDiagnosisResult hintForExpert(String message) {
        return new DoctorDiagnosisResult(HINT, EXPERT, message);
    }

    private DoctorDiagnosisResult hintForEveryone(String message) {
        return new DoctorDiagnosisResult(HINT, EVERYONE, message);
    }
}
