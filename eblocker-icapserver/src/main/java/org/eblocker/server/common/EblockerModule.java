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
package org.eblocker.server.common;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.inject.Provides;
import com.google.inject.Scopes;
import com.google.inject.Singleton;
import com.google.inject.TypeLiteral;
import com.google.inject.assistedinject.FactoryModuleBuilder;
import com.google.inject.matcher.AbstractMatcher;
import com.google.inject.matcher.Matchers;
import com.google.inject.name.Named;
import com.google.inject.name.Names;
import com.google.inject.spi.InjectionListener;
import com.google.inject.spi.TypeEncounter;
import com.google.inject.spi.TypeListener;
import io.netty.channel.ChannelHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import org.eblocker.server.app.BackgroundServices;
import org.eblocker.server.app.DeviceProperties;
import org.eblocker.server.common.blacklist.DomainBlacklistService;
import org.eblocker.server.common.blacklist.DomainBlockingNetworkService;
import org.eblocker.server.common.blacklist.DomainBlockingService;
import org.eblocker.server.common.blacklist.RequestHandler;
import org.eblocker.server.common.blocker.BlockerService;
import org.eblocker.server.common.blocker.UpdateTaskFactory;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.data.dns.DnsDataSource;
import org.eblocker.server.common.data.dns.JedisDnsDataSource;
import org.eblocker.server.common.data.events.DataSourceEventLogger;
import org.eblocker.server.common.data.events.EventDataSource;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.JedisEventDataSource;
import org.eblocker.server.common.data.statistic.BlockedDomainsStatisticService;
import org.eblocker.server.common.data.statistic.FilterStatisticsDataSource;
import org.eblocker.server.common.data.statistic.JedisFilterStatisticsDataSource;
import org.eblocker.server.common.executor.LoggingExecutorService;
import org.eblocker.server.common.malware.MalwareFilterService;
import org.eblocker.server.common.network.ArpSpoofer;
import org.eblocker.server.common.network.ArpSweeper;
import org.eblocker.server.common.network.DhcpBindListener;
import org.eblocker.server.common.network.DhcpServer;
import org.eblocker.server.common.network.NetworkInterfaceWatchdog;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.network.NetworkStateMachine;
import org.eblocker.server.common.network.PacketLogger;
import org.eblocker.server.common.network.ProblematicRouterDetection;
import org.eblocker.server.common.network.TelnetConnection;
import org.eblocker.server.common.network.TelnetConnectionImpl;
import org.eblocker.server.common.network.TorController;
import org.eblocker.server.common.network.TorExitNodeCountries;
import org.eblocker.server.common.network.TrafficAccounter;
import org.eblocker.server.common.network.unix.EblockerDnsServer;
import org.eblocker.server.common.network.unix.IpSetConfig;
import org.eblocker.server.common.network.unix.IpSets;
import org.eblocker.server.common.network.unix.IscDhcpServer;
import org.eblocker.server.common.network.unix.NetworkInterfaceAliases;
import org.eblocker.server.common.network.unix.NetworkServicesUnix;
import org.eblocker.server.common.network.unix.PacketLoggerUnix;
import org.eblocker.server.common.openvpn.OpenVpnChannelFactory;
import org.eblocker.server.common.openvpn.OpenVpnClientFactory;
import org.eblocker.server.common.openvpn.OpenVpnProfileFiles;
import org.eblocker.server.common.openvpn.OpenVpnService;
import org.eblocker.server.common.openvpn.RoutingController;
import org.eblocker.server.common.openvpn.VpnKeepAliveFactory;
import org.eblocker.server.common.openvpn.configuration.OpenVpnConfigurationParser;
import org.eblocker.server.common.openvpn.server.OpenVpnAddressListener;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.pubsub.JedisPubSubService;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.registration.DeviceRegistrationClient;
import org.eblocker.server.common.registration.DeviceRegistrationLicenseState;
import org.eblocker.server.common.registration.DeviceRegistrationLicenseStateImpl;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.common.service.FeatureServicePublisher;
import org.eblocker.server.common.service.FeatureServiceSubscriber;
import org.eblocker.server.common.service.FilterStatisticsService;
import org.eblocker.server.common.squid.SquidConfigController;
import org.eblocker.server.common.squid.SquidWarningService;
import org.eblocker.server.common.squid.acl.SquidAclModule;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.ssl.SslTestRequestHandler;
import org.eblocker.server.common.ssl.SslTestRequestHandlerFactory;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.startup.SubSystemUsageInterceptor;
import org.eblocker.server.common.status.StartupStatusReporter;
import org.eblocker.server.common.system.CommandRunner;
import org.eblocker.server.common.system.CpuInfo;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.common.system.unix.CommandRunnerUnix;
import org.eblocker.server.common.system.unix.CpuInfoUnix;
import org.eblocker.server.common.system.unix.ScriptRunnerUnix;
import org.eblocker.server.common.transaction.TransactionCache;
import org.eblocker.server.common.update.AutomaticUpdater;
import org.eblocker.server.common.update.DebianUpdater;
import org.eblocker.server.common.update.SystemUpdater;
import org.eblocker.server.http.controller.AnonymousController;
import org.eblocker.server.http.controller.AppWhitelistModuleController;
import org.eblocker.server.http.controller.AuthenticationController;
import org.eblocker.server.http.controller.BlockerController;
import org.eblocker.server.http.controller.ConfigurationBackupController;
import org.eblocker.server.http.controller.ConnectionCheckController;
import org.eblocker.server.http.controller.ControlBarController;
import org.eblocker.server.http.controller.CustomDomainFilterConfigController;
import org.eblocker.server.http.controller.CustomerInfoController;
import org.eblocker.server.http.controller.DashboardCardController;
import org.eblocker.server.http.controller.DeviceController;
import org.eblocker.server.http.controller.DeviceRegistrationController;
import org.eblocker.server.http.controller.DnsController;
import org.eblocker.server.http.controller.DomainBlockingController;
import org.eblocker.server.http.controller.DomainRecorderController;
import org.eblocker.server.http.controller.DomainWhiteListController;
import org.eblocker.server.http.controller.EventController;
import org.eblocker.server.http.controller.FactoryResetController;
import org.eblocker.server.http.controller.FeatureController;
import org.eblocker.server.http.controller.FeatureToggleController;
import org.eblocker.server.http.controller.FilterController;
import org.eblocker.server.http.controller.FilterStatisticsController;
import org.eblocker.server.http.controller.LanguageController;
import org.eblocker.server.http.controller.LedSettingsController;
import org.eblocker.server.http.controller.MessageCenterController;
import org.eblocker.server.http.controller.MobileConnectionCheckController;
import org.eblocker.server.http.controller.MobileDnsCheckController;
import org.eblocker.server.http.controller.NetworkController;
import org.eblocker.server.http.controller.OpenVpnController;
import org.eblocker.server.http.controller.OpenVpnServerController;
import org.eblocker.server.http.controller.PageContextController;
import org.eblocker.server.http.controller.ParentalControlController;
import org.eblocker.server.http.controller.ParentalControlFilterListsController;
import org.eblocker.server.http.controller.ProductMigrationController;
import org.eblocker.server.http.controller.RecordingController;
import org.eblocker.server.http.controller.RedirectController;
import org.eblocker.server.http.controller.ReminderController;
import org.eblocker.server.http.controller.SSLController;
import org.eblocker.server.http.controller.SettingsController;
import org.eblocker.server.http.controller.SetupWizardController;
import org.eblocker.server.http.controller.SplashController;
import org.eblocker.server.http.controller.TasksController;
import org.eblocker.server.http.controller.TimestampController;
import org.eblocker.server.http.controller.TimezoneController;
import org.eblocker.server.http.controller.TosController;
import org.eblocker.server.http.controller.TransactionRecorderController;
import org.eblocker.server.http.controller.UpdateController;
import org.eblocker.server.http.controller.UserAgentController;
import org.eblocker.server.http.controller.UserController;
import org.eblocker.server.http.controller.wrapper.ControllerWrapperFactory;
import org.eblocker.server.http.security.DashboardAuthorizationProcessor;
import org.eblocker.server.http.server.EblockerHttpsServer;
import org.eblocker.server.http.server.SSLContextHandler;
import org.eblocker.server.http.service.AccessDeniedRequestHandler;
import org.eblocker.server.http.service.AccessDeniedService;
import org.eblocker.server.http.service.AnonymousService;
import org.eblocker.server.http.service.AppModuleService;
import org.eblocker.server.http.service.AutoTrustAppService;
import org.eblocker.server.http.service.DashboardCardService;
import org.eblocker.server.http.service.DashboardService;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.MessageCenterService;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.eblocker.server.http.service.ParentalControlAccessRestrictionsService;
import org.eblocker.server.http.service.ParentalControlEnforcerService;
import org.eblocker.server.http.service.ParentalControlFilterListsService;
import org.eblocker.server.http.service.ParentalControlSearchEngineConfigService;
import org.eblocker.server.http.service.ParentalControlService;
import org.eblocker.server.http.service.ParentalControlUsageService;
import org.eblocker.server.http.service.ProductInfoService;
import org.eblocker.server.http.service.RegistrationServiceAvailabilityCheck;
import org.eblocker.server.http.service.SSLWhitelistService;
import org.eblocker.server.http.service.ShutdownExecutorService;
import org.eblocker.server.http.service.SystemStatusService;
import org.eblocker.server.http.service.UserService;
import org.eblocker.server.icap.filter.FilterManager;
import org.eblocker.server.icap.filter.bpjm.BpjmFilterService;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.eblocker.server.icap.server.EblockerIcapServer;
import org.eblocker.server.icap.transaction.TransactionProcessorsModule;
import org.eblocker.server.upnp.NetworkAddressFactoryCustomization;
import org.eblocker.server.upnp.UpnpActionCallbackFactory;
import org.eblocker.server.upnp.UpnpActionInvocationFactory;
import org.eblocker.server.upnp.UpnpPortForwardingAddFactory;
import org.eblocker.server.upnp.UpnpPortForwardingDeleteFactory;
import org.fourthline.cling.DefaultUpnpServiceConfiguration;
import org.fourthline.cling.UpnpService;
import org.fourthline.cling.UpnpServiceImpl;
import org.fourthline.cling.transport.impl.NetworkAddressFactoryImpl;
import org.fourthline.cling.transport.spi.NetworkAddressFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.Charset;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Module for dependency injection of eBlocker https + icap server.
 * Provides an executor service
 * for background tasks.
 */
