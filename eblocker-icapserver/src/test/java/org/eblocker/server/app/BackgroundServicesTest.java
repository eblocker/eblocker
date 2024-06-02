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

import org.eblocker.server.common.executor.NamedRunnable;
import org.eblocker.server.common.network.ArpListener;
import org.eblocker.server.common.network.DhcpBindListener;
import org.eblocker.server.common.network.DhcpListener;
import org.eblocker.server.common.network.Ip6AddressMonitor;
import org.eblocker.server.common.network.NetworkInterfaceWatchdog;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.network.ZeroconfRegistrationService;
import org.eblocker.server.common.scheduler.AppModuleServiceScheduler;
import org.eblocker.server.common.scheduler.BlockedDomainsWriteScheduler;
import org.eblocker.server.common.scheduler.BlockerUpdateScheduler;
import org.eblocker.server.common.scheduler.ContentFilterUpdateScheduler;
import org.eblocker.server.common.scheduler.DeviceServiceScheduler;
import org.eblocker.server.common.scheduler.DnsGatewayNamesScheduler;
import org.eblocker.server.common.scheduler.DnsStatisticsScheduler;
import org.eblocker.server.common.scheduler.DynDnsUpdateScheduler;
import org.eblocker.server.common.scheduler.FilterManagerScheduler;
import org.eblocker.server.common.scheduler.FilterStatisticsDeleteScheduler;
import org.eblocker.server.common.scheduler.FilterStatisticsUpdateScheduler;
import org.eblocker.server.common.scheduler.Ip6MulticastPingScheduler;
import org.eblocker.server.common.scheduler.Ip6RouterAdvertiserScheduler;
import org.eblocker.server.common.scheduler.IpAdressValidatorScheduler;
import org.eblocker.server.common.scheduler.LicenseExpirationCheckScheduler;
import org.eblocker.server.common.scheduler.MalwareUpdateScheduler;
import org.eblocker.server.common.scheduler.MessageCenterServiceScheduler;
import org.eblocker.server.common.scheduler.OpenVpnServiceScheduler;
import org.eblocker.server.common.scheduler.PCAccessRestrictionsServiceScheduler;
import org.eblocker.server.common.scheduler.ProblematicRouterDetectionScheduler;
import org.eblocker.server.common.scheduler.RecordedDomainsWriteScheduler;
import org.eblocker.server.common.scheduler.SessionPurgerScheduler;
import org.eblocker.server.common.scheduler.StartupTaskScheduler;
import org.eblocker.server.common.scheduler.TrafficAccounterScheduler;
import org.eblocker.server.common.scheduler.UpnpWatchdogScheduler;
import org.eblocker.server.common.update.AutomaticUpdater;
import org.eblocker.server.common.update.ControlBarAliasUpdater;
import org.eblocker.server.http.service.DeviceScanningService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.ScheduledExecutorService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class BackgroundServicesTest {
    private BackgroundServices services;

    private ScheduledExecutorService highPrioExecutorService;
    private ScheduledExecutorService lowPrioExecutorService;
    private ScheduledExecutorService unlimitedCachePoolExecutor;
    private ArpListener arpListener;
    private DhcpListener dhcpListener;
    private DhcpBindListener dhcpBindListener;
    private TorController torController;
    private Ip6AddressMonitor ip6AddressMonitor;
    private DeviceScanningService deviceScanningService;

    @BeforeEach
    void setUp() throws Exception {
        highPrioExecutorService = Mockito.mock(ScheduledExecutorService.class);
        lowPrioExecutorService = Mockito.mock(ScheduledExecutorService.class);
        unlimitedCachePoolExecutor = Mockito.mock(ScheduledExecutorService.class);
        dhcpListener = Mockito.mock(DhcpListener.class);
        dhcpBindListener = Mockito.mock(DhcpBindListener.class);
        arpListener = Mockito.mock(ArpListener.class);
        IpAdressValidatorScheduler ipAdressValidatorScheduler = Mockito.mock(IpAdressValidatorScheduler.class);
        Ip6MulticastPingScheduler ip6MulticastPingScheduler = Mockito.mock(Ip6MulticastPingScheduler.class);
        Ip6RouterAdvertiserScheduler ip6RouterAdvertiserScheduler = Mockito.mock(Ip6RouterAdvertiserScheduler.class);
        torController = Mockito.mock(TorController.class);
        ip6AddressMonitor = Mockito.mock(Ip6AddressMonitor.class);
        PCAccessRestrictionsServiceScheduler contingentEnforcerScheduler = Mockito.mock(PCAccessRestrictionsServiceScheduler.class);
        OpenVpnServiceScheduler openVpnServiceScheduler = Mockito.mock(OpenVpnServiceScheduler.class);
        DeviceServiceScheduler deviceServiceScheduler = Mockito.mock(DeviceServiceScheduler.class);
        TrafficAccounterScheduler trafficAccounterScheduler = Mockito.mock(TrafficAccounterScheduler.class);
        MessageCenterServiceScheduler messageCenterServiceScheduler = Mockito.mock(MessageCenterServiceScheduler.class);
        MalwareUpdateScheduler malwareUpdateScheduler = Mockito.mock(MalwareUpdateScheduler.class);
        NetworkInterfaceWatchdog networkInterfaceWatchdog = Mockito.mock(NetworkInterfaceWatchdog.class);
        ZeroconfRegistrationService zeroconfRegistrationService = Mockito.mock(ZeroconfRegistrationService.class);
        ControlBarAliasUpdater controlBarAliasUpdater = Mockito.mock(ControlBarAliasUpdater.class);
        DnsStatisticsScheduler dnsStatisticsScheduler = Mockito.mock(DnsStatisticsScheduler.class);
        DnsGatewayNamesScheduler dnsGatewayNamesScheduler = Mockito.mock(DnsGatewayNamesScheduler.class);
        deviceScanningService = Mockito.mock(DeviceScanningService.class);
        LicenseExpirationCheckScheduler licenseExpirationCheckScheduler = Mockito.mock(LicenseExpirationCheckScheduler.class);
        DynDnsUpdateScheduler dynDnsUpdateScheduler = Mockito.mock(DynDnsUpdateScheduler.class);
        FilterStatisticsDeleteScheduler filterStatisticsDeleteScheduler = Mockito.mock(FilterStatisticsDeleteScheduler.class);
        FilterStatisticsUpdateScheduler filterStatisticsUpdateScheduler = Mockito.mock(FilterStatisticsUpdateScheduler.class);
        BlockedDomainsWriteScheduler blockedDomainsWriteScheduler = Mockito.mock(BlockedDomainsWriteScheduler.class);
        UpnpWatchdogScheduler upnpWatchdogScheduler = Mockito.mock(UpnpWatchdogScheduler.class);
        BlockerUpdateScheduler blockerUpdateScheduler = Mockito.mock(BlockerUpdateScheduler.class);
        ContentFilterUpdateScheduler contentFilterUpdateScheduler = Mockito.mock(ContentFilterUpdateScheduler.class);

        // Not really used yet:
        SessionPurgerScheduler sessionPurgerScheduler = Mockito.mock(SessionPurgerScheduler.class);
        StartupTaskScheduler startupTaskScheduler = Mockito.mock(StartupTaskScheduler.class);
        ProblematicRouterDetectionScheduler routerDetectionScheduler = Mockito.mock(ProblematicRouterDetectionScheduler.class);
        FilterManagerScheduler filterManagerScheduler = Mockito.mock(FilterManagerScheduler.class);
        AppModuleServiceScheduler appModuleServiceScheduler = Mockito.mock(AppModuleServiceScheduler.class);
        RecordedDomainsWriteScheduler recordedDomainsWriteScheduler = Mockito.mock(RecordedDomainsWriteScheduler.class);
        AutomaticUpdater autoUpdater = null;

        services = new BackgroundServices(
                highPrioExecutorService,
                lowPrioExecutorService,
                unlimitedCachePoolExecutor,
                dhcpListener,
                dhcpBindListener,
                torController,
                ip6AddressMonitor,
                sessionPurgerScheduler,
                startupTaskScheduler,
                arpListener,
                null,
                routerDetectionScheduler,
                filterManagerScheduler,
                autoUpdater,
                appModuleServiceScheduler,
                contingentEnforcerScheduler,
                openVpnServiceScheduler,
                null,
                deviceServiceScheduler,
                trafficAccounterScheduler,
                networkInterfaceWatchdog,
                messageCenterServiceScheduler,
                malwareUpdateScheduler,
                dnsStatisticsScheduler,
                dnsGatewayNamesScheduler,
                zeroconfRegistrationService,
                controlBarAliasUpdater,
                ipAdressValidatorScheduler,
                ip6MulticastPingScheduler,
                ip6RouterAdvertiserScheduler,
                deviceScanningService,
                licenseExpirationCheckScheduler,
                dynDnsUpdateScheduler,
                filterStatisticsDeleteScheduler,
                filterStatisticsUpdateScheduler,
                blockedDomainsWriteScheduler,
                upnpWatchdogScheduler,
                blockerUpdateScheduler,
                recordedDomainsWriteScheduler,
                contentFilterUpdateScheduler,
                10);
    }

    /**
     * Tasks that block (because they subscribe to a Redis channel) should never be
     * executed on the shared executors.
     */
    @Test
    void noBlockingTasksOnSharedExecutors() {
        //given

        //when
        services.run();

        //then
        verify(highPrioExecutorService, never()).execute(arpListener);
        verify(lowPrioExecutorService, never()).execute(arpListener);

        verify(highPrioExecutorService, never()).execute(dhcpListener::run);
        verify(lowPrioExecutorService, never()).execute(dhcpListener::run);

        verify(highPrioExecutorService, never()).execute(dhcpBindListener);
        verify(lowPrioExecutorService, never()).execute(dhcpBindListener);

        verify(highPrioExecutorService, never()).execute(ip6AddressMonitor);
        verify(lowPrioExecutorService, never()).execute(ip6AddressMonitor);
    }

    @Test
    void blockedExecutorsAreOnlyUsedOnce() {
        //given

        //when
        services.run();

        //then
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        verify(unlimitedCachePoolExecutor, Mockito.atLeast(1)).execute(captor.capture());

        assertTrue(captor.getAllValues().stream().anyMatch(r -> r == arpListener));
        assertTrue(captor.getAllValues().stream().anyMatch(r -> r instanceof NamedRunnable && ((NamedRunnable) r).getName().startsWith(DhcpListener.class.getSimpleName())));
    }

    @Test
    void deviceScanningIsStarted() {
        //given

        //when
        services.run();

        //then
        verify(deviceScanningService).start();
    }

    @Test
    void torControllerHasHighPrio() {
        //given

        //when
        services.run();

        //then
        verify(torController).startCheckingConnection(eq(highPrioExecutorService), anyLong());
    }
}
