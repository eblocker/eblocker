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
package org.eblocker.server.app;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.executor.NamedRunnable;
import org.eblocker.server.common.network.ArpListener;
import org.eblocker.server.common.network.DhcpBindListener;
import org.eblocker.server.common.network.DhcpListener;
import org.eblocker.server.common.network.NeighborDiscoveryListener;
import org.eblocker.server.common.network.NetworkInterfaceWatchdog;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.network.ZeroconfRegistrationService;
import org.eblocker.server.common.openvpn.server.OpenVpnAddressListener;
import org.eblocker.server.common.scheduler.AppModuleServiceScheduler;
import org.eblocker.server.common.scheduler.BlockedDomainsWriteScheduler;
import org.eblocker.server.common.scheduler.BlockerUpdateScheduler;
import org.eblocker.server.common.scheduler.DeviceServiceScheduler;
import org.eblocker.server.common.scheduler.DnsGatewayNamesScheduler;
import org.eblocker.server.common.scheduler.DnsStatisticsScheduler;
import org.eblocker.server.common.scheduler.DynDnsUpdateScheduler;
import org.eblocker.server.common.scheduler.FilterManagerScheduler;
import org.eblocker.server.common.scheduler.FilterStatisticsDeleteScheduler;
import org.eblocker.server.common.scheduler.FilterStatisticsUpdateScheduler;
import org.eblocker.server.common.scheduler.Ip6MulticastPingScheduler;
import org.eblocker.server.common.scheduler.Ip6NetworkScanScheduler;
import org.eblocker.server.common.scheduler.Ip6RouterAdvertiserScheduler;
import org.eblocker.server.common.scheduler.IpAdressValidatorScheduler;
import org.eblocker.server.common.scheduler.LicenseExpirationCheckScheduler;
import org.eblocker.server.common.scheduler.MalwareUpdateScheduler;
import org.eblocker.server.common.scheduler.MessageCenterServiceScheduler;
import org.eblocker.server.common.scheduler.OpenVpnServiceScheduler;
import org.eblocker.server.common.scheduler.PCAccessRestrictionsServiceScheduler;
import org.eblocker.server.common.scheduler.ProblematicRouterDetectionScheduler;
import org.eblocker.server.common.scheduler.Scheduler;
import org.eblocker.server.common.scheduler.SessionPurgerScheduler;
import org.eblocker.server.common.scheduler.StartupTaskScheduler;
import org.eblocker.server.common.scheduler.TrafficAccounterScheduler;
import org.eblocker.server.common.scheduler.UpnpWatchdogScheduler;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.startup.SubSystemShutdown;
import org.eblocker.server.common.update.AutomaticUpdater;
import org.eblocker.server.common.update.ControlBarAliasUpdater;
import org.eblocker.server.http.service.DeviceScanningService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@SubSystemService(SubSystem.BACKGROUND_TASKS)
public class BackgroundServices {