public class EblockerModule extends BaseModule {

    private static final Logger LOG = LoggerFactory.getLogger(EblockerModule.class);

    public EblockerModule() throws IOException {
    }

    @Override
    protected void configure() {
        bindListener(Matchers.any(), new TypeListener() {
            @Override
            public <I> void hear(TypeLiteral<I> type, TypeEncounter<I> encounter) {
                LOG.debug("hear: {}", type);
                encounter.register(new InjectionListener<I>() {
                    @Override
                    public void afterInjection(I injectee) {
                        LOG.debug("injected: {}", injectee.getClass().getName());
                    }
                });
            }
        });

        super.configure();

        bind(PubSubService.class).to(JedisPubSubService.class);
        bind(FilterManager.class).in(Scopes.SINGLETON);
        bind(PageContextStore.class).in(Scopes.SINGLETON);
        bind(TransactionCache.class).in(Scopes.SINGLETON);
        bind(NetworkServices.class).to(NetworkServicesUnix.class).in(Scopes.SINGLETON);
        bind(NetworkStateMachine.class).in(Scopes.SINGLETON);
        bind(IpSets.class).in(Scopes.SINGLETON);
        bind(MalwareFilterService.class).in(Scopes.SINGLETON);
        bind(TrafficAccounter.class).in(Scopes.SINGLETON);
        bind(DeviceRegistrationProperties.class).in(Scopes.SINGLETON);
        bind(DeviceRegistrationClient.class).in(Scopes.SINGLETON);
        bind(DeviceRegistrationLicenseState.class).to(DeviceRegistrationLicenseStateImpl.class);
        bind(DhcpServer.class).to(IscDhcpServer.class);
        bind(DhcpBindListener.class);
        bind(ScriptRunner.class).to(ScriptRunnerUnix.class);
        bind(CommandRunner.class).to(CommandRunnerUnix.class);
        bind(PacketLogger.class).to(PacketLoggerUnix.class);
        bind(SystemUpdater.class).to(DebianUpdater.class).in(Scopes.SINGLETON);
        bind(AutomaticUpdater.class).in(Scopes.SINGLETON);
        bind(TorController.class).in(Scopes.SINGLETON);
        bind(CpuInfo.class).to(CpuInfoUnix.class);
        bind(SslService.class).in(Scopes.SINGLETON);
        bind(SSLContextHandler.class).in(Scopes.SINGLETON);
        bind(EblockerHttpsServer.class).in(Scopes.SINGLETON);
        bind(NetworkInterfaceWrapper.class).in(Scopes.SINGLETON);
        bind(NetworkInterfaceAliases.class).in(Scopes.SINGLETON);
        bind(NetworkInterfaceWatchdog.class);
        bind(SquidConfigController.class).in(Scopes.SINGLETON);
        bind(SquidWarningService.class).in(Scopes.SINGLETON);
        bind(AutoTrustAppService.class).in(Scopes.SINGLETON);
        bind(SSLWhitelistService.class).in(Scopes.SINGLETON);
        bind(FeatureServicePublisher.class).in(Scopes.SINGLETON);
        bind(FeatureServiceSubscriber.class).in(Scopes.SINGLETON);
        bind(PauseDeviceController.class).in(Scopes.SINGLETON);
        bind(ArpSweeper.class).in(Scopes.SINGLETON);
        bind(ProblematicRouterDetection.class).in(Scopes.SINGLETON);
        bind(ArpSpoofer.class).in(Scopes.SINGLETON);
        bind(TelnetConnection.class).to(TelnetConnectionImpl.class);
        bind(DeviceProperties.class).in(Scopes.SINGLETON);
        bind(DomainBlacklistService.class).in(Scopes.SINGLETON);
        bind(DomainBlockingNetworkService.class).in(Scopes.SINGLETON);
        bind(AnonymousService.class).in(Scopes.SINGLETON);
        bind(OpenVpnService.class).in(Scopes.SINGLETON);
        bind(OpenVpnConfigurationParser.class);
        bind(OpenVpnProfileFiles.class).in(Scopes.SINGLETON);
        bind(OpenVpnAddressListener.class).in(Scopes.SINGLETON);
        bind(RoutingController.class).in(Scopes.SINGLETON);
        bind(StartupStatusReporter.class).in(Scopes.SINGLETON);
        bind(ParentalControlService.class).in(Scopes.SINGLETON);
        bind(ParentalControlFilterListsService.class).in(Scopes.SINGLETON);
        bind(ParentalControlAccessRestrictionsService.class).in(Scopes.SINGLETON);
        bind(ParentalControlEnforcerService.class).in(Scopes.SINGLETON);
        bind(ParentalControlSearchEngineConfigService.class);
        bind(AccessDeniedService.class).in(Scopes.SINGLETON);
        bind(DomainBlockingService.class).in(Scopes.SINGLETON);
        bind(EventLogger.class).to(DataSourceEventLogger.class).in(Scopes.SINGLETON);
        bind(EventDataSource.class).to(JedisEventDataSource.class);
        bind(BackgroundServices.class).in(Scopes.SINGLETON);
        bind(DashboardService.class).in(Scopes.SINGLETON);
        bind(DashboardCardService.class).in(Scopes.SINGLETON);
        bind(EblockerIcapServer.class).in(Scopes.SINGLETON);
        bind(DnsDataSource.class).to(JedisDnsDataSource.class).in(Scopes.SINGLETON);
        bind(FilterStatisticsDataSource.class).to(JedisFilterStatisticsDataSource.class).in(Scopes.SINGLETON);
        bind(FilterStatisticsService.class);
        bind(ChannelHandler.class).annotatedWith(Names.named("DomainBlockingRequestHandler")).to(RequestHandler.class);
        bind(ChannelHandler.class).annotatedWith(Names.named("AccessDeniedRequestHandler")).to(AccessDeniedRequestHandler.class);
        bind(AppModuleService.class);
        bind(MessageCenterService.class);
        bind(BlockedDomainsStatisticService.class);
        bind(TorExitNodeCountries.class);
        bind(BpjmFilterService.class);
        bind(BlockerService.class);

        bind(OpenVpnServerService.class);
        bind(EblockerDnsServer.class);
        bind(DeviceService.class);
        bind(ParentalControlUsageService.class);
        bind(ProductInfoService.class);
        bind(UserService.class);
        bind(RegistrationServiceAvailabilityCheck.class);

        install(new FactoryModuleBuilder().build(OpenVpnChannelFactory.class));
        install(new FactoryModuleBuilder().build(OpenVpnClientFactory.class));
        install(new FactoryModuleBuilder().build(VpnKeepAliveFactory.class));
        install(new FactoryModuleBuilder().build(UpnpPortForwardingAddFactory.class));
        install(new FactoryModuleBuilder().build(UpnpPortForwardingDeleteFactory.class));
        install(new FactoryModuleBuilder().build(UpnpActionCallbackFactory.class));
        install(new FactoryModuleBuilder().build(UpnpActionInvocationFactory.class));
        install(new FactoryModuleBuilder().implement(ChannelHandler.class, SslTestRequestHandler.class).build(SslTestRequestHandlerFactory.class));
        install(new FactoryModuleBuilder().build(UpdateTaskFactory.class));

        install(new TransactionProcessorsModule());
        install(new SquidAclModule());

        bindInterceptor(Matchers.annotatedWith(SubSystemService.class), Matchers.any(), new SubSystemUsageInterceptor(getProvider(SystemStatusService.class)));

        binder().convertToTypes(new AbstractMatcher<TypeLiteral<?>>() {
            @Override
            public boolean matches(TypeLiteral<?> typeLiteral) {
                return IpAddress.class.isAssignableFrom(typeLiteral.getRawType());
            }
        }, (value, toType) -> IpAddress.parse(value));
    }

    /**
     * A clock for the recording service
     */
    @Provides
    public Clock provideClock() {
        return Clock.systemUTC();
    }

    /**
     * A clock that is in the default time zone
     *
     * @return
     */
    @Provides
    @Named("localClock")
    public Clock provideLocalClock() {
        return Clock.systemDefaultZone();
    }

    /**
     * Use this scheduled executor for long running and possibly CPU intensive tasks
     *
     * @return
     */
    @Provides
    @Named("lowPrioScheduledExecutor")
    @Singleton
    public ScheduledExecutorService provideLowPrioScheduledExecutor(ShutdownExecutorService shutdownExecutorService) {
        ScheduledExecutorService result = Executors.newScheduledThreadPool(2);
        shutdownExecutorService.addExecutorService(result);
        return new LoggingExecutorService("lowPrioScheduledExecutor", result);
    }

    /**
     * Use this scheduled executor for short tasks that are relatively urgent but take
     * at most a few seconds to complete
     *
     * @return
     */
    @Provides
    @Named("highPrioScheduledExecutor")
    @Singleton
    public ScheduledExecutorService provideHighPrioScheduledExecutor(ShutdownExecutorService shutdownExecutorService) {
        ScheduledExecutorService result = Executors.newScheduledThreadPool(3);
        shutdownExecutorService.addExecutorService(result);
        return new LoggingExecutorService("highPrioScheduledExecutor", result);
    }