    @SuppressWarnings("unused")
    private static final Logger log = LoggerFactory.getLogger(BackgroundServices.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private final ScheduledExecutorService highPrioExecutorService;
    private final ScheduledExecutorService lowPrioExecutorService;
    private final Executor unlimitedCachePoolExecutor;

    private final Scheduler filterManagerScheduler;
    private final SessionPurgerScheduler sessionPurgerScheduler;
    private final StartupTaskScheduler startupTaskScheduler;
    private final ArpListener arpListener;
    private final NeighborDiscoveryListener neighborDiscoveryListener;
    private final IpAdressValidatorScheduler ipAdressValidatorScheduler;
    private final Ip6MulticastPingScheduler ip6MulticastPingScheduler;
    private final Ip6NetworkScanScheduler ip6NetworkScanScheduler;
    private final Ip6RouterAdvertiserScheduler ip6RouterAdvertiserScheduler;

    private final ProblematicRouterDetectionScheduler problematicRouterDetectionScheduler;
    private final AutomaticUpdater autoUpdater;
    private DhcpListener dhcpListener;
    private DhcpBindListener dhcpBindListener;
    private final TorController torController;

    private final long torConnectionCheckDelay;

    private final AppModuleServiceScheduler appModuleServiceScheduler;
    private final PCAccessRestrictionsServiceScheduler pcAccessRestrictionsServiceScheduler;
    private final OpenVpnServiceScheduler openVpnServiceScheduler;
    private final OpenVpnAddressListener openVpnAddressListener;
    private final DeviceServiceScheduler deviceServiceScheduler;
    private final TrafficAccounterScheduler trafficAccounterScheduler;
    private final NetworkInterfaceWatchdog networkInterfaceWatchdog;
    private final MessageCenterServiceScheduler messageCenterServiceScheduler;
    private final MalwareUpdateScheduler malwareUpdateScheduler;
    private final DnsStatisticsScheduler dnsStatisticsScheduler;
    private final DnsGatewayNamesScheduler dnsGatewayNamesScheduler;
    private final ZeroconfRegistrationService zeroconfRegistrationService;
    private final ControlBarAliasUpdater controlBarAliasUpdater;
    private final DeviceScanningService deviceScanningService;
    private final LicenseExpirationCheckScheduler licenseExpirationCheckScheduler;
    private final DynDnsUpdateScheduler dynDnsUpdateScheduler;
    private final FilterStatisticsDeleteScheduler filterStatisticsDeleteScheduler;
    private final FilterStatisticsUpdateScheduler filterStatisticsUpdateScheduler;
    private final BlockedDomainsWriteScheduler blockedDomainsWriteScheduler;
    private final UpnpWatchdogScheduler upnpWatchdogScheduler;
    private final BlockerUpdateScheduler blockerUpdateScheduler;

    @Inject
    public BackgroundServices(
            @Named("highPrioScheduledExecutor") ScheduledExecutorService highPrioExecutorService,
            @Named("lowPrioScheduledExecutor") ScheduledExecutorService lowPrioExecutorService,
            @Named("unlimitedCachePoolExecutor") Executor unlimitedCachePoolExecutor,
            DhcpListener dhcpListener,
            DhcpBindListener dhcpBindListener,
            TorController torController,
            SessionPurgerScheduler sessionPurgerScheduler,
            StartupTaskScheduler startupTaskScheduler,
            ArpListener arpListener,
            NeighborDiscoveryListener neighborDiscoveryListener,
            ProblematicRouterDetectionScheduler problematicRouterDetectionScheduler,
            FilterManagerScheduler filterManagerScheduler,
            AutomaticUpdater autoUpdater,
            AppModuleServiceScheduler appModuleServiceScheduler,
            PCAccessRestrictionsServiceScheduler pcAccessRestrictionsServiceScheduler,
            OpenVpnServiceScheduler openVpnServiceScheduler,
            OpenVpnAddressListener openVpnAddressListener,
            DeviceServiceScheduler deviceServiceScheduler,
            TrafficAccounterScheduler trafficAccounterScheduler,
            NetworkInterfaceWatchdog networkInterfaceWatchdog,
            MessageCenterServiceScheduler messageCenterServiceScheduler,
            MalwareUpdateScheduler malwareUpdateScheduler,
            DnsStatisticsScheduler dnsStatisticsScheduler,
            DnsGatewayNamesScheduler dnsGatewayNamesScheduler,
            ZeroconfRegistrationService zeroconfRegistrationService,
            ControlBarAliasUpdater controlBarAliasUpdater,
            IpAdressValidatorScheduler ipAdressValidatorScheduler,
            Ip6MulticastPingScheduler ip6MulticastPingScheduler,
            Ip6NetworkScanScheduler ip6NetworkScanScheduler,
            Ip6RouterAdvertiserScheduler ip6RouterAdvertiserScheduler,
            DeviceScanningService deviceScanningService,
            LicenseExpirationCheckScheduler licenseExpirationCheckScheduler,
            DynDnsUpdateScheduler dynDnsUpdateScheduler,
            FilterStatisticsDeleteScheduler filterStatisticsDeleteScheduler,
            FilterStatisticsUpdateScheduler filterStatisticsUpdateScheduler,
            BlockedDomainsWriteScheduler blockedDomainsWriteScheduler,
            UpnpWatchdogScheduler upnpWatchdogScheduler,
            BlockerUpdateScheduler blockerUpdateScheduler,
            @Named("tor.connection.check.delay") long torDelay) {

        this.highPrioExecutorService = highPrioExecutorService;
        this.lowPrioExecutorService = lowPrioExecutorService;

        this.unlimitedCachePoolExecutor = unlimitedCachePoolExecutor;

        this.filterManagerScheduler = filterManagerScheduler;
        this.sessionPurgerScheduler = sessionPurgerScheduler;
        this.startupTaskScheduler = startupTaskScheduler;
        this.arpListener = arpListener;
        this.neighborDiscoveryListener = neighborDiscoveryListener;
        this.ipAdressValidatorScheduler = ipAdressValidatorScheduler;
        this.ip6MulticastPingScheduler = ip6MulticastPingScheduler;
        this.ip6NetworkScanScheduler = ip6NetworkScanScheduler;
        this.ip6RouterAdvertiserScheduler = ip6RouterAdvertiserScheduler;
        this.problematicRouterDetectionScheduler = problematicRouterDetectionScheduler;
        this.appModuleServiceScheduler = appModuleServiceScheduler;
        this.pcAccessRestrictionsServiceScheduler = pcAccessRestrictionsServiceScheduler;
        this.openVpnServiceScheduler = openVpnServiceScheduler;
        this.openVpnAddressListener = openVpnAddressListener;
        this.deviceServiceScheduler = deviceServiceScheduler;
        this.autoUpdater = autoUpdater;
        this.networkInterfaceWatchdog = networkInterfaceWatchdog;
        this.trafficAccounterScheduler = trafficAccounterScheduler;
        this.messageCenterServiceScheduler = messageCenterServiceScheduler;
        this.malwareUpdateScheduler = malwareUpdateScheduler;
        this.dnsStatisticsScheduler = dnsStatisticsScheduler;
        this.dnsGatewayNamesScheduler = dnsGatewayNamesScheduler;

        this.dhcpListener = dhcpListener;
        this.dhcpBindListener = dhcpBindListener;
        this.torController = torController;

        this.torConnectionCheckDelay = torDelay;
        this.zeroconfRegistrationService = zeroconfRegistrationService;
        this.controlBarAliasUpdater = controlBarAliasUpdater;
        this.deviceScanningService = deviceScanningService;
        this.licenseExpirationCheckScheduler = licenseExpirationCheckScheduler;
        this.dynDnsUpdateScheduler = dynDnsUpdateScheduler;
        this.filterStatisticsDeleteScheduler = filterStatisticsDeleteScheduler;
        this.filterStatisticsUpdateScheduler = filterStatisticsUpdateScheduler;
        this.blockedDomainsWriteScheduler = blockedDomainsWriteScheduler;
        this.upnpWatchdogScheduler = upnpWatchdogScheduler;
        this.blockerUpdateScheduler = blockerUpdateScheduler;
    }

    @SubSystemInit
    public void run() {
        sessionPurgerScheduler.schedule(lowPrioExecutorService);
        startupTaskScheduler.schedule(lowPrioExecutorService);
        appModuleServiceScheduler.schedule(lowPrioExecutorService);
        filterManagerScheduler.schedule(lowPrioExecutorService);

        unlimitedCachePoolExecutor.execute(arpListener);
        unlimitedCachePoolExecutor.execute(neighborDiscoveryListener);
        ipAdressValidatorScheduler.schedule(highPrioExecutorService);
        ip6MulticastPingScheduler.schedule(highPrioExecutorService);
        ip6NetworkScanScheduler.schedule(highPrioExecutorService);
        ip6RouterAdvertiserScheduler.schedule(highPrioExecutorService);
        deviceScanningService.start();

        problematicRouterDetectionScheduler.schedule(lowPrioExecutorService);

        unlimitedCachePoolExecutor.execute(new NamedRunnable(dhcpListener.getClass().getSimpleName(), dhcpListener::run));

        //check if interface 'eth0' gets a new IP address assigned via DHCP and tell NetworkInterfaceWrapper
        unlimitedCachePoolExecutor.execute(dhcpBindListener);

        unlimitedCachePoolExecutor.execute(openVpnAddressListener);

        //start Tor control port connection
        if (torConnectionCheckDelay >= 0) {
            torController.startCheckingConnection(highPrioExecutorService, torConnectionCheckDelay);
        }

        pcAccessRestrictionsServiceScheduler.schedule(highPrioExecutorService);

        // schedule OpenVpnService Cache Cleaner
        openVpnServiceScheduler.schedule(highPrioExecutorService);

        highPrioExecutorService.scheduleAtFixedRate(networkInterfaceWatchdog, 1, 3, TimeUnit.SECONDS);

        // schedule DeviceService refresh
        deviceServiceScheduler.schedule(lowPrioExecutorService);

        // schedule network activity refresh
        trafficAccounterScheduler.schedule(lowPrioExecutorService);

        // schedule message center refresh
        messageCenterServiceScheduler.schedule(lowPrioExecutorService);

        malwareUpdateScheduler.schedule(lowPrioExecutorService);

        //start automatic updating service
        if (autoUpdater != null && autoUpdater.isActivated())
            autoUpdater.start();

        lowPrioExecutorService.execute(new NamedRunnable(zeroconfRegistrationService.getClass().getSimpleName(), zeroconfRegistrationService::registerConsoleService));

        controlBarAliasUpdater.start();

        dnsStatisticsScheduler.schedule(lowPrioExecutorService);
        dnsGatewayNamesScheduler.schedule(lowPrioExecutorService);

        licenseExpirationCheckScheduler.schedule(lowPrioExecutorService);
        dynDnsUpdateScheduler.schedule(lowPrioExecutorService);

        filterStatisticsDeleteScheduler.schedule(lowPrioExecutorService);
        filterStatisticsUpdateScheduler.schedule(lowPrioExecutorService);
        blockedDomainsWriteScheduler.schedule(lowPrioExecutorService);
        upnpWatchdogScheduler.schedule(lowPrioExecutorService);
        blockerUpdateScheduler.schedule(lowPrioExecutorService);
    }

    @SubSystemShutdown
    public void shutdown() {
        zeroconfRegistrationService.unregisterConsoleService();
        STATUS.info("Background services shut down.");
    }
}