    @Provides
    @Named("unlimitedCachePoolExecutor")
    @Singleton
    public Executor provideUnlimitedCachePoolExecutor(@Named("unlimitedCachePoolExecutorService") ExecutorService executorService) {
        return executorService;
    }

    @Provides
    @Named("unlimitedCachePoolExecutorService")
    @Singleton
    public ExecutorService provideUnlimitedCachePoolExecutorService(ShutdownExecutorService shutdownExecutorService) {
        ExecutorService result = Executors.newCachedThreadPool();
        shutdownExecutorService.addExecutorService(result);
        return new LoggingExecutorService("unlimitedCachePoolExecutor", result);
    }

    @Provides
    @Named("toolbarInlayTemplate")
    public String provideToolbarInlayTemplate() {
        String name = "toolbarInlayTemplate";
        String path = getProperty("resource.toolbarInlayTemplate.path");
        String charsetName = getProperty("toolbarInlayTemplate.resource.charset");
        if (charsetName == null) {
            charsetName = "UTF-8";
        }
        return ResourceHandler.load(new SimpleResource(name, path, Charset.forName(charsetName)));
    }

    @Provides
    @Named("toolbarInlayMinJs")
    public String provideToolbarInlayMinifiedJs() {
        String name = "toolbarInlayMinJs";
        String path = getProperty("resource.toolbarInlayMinJs.path");
        String charsetName = getProperty("toolbarInlayTemplate.resource.charset");
        if (charsetName == null) {
            charsetName = "UTF-8";
        }
        return ResourceHandler.load(new SimpleResource(name, path, Charset.forName(charsetName)));
    }

    @Provides
    @Named("toolbarInlayMinCss")
    public String provideToolbarInlayMinifiedCss() {
        String name = "toolbarInlayMinCss";
        String path = getProperty("resource.toolbarInlayMinCss.path");
        String charsetName = getProperty("toolbarInlayTemplate.resource.charset");
        if (charsetName == null) {
            charsetName = "UTF-8";
        }
        return ResourceHandler.load(new SimpleResource(name, path, Charset.forName(charsetName)));
    }

    @Provides
    @Named("toolbarYoutubeInlayTemplate")
    public String provideToolbarYoutubeInlayTemplate() {
        String name = "toolbarYoutubeInlayTemplate";
        String path = getProperty("resource.toolbarInlayTemplate.youtube.path");
        String charsetName = getProperty("toolbarInlayTemplate.resource.charset");
        if (charsetName == null) {
            charsetName = "UTF-8";
        }
        return ResourceHandler.load(new SimpleResource(name, path, Charset.forName(charsetName)));
    }

    @Provides
    @Named("arpProbeCache")
    @Singleton
    public ConcurrentMap<String, Long> provideArpProbeCache() {
        return new ConcurrentHashMap<>(64, 0.75f, 1);
    }

    @Provides
    @Named("arpResponseTable")
    @Singleton
    public Table<String, IpAddress, Long> provideArpResponseTable() {
        return HashBasedTable.create();
    }

    @Provides
    @Named("http.server.aliases.map")
    public Map<String, String> httpAliasesMap() {
        Map<String, String> map = new HashMap<>();
        String httpAliases = getProperty("http.server.aliases");
        String httpAliasPrefix = getProperty("http.server.prefix.aliases");
        if (httpAliases != null) {
            String prefix = "http.server.alias.";
            String[] aliases = httpAliases.split("\\s");
            for (String alias : aliases) {
                String regex = getProperty(prefix + alias + ".regex");
                String path = getProperty(prefix + alias + ".path");
                map.put(regex, httpAliasPrefix + path);
            }
        }
        return map;
    }

    @Provides
    @Named("malware.filter.ipset")
    @Singleton
    public IpSetConfig provideMalwareFilterIpSet(@Named("malware.filter.ipset.name") String name,
                                                 @Named("malware.filter.ipset.type") String type,
                                                 @Named("malware.filter.ipset.maxSize") int maxSize) {
        return new IpSetConfig(name, type, maxSize);
    }

    /**
     * This method constructs a customized NetworkAddressFactory. NetworkAddressFactoryImpl is not subclassed explicitly
     * because it calls the overridden methods in its constructor so setting member variables is not possible. Actual
     * customization is moved to an extra class for testability: {@link NetworkAddressFactoryCustomization}
     */
    @Provides
    @Singleton
    public UpnpService provideUpnpService(NetworkAddressFactoryCustomization customization) {
        return new UpnpServiceImpl(new DefaultUpnpServiceConfiguration() {
            @Override
            protected NetworkAddressFactory createNetworkAddressFactory(int streamListenPort) {
                return new NetworkAddressFactoryImpl(streamListenPort) {
                    @Override
                    protected boolean isUsableAddress(NetworkInterface networkInterface, InetAddress address) {
                        return customization.isUsableAddress(networkInterface.getName(), address);
                    }

                    @Override
                    protected boolean isUsableNetworkInterface(NetworkInterface iface) {
                        return customization.isUsableNetworkInterface(iface.getName());
                    }
                };
            }
        });
    }

    @Provides
    @Singleton
    @Named("nettyBossEventGroupLoop")
    public NioEventLoopGroup provideNettyBossEventGroupLoop(@Named("unlimitedCachePoolExecutor") Executor executor) {
        return new NioEventLoopGroup(2, executor);
    }

    @Provides
    @Singleton
    @Named("nettyWorkerEventGroupLoop")
    public NioEventLoopGroup provideNettyWorkerEventGroupLoop(@Named("unlimitedCachePoolExecutor") Executor executor) {
        return new NioEventLoopGroup(2 * Runtime.getRuntime().availableProcessors(), executor);
    }

    @Provides
    @Singleton
    public AnonymousController anonymousController() {
        return ControllerWrapperFactory.wrap(AnonymousController.class);
    }

    @Provides
    @Singleton
    public AppWhitelistModuleController appWhitelistModuleController() {
        return ControllerWrapperFactory.wrap(AppWhitelistModuleController.class);
    }

    @Provides
    @Singleton
    public AuthenticationController authenticationController() {
        return ControllerWrapperFactory.wrap(AuthenticationController.class);
    }

    @Provides
    @Singleton
    public ControlBarController controlBarController() {
        return ControllerWrapperFactory.wrap(ControlBarController.class);
    }

    @Provides
    @Singleton
    public DeviceController deviceController() {
        return ControllerWrapperFactory.wrap(DeviceController.class);
    }

    @Provides
    @Singleton
    public DeviceRegistrationController deviceRegistrationController() {
        return ControllerWrapperFactory.wrap(DeviceRegistrationController.class);
    }

    @Provides
    @Singleton
    public DnsController dnsController() {
        return ControllerWrapperFactory.wrap(DnsController.class);
    }

    @Provides
    @Singleton
    public DomainBlockingController domainBlacklistController() {
        return ControllerWrapperFactory.wrap(DomainBlockingController.class);
    }

    @Provides
    @Singleton
    public DomainWhiteListController domainWhiteListController() {
        return ControllerWrapperFactory.wrap(DomainWhiteListController.class);
    }

    @Provides
    @Singleton
    public DashboardCardController dashboardCardController() {
        return ControllerWrapperFactory.wrap(DashboardCardController.class);
    }

    @Provides
    @Singleton
    public CustomerInfoController customerInfoController() {
        return ControllerWrapperFactory.wrap(CustomerInfoController.class);
    }

    @Provides
    @Singleton
    public EventController eventController() {
        return ControllerWrapperFactory.wrap(EventController.class);
    }

    @Provides
    @Singleton
    public FactoryResetController factoryResetController() {
        return ControllerWrapperFactory.wrap(FactoryResetController.class);
    }

    @Provides
    @Singleton
    public FeatureController featureController() {
        return ControllerWrapperFactory.wrap(FeatureController.class);
    }

    @Provides
    @Singleton
    public FeatureToggleController featureToggleController() {
        return ControllerWrapperFactory.wrap(FeatureToggleController.class);
    }

    @Provides
    @Singleton
    public MobileConnectionCheckController mobileConnectionCheckController() {
        return ControllerWrapperFactory.wrap(MobileConnectionCheckController.class);
    }

    @Provides
    @Singleton
    public MobileDnsCheckController mobileDnsCheckController() {
        return ControllerWrapperFactory.wrap(MobileDnsCheckController.class);
    }

    @Provides
    @Singleton
    public FilterController filterController() {
        return ControllerWrapperFactory.wrap(FilterController.class);
    }

    @Provides
    @Singleton
    public LanguageController languageController() {
        return ControllerWrapperFactory.wrap(LanguageController.class);
    }

    @Provides
    @Singleton
    public MessageCenterController messageCenterController() {
        return ControllerWrapperFactory.wrap(MessageCenterController.class);
    }

    @Provides
    @Singleton
    public NetworkController networkController() {
        return ControllerWrapperFactory.wrap(NetworkController.class);
    }

    @Provides
    @Singleton
    public OpenVpnController openVpnController() {
        return ControllerWrapperFactory.wrap(OpenVpnController.class);
    }

    @Provides
    @Singleton
    public OpenVpnServerController openVpnServerController() {
        return ControllerWrapperFactory.wrap(OpenVpnServerController.class);
    }

    @Provides
    @Singleton
    public PageContextController pageContextController() {
        return ControllerWrapperFactory.wrap(PageContextController.class);
    }

    @Provides
    @Singleton
    public ParentalControlController parentalControlController() {
        return ControllerWrapperFactory.wrap(ParentalControlController.class);
    }

    @Provides
    @Singleton
    public ParentalControlFilterListsController parentalControlFilterListsController() {
        return ControllerWrapperFactory.wrap(ParentalControlFilterListsController.class);
    }

    @Provides
    @Singleton
    public RecordingController recordingController() {
        return ControllerWrapperFactory.wrap(RecordingController.class);
    }

    @Provides
    @Singleton
    public RedirectController redirectController() {
        return ControllerWrapperFactory.wrap(RedirectController.class);
    }

    @Provides
    @Singleton
    public ReminderController reminderController() {
        return ControllerWrapperFactory.wrap(ReminderController.class);
    }

    @Provides
    @Singleton
    public SplashController splashController() {
        return ControllerWrapperFactory.wrap(SplashController.class);
    }

    @Provides
    @Singleton
    public SSLController sslController() {
        return ControllerWrapperFactory.wrap(SSLController.class);
    }

    @Provides
    @Singleton
    public ProductMigrationController productMigrationController() {
        return ControllerWrapperFactory.wrap(ProductMigrationController.class);
    }

    @Provides
    @Singleton
    public SettingsController settingsController() {
        return ControllerWrapperFactory.wrap(SettingsController.class);
    }

    @Provides
    @Singleton
    public SetupWizardController setupWizardController() {
        return ControllerWrapperFactory.wrap(SetupWizardController.class);
    }

    @Provides
    @Singleton
    public TimestampController timestampController() {
        return ControllerWrapperFactory.wrap(TimestampController.class);
    }

    @Provides
    @Singleton
    public TimezoneController timezoneController() {
        return ControllerWrapperFactory.wrap(TimezoneController.class);
    }

    @Provides
    @Singleton
    public TransactionRecorderController transactionRecorderController() {
        return ControllerWrapperFactory.wrap(TransactionRecorderController.class);
    }

    @Provides
    @Singleton
    public DomainRecorderController domainRecorderController() {
        return ControllerWrapperFactory.wrap(DomainRecorderController.class);
    }

    @Provides
    @Singleton
    public UpdateController updateController() {
        return ControllerWrapperFactory.wrap(UpdateController.class);
    }

    @Provides
    @Singleton
    public UserAgentController userAgentController() {
        return ControllerWrapperFactory.wrap(UserAgentController.class);
    }

    @Provides
    @Singleton
    public UserController userController() {
        return ControllerWrapperFactory.wrap(UserController.class);
    }

    @Provides
    @Singleton
    public TosController tosController() {
        return ControllerWrapperFactory.wrap(TosController.class);
    }

    @Provides
    @Singleton
    public CustomDomainFilterConfigController customDomainFilterController() {
        return ControllerWrapperFactory.wrap(CustomDomainFilterConfigController.class);
    }

    @Provides
    @Singleton
    public FilterStatisticsController filterStatisticsController() {
        return ControllerWrapperFactory.wrap(FilterStatisticsController.class);
    }

    @Provides
    @Singleton
    public ConfigurationBackupController configurationBackupController() {
        return ControllerWrapperFactory.wrap(ConfigurationBackupController.class);
    }

    @Provides
    @Singleton
    public LedSettingsController ledSettingsController() {
        return ControllerWrapperFactory.wrap(LedSettingsController.class);
    }

    @Provides
    @Singleton
    public TasksController tasksController() {
        return ControllerWrapperFactory.wrap(TasksController.class);
    }

    @Provides
    @Singleton
    public ConnectionCheckController connectionCheckController() {
        return ControllerWrapperFactory.wrap(ConnectionCheckController.class);
    }

    @Provides
    @Singleton
    public BlockerController blockerController() {
        return ControllerWrapperFactory.wrap(BlockerController.class);
    }

    /**
     * The DashboardAuthorizationProcessor is not really a Controller,
     * but it must be wrapped by the ControllerWrapperFactory,
     * because it needs to access the DeviceService.
     * @return
     */
    @Provides
    @Singleton
    public DashboardAuthorizationProcessor dashboardAuthorizationProcessor() {
        return ControllerWrapperFactory.wrap(DashboardAuthorizationProcessor.class);
    }
}
