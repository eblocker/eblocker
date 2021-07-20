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
package org.eblocker.server.http.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.name.Named;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.ssl.ClientAuth;
import io.netty.handler.ssl.JdkSslContext;
import io.netty.handler.ssl.SslContext;
import org.eblocker.server.common.BaseModule;
import org.eblocker.server.common.data.IpAddressModule;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.ServiceNotAvailableException;
import org.eblocker.server.common.network.BaseURLs;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.startup.SubSystemShutdown;
import org.eblocker.server.common.status.StartupStatusReporter;
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
import org.eblocker.server.http.controller.boot.DiagnosticsReportController;
import org.eblocker.server.http.controller.boot.SystemStatusController;
import org.eblocker.server.http.exceptions.restexpress.ServiceNotAvailableServiceException;
import org.eblocker.server.http.security.DashboardAuthorizationProcessor;
import org.eblocker.server.http.security.SecurityProcessor;
import org.restexpress.Request;
import org.restexpress.RestExpress;
import org.restexpress.pipeline.Preprocessor;
import org.restexpress.response.RawResponseWrapper;
import org.restexpress.serialization.NullSerializationProvider;
import org.restexpress.serialization.SerializationProvider;
import org.restexpress.serialization.json.JacksonJsonProcessor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;

/**
 * This RestExpress server provides a REST JSON API for the toolbar
 * and admin interface.
 * <p>
 * It also serves static files.
 * <p>
 * The server responds to both http and https requests. If setting up the SSL context
 * has failed, however, only http requests are processed. This allows the user to see
 * error logs and probably reset the unit to a usable state.
 */
@SubSystemService(SubSystem.HTTP_SERVER)
@SuppressWarnings("squid:S1192")
public class EblockerHttpsServer implements Preprocessor {
    private static final Logger log = LoggerFactory.getLogger(EblockerHttpsServer.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private RestExpress server;
    private final int httpPort;
    private final int httpsPort;

    private final StaticFileController staticFileController;
    private final DiagnosticsReportController diagnosticsReportController;
    private final SystemStatusController systemStatusController;

    private final DeviceController deviceController;
    private final RedirectController redirectController;
    private final NetworkController networkController;
    private final ParentalControlController parentalControlController;
    private final FilterController filterController;
    private final UserAgentController userAgentController;
    private final AnonymousController anonymousController;
    private final DomainWhiteListController domainWhiteListController;
    private final DeviceRegistrationController deviceRegistrationController;
    private final FactoryResetController factoryResetController;
    private final UpdateController updateController;
    private final SSLController sslController;
    private SSLContextHandler sslContextHandler;
    private final RecordingController recordingController;
    private final LanguageController languageController;
    private final SetupWizardController setupWizardController;
    private final TimezoneController timezoneController;
    private final MessageCenterController messageCenterController;
    private final AppWhitelistModuleController appModulesController;
    private final AuthenticationController authenticationController;
    private final OpenVpnController openVpnController;
    private final OpenVpnServerController openVpnServerController;
    private final ControlBarController controlBarController;
    private final SplashController splashController;
    private StartupStatusReporter startupStatusReporter;
    private BaseURLs baseUrls;
    private final ParentalControlFilterListsController filterListsController;
    private final TimestampController timestampController;
    private final TransactionRecorderController transactionRecorderController;
    private final UserController userController;
    private final DomainBlockingController domainBlockingController;
    private final EventController eventController;
    private final SettingsController settingsController;
    private final PageContextController pageContextController;
    private final ReminderController reminderController;
    private final DnsController dnsController;
    private final ProductMigrationController productMigrationController;
    private final DashboardCardController dashboardCardController;
    private final CustomerInfoController customerInfoController;
    private final FeatureController featureController;
    private final MobileConnectionCheckController mobileConnectionCheckController;
    private final MobileDnsCheckController mobileDnsCheckController;
    private final TosController tosController;
    private final FilterStatisticsController filterStatisticsController;
    private final ConfigurationBackupController configBackupController;
    private final CustomDomainFilterConfigController customDomainFilterConfigController;
    private final LedSettingsController ledSettingsController;
    private final TasksController tasksController;
    private final FeatureToggleController featureToggleController;
    private final ConnectionCheckController connectionCheckController;
    private final BlockerController blockerController;
    private final DomainRecorderController domainRecorderController;

    private Channel httpsChannel;

    @Inject
    public EblockerHttpsServer(@Named("httpPort") int httpPort,
                               @Named("httpsPort") int httpsPort,
                               @Named("http.server.useSystemOut") boolean useSystemOut,

                               SecurityProcessor securityProcessor,
                               DashboardAuthorizationProcessor dashboardAuthorizationProcessor,
                               StartupStatusReporter startupStatusReporter,
                               BaseURLs baseUrls,

                               // Controllers that are available during boot phase
                               StaticFileController staticFileController,
                               DiagnosticsReportController diagnosticsReportController,
                               SystemStatusController systemStatusController,
                               SSLContextHandler sslContextHandler,

                               // Controllers that are NOT available during boot phase
                               AnonymousController anonymousController,
                               AppWhitelistModuleController appModulesController,
                               AuthenticationController authenticationController,
                               ControlBarController controlBarController,
                               SplashController splashController,
                               DeviceController deviceController,
                               DeviceRegistrationController deviceRegistrationController,
                               DnsController dnsController,
                               DomainBlockingController domainBlockingController,
                               DomainWhiteListController domainWhiteListController,
                               EventController eventController,
                               FactoryResetController factoryResetController,
                               FeatureToggleController featureToggleController,
                               FilterController filterController,
                               LanguageController languageController,
                               MessageCenterController messageCenterController,
                               NetworkController networkController,
                               OpenVpnController openVpnController,
                               OpenVpnServerController openVpnServerController,
                               PageContextController pageContextController,
                               ParentalControlController parentalControlController,
                               ParentalControlFilterListsController filterListsController,
                               RecordingController recordingController,
                               RedirectController redirectController,
                               ReminderController reminderController,
                               SSLController sslController,
                               SettingsController settingsController,
                               SetupWizardController setupWizardController,
                               TimestampController timestampController,
                               TimezoneController timezoneController,
                               TransactionRecorderController transactionRecorderController,
                               UpdateController updateController,
                               UserAgentController userAgentController,
                               UserController userController,
                               ProductMigrationController productMigrationController,
                               DashboardCardController dashboardCardController,
                               CustomerInfoController customerInfoController,
                               FeatureController featureController,
                               MobileConnectionCheckController mobileConnectionCheckController,
                               MobileDnsCheckController mobileDnsCheckController,
                               TosController tosController,
                               FilterStatisticsController filterStatisticsController,
                               ConfigurationBackupController configBackupController,
                               CustomDomainFilterConfigController customDomainFilterConfigController,
                               LedSettingsController ledSettingsController,
                               TasksController tasksController,
                               ConnectionCheckController connectionCheckController,
                               BlockerController blockerController,
                               DomainRecorderController domainRecorderController
    ) {
        // Set up SerializationProvider to make sure special characters like "&"
        // are transported to the frontend without encoding
        // Adding processors mimicks the functionality of DefaultSerializationProvider
        SerializationProvider serializationProvider = new NullSerializationProvider();
        serializationProvider.add(new JacksonJsonProcessor(false) {
            @Override
            protected void initializeMapper(ObjectMapper mapper) {
                super.initializeMapper(mapper);
                mapper.registerModule(new JavaTimeModule());
                mapper.registerModule(new IpAddressModule());
                mapper.enable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            }
        }, new RawResponseWrapper(), true);

        RestExpress.setDefaultSerializationProvider(serializationProvider);
        server = new RestExpress()
                .setUseSystemOut(useSystemOut)
                .addPreprocessor(this)
                .addPreprocessor(securityProcessor)
                .addPreprocessor(dashboardAuthorizationProcessor)
                .addPostprocessor(new CacheControlPostProcessor())
                .addFinallyProcessor(new ExceptionLogger())
                .setExecutorThreadCount(2 * Runtime.getRuntime().availableProcessors())
                .noCompression()
                .mapException(ServiceNotAvailableException.class, ServiceNotAvailableServiceException.class);

        this.httpPort = httpPort;
        this.httpsPort = httpsPort;

        this.staticFileController = staticFileController;
        this.diagnosticsReportController = diagnosticsReportController;
        this.systemStatusController = systemStatusController;
        this.sslContextHandler = sslContextHandler;

        this.deviceController = deviceController;
        this.redirectController = redirectController;
        this.networkController = networkController;
        this.parentalControlController = parentalControlController;
        this.userController = userController;
        this.domainBlockingController = domainBlockingController;
        this.filterController = filterController;
        this.userAgentController = userAgentController;
        this.anonymousController = anonymousController;
        this.domainWhiteListController = domainWhiteListController;
        this.deviceRegistrationController = deviceRegistrationController;
        this.factoryResetController = factoryResetController;
        this.updateController = updateController;
        this.sslController = sslController;
        this.recordingController = recordingController;
        this.featureToggleController = featureToggleController;

        this.languageController = languageController;
        this.setupWizardController = setupWizardController;
        this.timezoneController = timezoneController;
        this.timestampController = timestampController;

        this.messageCenterController = messageCenterController;
        this.appModulesController = appModulesController;

        this.openVpnController = openVpnController;
        this.openVpnServerController = openVpnServerController;

        this.controlBarController = controlBarController;

        this.splashController = splashController;

        this.startupStatusReporter = startupStatusReporter;
        this.baseUrls = baseUrls;
        this.filterListsController = filterListsController;

        this.transactionRecorderController = transactionRecorderController;
        this.eventController = eventController;
        this.settingsController = settingsController;
        this.pageContextController = pageContextController;
        this.reminderController = reminderController;
        this.dnsController = dnsController;
        this.productMigrationController = productMigrationController;

        this.authenticationController = authenticationController;

        this.dashboardCardController = dashboardCardController;
        this.customerInfoController = customerInfoController;

        this.featureController = featureController;
        this.mobileConnectionCheckController = mobileConnectionCheckController;
        this.mobileDnsCheckController = mobileDnsCheckController;

        this.tosController = tosController;
        this.filterStatisticsController = filterStatisticsController;
        this.configBackupController = configBackupController;
        this.customDomainFilterConfigController = customDomainFilterConfigController;

        this.ledSettingsController = ledSettingsController;
        this.tasksController = tasksController;
        this.connectionCheckController = connectionCheckController;
        this.blockerController = blockerController;

        this.domainRecorderController = domainRecorderController;

        setUpRoutes();


        //observe ssl context for changes
        sslContextHandler.addContextChangeListener(new SSLContextHandler.SslContextChangeListener() {
            @Override
            public void onEnable() {
                updateSSLContext();
            }

            @Override
            public void onDisable() {
                unbindHttpsChannel();
            }
        });
    }

    /**
     * Binds to the http and https ports and starts the server. This method never returns.
     */
    @SubSystemInit
    public void run() throws Throwable {
        log.info("Binding on http port {}", httpPort);
        server.bind(httpPort);
        startupStatusReporter.portConnected("HTTP", baseUrls.getHttpURL());
    }

    @SubSystemShutdown
    public void stop() {
        server.shutdown();
        STATUS.info("HTTP server stopped");
    }

    /**
     * Call this method to inform the HTTP server that its IP address changed.
     * This will generate a new SSL certificate for the HTTPS server and use this from now on.
     */
    private void updateSSLContext() {
        SSLContext newSSLContext = sslContextHandler.getSSLContext();
        if (newSSLContext != null) {
            SslContext nettySslContext = new JdkSslContext(newSSLContext, false, ClientAuth.NONE);
            log.info("Refreshing SSLContext of HTTPS server...and rebinding.");
            server.setSSLContext(nettySslContext);
            boolean httpsUpdate = httpsChannel != null;
            if (httpsUpdate) {
                unbindHttpsChannel();
            }
            log.info("Binding on https port {}", httpsPort);
            try {
                httpsChannel = server.bind(httpsPort);
            } catch (Throwable t) {
                log.error("failed to bind to {}", httpsPort, t);
                return;
            }
            if (!httpsUpdate) {
                startupStatusReporter.portConnected("HTTPS", baseUrls.getHttpsURL());
            }
        }
    }

    private void unbindHttpsChannel() {
        if (httpsChannel != null) {
            log.info("Unbinding open HTTPS port");
            httpsChannel.close().awaitUninterruptibly();
        }
    }

    public static void main(String[] args) throws Throwable {
        Injector injector = Guice.createInjector(new BaseModule());
        injector.getInstance(EblockerHttpsServer.class).run();
    }

    private void setUpRoutes() {
        addFilterRoutes();
        addDeviceRoutes();
        addRedirectRoutes();
        addNetworkRoutes();
        addParentalControlRoutes();
        addDomainWhiteListRoutes();
        addDeviceRegistrationRoutes();
        addSSLRoutes();

        addTimestampRoutes();
        addUserMessagesRoutes();

        addVpnRoutes();

        addControlBarRoutes();

        addAdminConsoleRoutes();

        addAdvicePageRoutes();

        addErrorPageRoutes();

        addFilterListsRoutes();
        addTransactionRecorderRoutes();

        addDashboardRoutes();
        addAdminDashboardRoutes();

        addPageContextRoutes();
        addReminderRoutes();
        addConfigurationBackupRoutes();
        addFeatureToggleRoutes();
        addConnectionCheckRoutes();
        addBlockerRoutes();

        // This must be the last route (catch all):
        server
                .regex("/.*", staticFileController)
                .method(HttpMethod.GET)
                .name("static.route")
                .noSerialization();
    }

    private void addVpnRoutes() {
        server
                .uri("/anonymous/vpn/profiles", openVpnController)
                .action("getProfiles", HttpMethod.GET)
                .name("controlbar.vpn.profiles.get.route");// Called from controlbar

        server
                .uri("/anonymous/vpn/profiles/status/{device}", openVpnController)
                .action("getVpnStatusByDevice", HttpMethod.GET)
                .name("errorpage.vpn.profiles.get.status.device");// Called from controlbar and squid error page

        server
                .uri("/anonymous/vpn/profile/{id}/status", openVpnController)
                .action("getVpnStatus", HttpMethod.GET)
                .name("controlbar.vpn.profile.get.status");// Called from controlbar

        server // Similar to the one below - but used in a different place
                .uri("/anonymous/vpn/profile/{id}/status/{device}", openVpnController)
                .action("setVpnDeviceStatus", HttpMethod.PUT)
                .name("controlbar.vpn.profile.status.device.set");// Called from controlbar

        server // Similar to the one above - but used in a different place
                .uri("/anonymous/vpn/profile/{id}/status-this", openVpnController)
                .action("setVpnThisDeviceStatus", HttpMethod.PUT)
                .name("errorpageExclusive.vpn.profile.status.device.set");// Called from squid error page
    }

    private void addTimestampRoutes() {
        server
                .uri("/api/localtimestamp", timestampController)
                .action("getLocalTimestamp", HttpMethod.GET)
                .name("dashboard.timestamp.route");

    }

    private void addSSLRoutes() {
        server
                .uri("/api/ssl/caCertificate.crt", sslController)
                .action("getCACertificateByteStream", HttpMethod.GET)
                .noSerialization()
                .name("public.ssl.root.ca.download.route");

        server
                .uri("/api/ssl/firefox/caCertificate.crt", sslController)
                .action("getCACertificateByteStreamForFirefox", HttpMethod.GET)
                .noSerialization()
                .name("public.ssl.root.ca.download.firefox.route");

        server
                .uri("/api/ssl/firefox/renewalCertificate.crt", sslController)
                .action("getRenewalCertificateByteStreamForFirefox", HttpMethod.GET)
                .noSerialization()
                .name("public.ssl.renewal.download.firefox.route");

        server
                .uri("/api/ssl/renewalCertificate.crt", sslController)
                .action("getRenewalCertificateByteStream", HttpMethod.GET)
                .noSerialization()
                .name("public.ssl.root.renewal.download.route");
        //TODO: Is this really required for the ControlBar?
        server
                .uri("/ssl/status", sslController)
                .action("getSSLState", HttpMethod.GET)
                .name("controlbar.ssl.status.get.route");

        server
                .uri("/ssl/whitelist", sslController)
                .action("addUrlToSSLWhitelist", HttpMethod.POST)
                .name("errorpageExclusive.whitelist.set.route");// Available only for errorpage and console, not for controlbar

        server.uri("/ssl/test/{serialNumber}", sslController)
                .action("markCertificateStatus", HttpMethod.POST)
                .name("public.ssl.root.ca.test");

        server
                .uri("/api/ssl/device/status", sslController)
                .action("setDeviceStatus", HttpMethod.POST)
                .name("dashboard.sll.device.status.post");

        server
                .uri("/api/ssl/status", sslController)
                .action("getSslDashboardStatus", HttpMethod.GET)
                .name("dashboard.ssl.status.get");

        server
                .uri("/api/ssl/errors", sslController)
                .action("getFailedConnections", HttpMethod.GET)
                .name("ssl.failed.connections.get");

        server
                .uri("/api/ssl/errors", sslController)
                .action("clearFailedConnections", HttpMethod.DELETE)
                .name("ssl.failed.connections.delete");

        server
                .uri("/api/ssl/errors/recording", sslController)
                .action("getErrorRecordingEnabled", HttpMethod.GET)
                .name("ssl.error.recording.enabled.get");

        server
                .uri("/api/ssl/errors/recording", sslController)
                .action("setErrorRecordingEnabled", HttpMethod.PUT)
                .name("ssl.error.recording.enabled.set");
    }

    private void addDeviceRegistrationRoutes() {
        // public (required to load the console)
        server
                .uri("/registration", deviceRegistrationController)
                .action("registrationStatus", HttpMethod.GET)
                .name("public.registration.status.route");
    }

    private void addNetworkRoutes() {
        server
                .uri("/network/setupPageInfo", networkController)
                .action("getSetupPageInfo", HttpMethod.GET)
                .name("public.network.config.setuppage.get.route");
    }

    private void addParentalControlRoutes() {
        server
                .uri("/api/squiderror/searchEngineConfig", parentalControlController)
                .action("getSearchEngineConfiguration", HttpMethod.GET)
                .name("errorpage.parentalcontrol.searchEngine.get");
    }

    private void addFilterRoutes() {
        // dashboard
        server
                .uri("/summary/whitelist/config", filterController)
                .action("getConfig", HttpMethod.GET)
                .name("dashboard.filter.config.route");

        // dashboard
        server
                .uri("/summary/whitelist/config", filterController)
                .action("putConfig", HttpMethod.PUT)
                .name("dashboard.filter.save.config.route");
    }

    private void addDeviceRoutes() {
        // controlbar: Used by controlbar to find current device.
        server
                .uri("/devices", deviceController)
                .action("getAllDevices", HttpMethod.GET)
                .name("controlbar.devices.route");

        server
                .uri("/api/device/icon", deviceController)
                .action("getIconSettings", HttpMethod.GET)
                .name("dashboard.icon.get.route");

        server
                .uri("/api/device/icon", deviceController)
                .action("resetIconSettings", HttpMethod.DELETE)
                .name("dashboard.icon.reset.route");

        server
                .uri("/api/device/icon", deviceController)
                .action("setIconSettings", HttpMethod.POST)
                .name("dashboard.icon.set.route");

        server
                .uri("/api/device/iconpos/{iconPos}", deviceController)
                .action("setIconPosition", HttpMethod.POST)
                .name("public.icon.position.set.route");
    }

    private void addRedirectRoutes() {
        //
        // Redirects must be public.
        // They are protected by the transaction UUID.
        //
        server
                .uri("/redirect/{decision}/{uuid}", redirectController)
                .method(HttpMethod.GET)
                .name("public.redirect.route");

        server
                .uri("/redirect/{decision}/{uuid}", redirectController)
                .method(HttpMethod.PUT)
                .name("public.redirect.store.route");

        server
                //.uri("/redirect/{domain}", redirectController)
                //.method(HttpMethod.DELETE)
                .uri("/redirect-delete/{domain}", redirectController)
                .action("delete", HttpMethod.GET)  //  Easier to test
                .name("public.redirect.delete.route");

        // Used by 1px.svg
        server.uri("/redirect/prepare", redirectController)
                .action("prepare", HttpMethod.GET)
                .name("public.redirect.prepare.route");
    }

    private void addDomainWhiteListRoutes() {
        // dashboard
        server
                .uri("/summary/whitelist/all", domainWhiteListController)
                .action("getWhitelist", HttpMethod.GET)
                .name("public.whitelist.get.route");

        // dashboard
        server
                .uri("/summary/whitelist/all", domainWhiteListController)
                .action("setWhitelist", HttpMethod.PUT)
                .name("public.whitelist.set.route");

        // dashboard
        server
                .uri("/summary/whitelist/update", domainWhiteListController)
                .action("updateWhitelistEntry", HttpMethod.PUT)
                .name("public.whitelist.update.route");
    }

    private void addUserMessagesRoutes() {
        // controlbar and dashboard
        server
                .uri("/messages", messageCenterController)
                .action("getMessages", HttpMethod.GET)
                .name("public.messages.get.route");

        // public (loaded early in the controlbar where no token verification takes place yet)
        server
                .uri("/messages/count", messageCenterController)
                .action("getNumberOfMessages", HttpMethod.GET)
                .name("public.messages.count.route");

        // controlbar and dashboard!
        server
                .uri("/api/icon/state", controlBarController)
                .action("getIconState", HttpMethod.GET)
                .name("public.messages.iconState.route");

        // controlbar and dashboard
        server
                .uri("/messages/action", messageCenterController)
                .action("executeMessageAction", HttpMethod.POST)
                .name("public.messages.action.route");

        // controlbar and dashboard
        server
                .uri("/messages/hide", messageCenterController)
                .action("hideMessage", HttpMethod.POST)
                .name("public.messages.hide.route");

        // controlbar and dashboard
        server
                .uri("/messages/donotshowagain", messageCenterController)
                .action("setDoNotShowAgain", HttpMethod.PUT)
                .name("public.messages.donotshowagain.put.route");

    }

    private void addAdminConsoleRoutes() {
        // ** New Adminconsole: Settings status routes
        server
                .uri("/api/adminconsole/settings", settingsController)
                .action("getLocaleSettings", HttpMethod.GET)
                .name("public.locale.get.route"); // public, so that private window can load settings
        server
                .uri("/api/adminconsole/settings", settingsController)
                .action("setLocale", HttpMethod.PUT)
                .name("adminconsole.locale.put.route");

        // ** New Adminconsole: System status routes
        server
                .uri("/api/adminconsole/systemstatus/shutdown", systemStatusController)
                .action("shutdown", HttpMethod.POST)
                .name("adminconsole.systemstatus.shutdown");

        server
                .uri("/api/adminconsole/systemstatus/reboot", systemStatusController)
                .action("reboot", HttpMethod.POST)
                .name("adminconsole.systemstatus.reboot");

        //
        // Must be public routes, as these are used, before the system is really started.
        // In particular, before the DB and the admin password are available.
        // So it's impossible to authenticate the user!
        //
        // The shutdown/reboot APIs MUST check that the system is not fully booted,
        // but is in ERROR state instead.
        // If not in ERROR state, the requests MUST be rejected.
        //
        server
                .uri("/api/adminconsole/systemstatus", systemStatusController)
                .action("get", HttpMethod.GET)
                .name("public.adminconsole.systemstatus.get");

        server
                .uri("/api/adminconsole/systemstatus/shutdown/onerror", systemStatusController)
                .action("shutdownOnError", HttpMethod.POST)
                .name("public.adminconsole.systemstatus.shutdown");

        server
                .uri("/api/adminconsole/systemstatus/reboot/onerror", systemStatusController)
                .action("rebootOnError", HttpMethod.POST)
                .name("public.adminconsole.systemstatus.reboot");

        server
                .uri("/api/adminconsole/authentication/token/{appContext}", authenticationController)
                .action("generateConsoleToken", HttpMethod.GET)
                .name("public.token.get.route");

        // FIXME hpe: has to be public, so we should reuse the existing one .. (and change path to /api/..)
        server
                .uri("/api/adminconsole/authentication/login/{appContext}", authenticationController)
                .action("login", HttpMethod.POST)
                .name("public.console.authentication.login.route");

        // Only accessible with valid token (pw must have been entered for adminconsole)
        server
                .uri("/api/adminconsole/authentication/renew/{appContext}", authenticationController)
                .action("renewToken", HttpMethod.GET)
                .name("adminconsole.authentication.renew.route");

        server
                .uri("/api/adminconsole/authentication/enable", authenticationController)
                .action("enable", HttpMethod.POST)
                .name("adminconsole.authentication.enable.route");

        // Only used from within console
        server
                .uri("/api/adminconsole/authentication/disable", authenticationController)
                .action("disable", HttpMethod.POST)
                .name("adminconsole.authentication.disable.route");

        // Must be available without authentication, if user lost password
        server
                .uri("/api/adminconsole/authentication/initiateReset", authenticationController)
                .action("initiateReset", HttpMethod.POST)
                .name("adminconsole.authentication.initiateReset.route")
                .flag(SecurityProcessor.NO_AUTHENTICATION_REQUIRED);

        // Must be available without authentication, if user lost password
        server
                .uri("/api/adminconsole/authentication/executeReset", authenticationController)
                .action("executeReset", HttpMethod.POST)
                .name("adminconsole.authentication.executeReset.route")
                .flag(SecurityProcessor.NO_AUTHENTICATION_REQUIRED);

        // Must be available without authentication, if user lost password
        server
                .uri("/api/adminconsole/authentication/cancelReset", authenticationController)
                .action("cancelReset", HttpMethod.POST)
                .name("adminconsole.authentication.cancelReset.route")
                .flag(SecurityProcessor.NO_AUTHENTICATION_REQUIRED);

        // Must be available without authentication, if user lost password
        server
                .uri("/api/adminconsole/authentication/wait", authenticationController)
                .action("passwordEntryInSeconds", HttpMethod.GET)
                .name("adminconsole.authentication.wait.route")
                .flag(SecurityProcessor.NO_AUTHENTICATION_REQUIRED);

        // ** New Adminconsole: General, to get ProductInfo, to get tos container
        server
                .uri("/api/adminconsole/registration", deviceRegistrationController)
                .action("registrationStatus", HttpMethod.GET)
                .name("adminconsole.registration.status.route");
        server
                .uri("/api/adminconsole/tos", tosController)
                .action("getTos", HttpMethod.GET)
                .name("adminconsole.license.get.tos.route");

        // ** New Adminconsole: license registration
        server
                .uri("/api/adminconsole/registration", deviceRegistrationController)
                .action("register", HttpMethod.POST)
                .name("adminconsole.registration.register.route");
        server
                .uri("/api/adminconsole/registration", deviceRegistrationController)
                .action("resetRegistration", HttpMethod.DELETE)
                .name("adminconsole.registration.reset.route");

        // ** New Adminconsole: upsell info
        server
                .uri("/api/adminconsole/upsellInfo/{feature}", productMigrationController)
                .action("getUpsellInfo", HttpMethod.GET)
                .name("adminconsole.productmigration.getUpsellInfo");

        // ** New Adminconsole: license setup wizard
        server
                .uri("/api/adminconsole/setup/info", setupWizardController)
                .action("getInfo", HttpMethod.GET)
                .name("adminconsole.setup.info.route");
        server
                .uri("/api/adminconsole/setup/serial/checkformat", setupWizardController)
                .action("checkSerialNumber", HttpMethod.PUT)
                .name("adminconsole.setup.serial.check.route");

        // ** New Adminconsole: update
        server
                .uri("/api/adminconsole/updates/status", updateController)
                .action("getUpdatingStatus", HttpMethod.GET)
                .name("adminconsole.updates.status.get.route");
        server
                .uri("/api/adminconsole/updates/autoupdate", updateController)
                .action("getAutoUpdateInformation", HttpMethod.GET)
                .name("adminconsole.updates.autoupdate.get.route");
        server
                .uri("/api/adminconsole/updates/automaticUpdatesStatus", updateController)
                .action("setAutomaticUpdatesStatus", HttpMethod.POST)
                .name("adminconsole.updates.autoupdate.set.route");
        server
                .uri("/api/adminconsole/updates/status", updateController)
                .action("setUpdatingStatus", HttpMethod.POST)
                .name("adminconsole.updates.status.set.route");
        //        server
        //            .uri("/api/adminconsole/updates/download", updateController)
        //            .action("downloadUpdates", HttpMethod.GET)
        //            .name("adminconsole.updates.download.set.route");
        server
                .uri("/api/adminconsole/updates/check", updateController)
                .action("getUpdatesCheckStatus", HttpMethod.GET)
                .name("adminconsole.updates.check.set.route");
        server
                .uri("/api/adminconsole/updates/automaticUpdatesConfig", updateController)
                .action("setAutomaticUpdatesConfig", HttpMethod.POST)
                .name("adminconsole.updates.autoupdate.config.set.route");

        // ** New Adminconsole: Users
        server
                .uri("/api/adminconsole/users", userController)
                .action("getUsers", HttpMethod.GET)
                .name("adminconsole.get.users.route");// Used in error page
        server
                .uri("/api/adminconsole/users", userController)
                .action("createUser", HttpMethod.POST)
                .name("adminconsole.parentalcontrol.store.user.route");
        server
                .uri("/api/adminconsole/users", userController)
                .action("updateUser", HttpMethod.PUT)
                .name("adminconsole.parentalcontrol.put.user.route");
        server
                .uri("/api/adminconsole/users/dashboard/update/{id}", userController)
                .action("updateUserDashboardView", HttpMethod.PUT)
                .name("adminconsole.parentalcontrol.update.user.dashboard.route");
        server
                .uri("/api/adminconsole/users/dashboard/updateall", userController)
                .action("updateDashboardViewOfAllDefaultSystemUsers", HttpMethod.PUT)
                .name("adminconsole.parentalcontrol.update.all.dashboard.route");
        server
                .uri("/api/adminconsole/users/{id}", userController)
                .action("deleteUser", HttpMethod.DELETE)
                .name("adminconsole.parentalcontrol.delete.user.route");
        server
                .uri("/api/adminconsole/users/all", userController)
                .action("deleteAllUsers", HttpMethod.POST)
                .name("adminconsole.parentalcontrol.delete.all.user.route");
        server
                .uri("/api/adminconsole/users/unique", userController)
                .action("isUnique", HttpMethod.GET)
                .name("adminconsole.parentalcontrol.user.unique.route");
        server
                .uri("/api/adminconsole/users/{id}/pin", userController)
                .action("setPin", HttpMethod.POST)
                .name("adminconsole.parentalcontrol.user.set.pin.route");
        server
                .uri("/api/adminconsole/users/{id}/pin", userController)
                .action("resetPin", HttpMethod.DELETE)
                .name("adminconsole.parentalcontrol.user.reset.pin.route");

        // ** New Adminconsole: UserProfiles
        server
                .uri("/api/adminconsole/userprofiles", parentalControlController)
                .action("storeNewProfile", HttpMethod.POST)
                .name("adminconsole.store.profile.route");
        server
                .uri("/api/adminconsole/userprofiles", parentalControlController)
                .action("getProfiles", HttpMethod.GET)
                .name("adminconsole.parentalcontrol.get.profiles.route");
        server
                .uri("/api/adminconsole/userprofiles", parentalControlController)
                .action("updateProfile", HttpMethod.PUT)
                .name("adminconsole.put.profile.route");
        server
                .uri("/api/adminconsole/userprofiles/{id}", parentalControlController)
                .action("deleteProfile", HttpMethod.DELETE)
                .name("adminconsole.delete.profile.route");
        server
                .uri("/api/adminconsole/userprofiles/all", parentalControlController)
                .action("deleteAllProfiles", HttpMethod.POST)
                .name("adminconsole.delete.all.profile.route");
        server
                .uri("/api/adminconsole/userprofiles/unique", parentalControlController)
                .action("isUnique", HttpMethod.GET)
                .name("adminconsole.unique.profil.route");
        server
                .uri("/api/adminconsole/userprofiles/updates", parentalControlController)
                .action("getProfilesBeingUpdated", HttpMethod.GET)
                .name("adminconsole.profile.updates.route");
        server
                .uri("/api/adminconsole/userprofile/bonustime/{id}", parentalControlController)
                .action("addOnlineTimeForToday", HttpMethod.POST)
                .name("adminconsole.userprofile.set.bonustime.route");
        server
                .uri("/api/adminconsole/userprofile/bonustime/{id}", parentalControlController)
                .action("resetBonusTimeForToday", HttpMethod.DELETE)
                .name("adminconsole.userprofile.delete.bonustime.route");

        // ** New Adminconsole: Devices
        server
                .uri("/api/adminconsole/devices", deviceController)
                .action("getAllDevices", HttpMethod.GET)
                .name("adminconsole.devices.route");
        server
                .uri("/api/adminconsole/devices/scan", deviceController)
                .action("isScanningAvailable", HttpMethod.GET)
                .name("adminconsole.devices.scan.available.route");
        server
                .uri("/api/adminconsole/devices/scan", deviceController)
                .action("scanDevices", HttpMethod.POST)
                .name("adminconsole.devices.scan.route");
        server
                .uri("/api/adminconsole/devices/scanningInterval", deviceController)
                .action("getScanningInterval", HttpMethod.GET)
                .name("adminconsole.devices.get.scanning.interval");
        server
                .uri("/api/adminconsole/devices/scanningInterval", deviceController)
                .action("setScanningInterval", HttpMethod.POST)
                .name("adminconsole.devices.set.scanning.interval");

        server
                .uri("/api/adminconsole/devices/autoEnableNewDevices", deviceController)
                .action("isAutoEnableNewDevices", HttpMethod.GET)
                .name("adminconsole.devices.get.auto.enable.new.devices");

        server
                .uri("/api/adminconsole/devices/autoEnableNewDevices", deviceController)
                .action("setAutoEnableNewDevices", HttpMethod.POST)
                .name("adminconsole.devices.set.auto.enable.new.devices");

        server
                .uri("/api/adminconsole/devices/autoEnableNewDevicesAfterActivation", deviceController)
                .action("setAutoEnableNewDevicesAndResetExisting", HttpMethod.POST)
                .name("adminconsole.devices.set.auto.enable.new.devices.after.activation");

        server
                .uri("/api/adminconsole/devices/{deviceId}", deviceController)
                .action("getDeviceById", HttpMethod.GET)
                .name("adminconsole.device.get.by.id.route");
        server
                .uri("/api/adminconsole/devices/{deviceId}", deviceController)
                .action("updateDevice", HttpMethod.PUT)
                .name("adminconsole.devices.update.route");
        server
                .uri("/api/adminconsole/devices/{deviceId}", deviceController)
                .action("deleteDevice", HttpMethod.DELETE)
                .name("adminconsole.devices.delete.route");
        server
                .uri("/api/adminconsole/devices/reset/{deviceId}", deviceController)
                .action("resetDevice", HttpMethod.PUT)
                .name("adminconsole.devices.reset.route");

        // ** New Adminconsole: SSL
        server
                .uri("/api/adminconsole/ssl/status", sslController)
                .action("getSSLState", HttpMethod.GET)
                .name("adminconsole.ssl.status.get.route");
        server
                .uri("/api/adminconsole/ssl/status", sslController)
                .action("setSSLState", HttpMethod.POST)
                .name("adminconsole.ssl.status.set.route");
        server
                .uri("/api/adminconsole/ssl/status/renewal", sslController)
                .action("getSslDashboardStatus", HttpMethod.GET)
                .name("adminconsole.ssl.status.renewal.get");
        server
                .uri("/api/adminconsole/ssl/rootca", sslController)
                .action("createNewRootCA", HttpMethod.POST)
                .name("adminconsole.ssl.root.ca.set.route");
        server
                .uri("/api/adminconsole/ssl/rootca", sslController)
                .action("getRootCaCertificate", HttpMethod.GET)
                .name("adminconsole.ssl.root.ca.get");
        server.uri("/api/adminconsole/ssl/rootca/options", sslController)
                .action("getDefaultCaOptions", HttpMethod.GET)
                .name("adminconsole.ssl.root.ca.options");
        server
                .uri("/api/adminconsole/ssl/certs/status", sslController)
                .action("areCertificatesReady", HttpMethod.GET)
                .name("adminconsole.ssl.certs.status.get.route");
        server
                .uri("/api/adminconsole/ssl/whitelist", sslController)
                .action("addUrlToSSLWhitelist", HttpMethod.POST)
                .name("adminconsole.whitelist.set.route");
        server
                .uri("/api/adminconsole/ssl/errors", sslController)
                .action("getFailedConnections", HttpMethod.GET)
                .name("adminconsole.failed.connections.get");
        server
                .uri("/api/adminconsole/ssl/errors", sslController)
                .action("clearFailedConnections", HttpMethod.DELETE)
                .name("adminconsole.failed.connections.delete");
        server
                .uri("/api/adminconsole/ssl/errors/recording", sslController)
                .action("getErrorRecordingEnabled", HttpMethod.GET)
                .name("adminconsole.error.recording.enabled.get");
        server
                .uri("/api/adminconsole/ssl/errors/recording", sslController)
                .action("setErrorRecordingEnabled", HttpMethod.PUT)
                .name("adminconsole.error.recording.enabled.set");

        // ** New Adminconsole: Trusted apps
        server
                .uri("/api/adminconsole/trustedapps/id", appModulesController)
                .action("create", HttpMethod.POST)
                .name("adminconsole.app.modules.post.route");
        server
                .uri("/api/adminconsole/trustedapps/id/{id}", appModulesController)
                .action("read", HttpMethod.GET)
                .name("adminconsole.app.modules.get.route");
        server
                .uri("/api/adminconsole/trustedapps/id/{id}", appModulesController)
                .action("update", HttpMethod.PUT)
                .name("adminconsole.app.modules.put.route");
        server
                .uri("/api/adminconsole/trustedapps/id/{id}", appModulesController)
                .action("delete", HttpMethod.DELETE)
                .name("adminconsole.app.modules.delete.route");
        server
                .uri("/api/adminconsole/trustedapps/all", appModulesController)
                .action("getAppWhitelistModules", HttpMethod.GET)
                .name("adminconsole.app.modules.getall.route");
        server
                .uri("/api/adminconsole/trustedapps/enable", appModulesController)
                .action("enableAppWhitelistModule", HttpMethod.PUT)
                .name("adminconsole.app.modules.enable.route");
        server
                .uri("/api/adminconsole/trustedapps/unique", appModulesController)
                .action("isUnique", HttpMethod.GET)
                .name("adminconsole.app.modules.get.unique.route");

        // ** New Adminconsole: Trusted Domains
        server
                .uri("/api/adminconsole/trusteddomains/onlyenabled", appModulesController)
                .action("getOnlyEnabledAppWhitelistModules", HttpMethod.GET)
                .name("adminconsole.app.modules.get.enabled.route");
        server
                .uri("/api/adminconsole/trusteddomains/delete", sslController)
                .action("removeWhitelistedUrl", HttpMethod.PUT)
                .name("adminconsole.ssl.whitelist.delete.route");
        server
                .uri("/api/adminconsole/trusteddomains/deleteall", sslController)
                .action("removeAllWhitelistedUrl", HttpMethod.PUT)
                .name("adminconsole.ssl.whitelist.delete.all.route");

        // ** New Adminconsole: DNS
        server
                .uri("/api/adminconsole/dns/config/resolvers", dnsController)
                .action("getDnsResolvers", HttpMethod.GET)
                .name("adminconsole.dns.config.resolvers.get");
        server
                .uri("/api/adminconsole/dns/config/resolvers", dnsController)
                .action("setDnsResolvers", HttpMethod.PUT)
                .name("adminconsole.dns.config.resolvers.set");
        server
                .uri("/api/adminconsole/dns/config/records", dnsController)
                .action("getLocalDnsRecords", HttpMethod.GET)
                .name("adminconsole.dns.config.records.get");
        server
                .uri("/api/adminconsole/dns/config/records", dnsController)
                .action("setLocalDnsRecords", HttpMethod.PUT)
                .name("adminconsole.dns.config.records.set");
        server
                .uri("/api/adminconsole/dns/cache", dnsController)
                .action("flushCache", HttpMethod.DELETE)
                .name("adminconsole.dns.cache.flush");
        server
                .uri("/api/adminconsole/dns/status", dnsController)
                .action("getStatus", HttpMethod.GET)
                .name("adminconsole.dns.status.get");
        server
                .uri("/api/adminconsole/dns/status", dnsController)
                .action("setStatus", HttpMethod.PUT)
                .name("adminconsole.dns.status.set");
        server
                .uri("/api/adminconsole/dns/stats", dnsController)
                .action("getResolverStats", HttpMethod.GET)
                .name("adminconsole.dns.resolver.stats.get");

        server
                .uri("/api/adminconsole/dns/test", dnsController)
                .action("testNameServer", HttpMethod.POST)
                .name("adminconsole.dns.stats.test");

        // ** New Adminconsole: webrtc
        server
                .uri("/api/adminconsole/webrtc", anonymousController)
                .action("setWebRTCBlockingState", HttpMethod.PUT)
                .name("adminconsole.anonymous.webrtc.route");
        server
                .uri("/api/adminconsole/webrtc", anonymousController)
                .action("isWebRTCBlockingEnabled", HttpMethod.GET)
                .name("adminconsole.anonymous.webrtc.route");

        // ** New Adminconsole: referrer
        server
                .uri("/api/adminconsole/referrer", anonymousController)
                .action("setHTTPRefererRemovingState", HttpMethod.PUT)
                .name("adminconsole.anonymous.referrer.route");
        server
                .uri("/api/adminconsole/referrer", anonymousController)
                .action("isHTTPRefererRemovingEnabled", HttpMethod.GET)
                .name("adminconsole.anonymous.referrer.route");

        // ** New Adminconsole: captive portal
        server
                .uri("/api/adminconsole/captiveportal", anonymousController)
                .action("setGoogleCaptivePortalRedirectState", HttpMethod.PUT)
                .name("adminconsole.anonymous.captiveportal.route");
        server
                .uri("/api/adminconsole/captiveportal", anonymousController)
                .action("getGoogleCaptivePortalRedirectState", HttpMethod.GET)
                .name("adminconsole.controlbar.anonymous.captiveportal.route");

        // ** New Adminconsole: do not track / do-not-track
        server
                .uri("/api/adminconsole/dnt", anonymousController)
                .action("getDntHeaderState", HttpMethod.GET)
                .name("adminconsole.anonymous.dntheader.get.route");
        server
                .uri("/api/adminconsole/dnt", anonymousController)
                .action("setDntHeaderState", HttpMethod.PUT)
                .name("adminconsole.anonymous.dntheader.set.route");

        // ** New Adminconsole: Compression
        server
                .uri("/api/adminconsole/compressionmode", featureController)
                .action("getCompressionMode", HttpMethod.GET)
                .name("adminconsole.features.compressionmode");
        server
                .uri("/api/adminconsole/compressionmode", featureController)
                .action("setCompressionMode", HttpMethod.PUT)
                .name("adminconsole.features.compressionmode");

        // ** New Adminconsole: System Language
        server
                .uri("/api/adminconsole/language", languageController)
                .action("setLanguage", HttpMethod.POST)
                .name("adminconsole.language.set.route");

        // ** New Adminconsole: System Timezone
        server
                .uri("/api/adminconsole/timezone/continents", timezoneController)
                .action("getTimezoneCategories", HttpMethod.GET)
                .name("adminconsole.timezone.continents.get.route");
        server
                .uri("/api/adminconsole/timezone/continent/countries", timezoneController)
                .action("getTimeZoneStringsForCategory", HttpMethod.PUT)
                .name("adminconsole.timezone.continents.put.countries.route");

        // ** New Adminconsole: System Events
        server
                .uri("/api/adminconsole/events", eventController)
                .action("getEvents", HttpMethod.GET)
                .name("adminconsole.events.get.route");
        server
                .uri("/api/adminconsole/events/{mode}", eventController)
                .action("deleteSeveralEvents", HttpMethod.DELETE)
                .name("adminconsole.events.delete.route");

        // ** New Adminconsole: System Diagnostics Report
        server
                .uri("/api/adminconsole/diagnostics/report", diagnosticsReportController)
                .action("startReport", HttpMethod.POST)
                .name("adminconsole.diagnostics.report.create.route");

        server
                .uri("/api/adminconsole/diagnostics/report", diagnosticsReportController)
                .action("getReportStatus", HttpMethod.GET)
                .name("adminconsole.diagnostics.report.status.route");

        server
                .uri("/api/diagnostics/report/download", diagnosticsReportController)
                .action("getReport", HttpMethod.GET)
                .name("public.diagnostics.report.download.route")
                .noSerialization();

        // ** New Adminconsole: System Factory reset
        server.uri("/api/adminconsole/factoryreset", factoryResetController)
                .action("factoryReset", HttpMethod.GET)
                .name("adminconsole.system.factoryreset.route");

        // ** New Adminconsole: ParentalControl filterlists
        server
                .uri("/api/adminconsole/filterlists", filterListsController)
                .action("getFilterLists", HttpMethod.GET)
                .name("adminconsole.controlbar.filterlists.get.route");
        server
                .uri("/api/adminconsole/filterlists/{id}/domains", filterListsController)
                .action("getFilterListDomains", HttpMethod.GET)
                .name("adminconsole.parentalcontrol.filterlists.getdomains.route");
        server
                .uri("/api/adminconsole/filterlists/{id}/update", filterListsController)
                .action("updateFilterList", HttpMethod.PUT)
                .name("adminconsole.parentalcontrol.filterlists.update.route");
        server
                .uri("/api/adminconsole/filterlists/{id}", filterListsController)
                .action("deleteFilterList", HttpMethod.DELETE)
                .name("adminconsole.parentalcontrol.filterlists.delete.route");
        server
                .uri("/api/adminconsole/filterlists", filterListsController)
                .action("createFilterList", HttpMethod.POST)
                .name("adminconsole.parentalcontrol.filterlists.create.route");
        server
                .uri("/api/adminconsole/filterlists/unique", filterListsController)
                .action("isUnique", HttpMethod.GET)
                .name("adminconsole.parentalcontrol.filterlists.unique.route");
        server
                .uri("/api/adminconsole/filterlists/meta", filterListsController)
                .action("getFilterMetaData", HttpMethod.GET)
                .name("adminconsole.controlbar.filterlists.metadata.get.route");

        // ** New Adminconsole: Cloaking
        server
                .uri("/api/adminconsole/useragent/cloaked", userAgentController)
                .action("getCloakedUserAgentByDeviceId", HttpMethod.GET)
                .name("adminconsole.useragents.getCloakedUserAgentByDeviceId.route");
        server
                .uri("/api/adminconsole/useragent/list", userAgentController)
                .action("getAgentList", HttpMethod.GET)
                .name("adminconsole.useragent.getAgentList.route");
        server
                .uri("/api/adminconsole/useragent/cloaked", userAgentController)
                .action("setCloakedUserAgentByDeviceId", HttpMethod.PUT)
                .name("adminconsole.useragent.setCloakedUserAgentByDeviceId.route");

        // ** New Adminconsole: Network
        server
                .uri("/api/adminconsole/network/dhcpstate", networkController)
                .action("getDHCPActive", HttpMethod.GET)
                .name("adminconsole.network.config.get.network.state.route");
        server
                .uri("/api/adminconsole/network", networkController)
                .action("getConfiguration", HttpMethod.GET)
                .name("adminconsole.network.config.get.route");
        server
                .uri("/api/adminconsole/network/setupPageInfo", networkController)
                .action("getSetupPageInfo", HttpMethod.GET)
                .name("adminconsole.network.config.setuppage.get.route");
        server
                .uri("/api/adminconsole/network", networkController)
                .action("updateConfiguration", HttpMethod.PUT)
                .name("adminconsole.network.config.put.route");

        server
                .uri("/api/adminconsole/network/dhcpservers", networkController)
                .action("getDhcpServers", HttpMethod.GET)
                .name("adminconsole.network.config.get.dhcp.servers.route");

        // ** New Adminconsole: VPN
        server
                .uri("/api/adminconsole/vpn/profiles", openVpnController)
                .action("getProfiles", HttpMethod.GET)
                .name("adminconsole.vpn.profiles.get.route");
        server
                .uri("/api/adminconsole/vpn/profile", openVpnController)
                .action("createProfile", HttpMethod.POST)
                .name("adminconsole.vpn.profiles.create.route");
        server
                .uri("/api/adminconsole/vpn/profile/{id}", openVpnController)
                .action("getProfile", HttpMethod.GET)
                .name("adminconsole.vpn.profile.get.route");
        server
                .uri("/api/adminconsole/vpn/profile/{id}", openVpnController)
                .action("updateProfile", HttpMethod.PUT)
                .name("adminconsole.vpn.profile.update.route");
        server
                .uri("/api/adminconsole/vpn/profile/{id}", openVpnController)
                .action("deleteProfile", HttpMethod.DELETE)
                .name("adminconsole.vpn.profile.delete.route");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/config", openVpnController)
                .action("getProfileConfig", HttpMethod.GET)
                .name("adminconsole.vpn.profile.get.config.route");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/config", openVpnController)
                .action("uploadProfileConfig", HttpMethod.PUT)
                .name("adminconsole.vpn.profile.create.config.route");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/config/{option}", openVpnController)
                .action("uploadProfileConfigOption", HttpMethod.PUT)
                .name("adminconsole.vpn.profile.set.config.option");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/status", openVpnController)
                .action("setVpnStatus", HttpMethod.PUT)
                .name("adminconsole.vpn.profile.set.status");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/status", openVpnController)
                .action("getVpnStatus", HttpMethod.GET)
                .name("adminconsole.vpn.profile.get.status");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/status/{device}", openVpnController)
                .action("getVpnDeviceStatus", HttpMethod.GET)
                .name("adminconsole.vpn.profile.status.device.get");
        server
                .uri("/api/adminconsole/vpn/profile/{id}/status/{device}", openVpnController)
                .action("setVpnDeviceStatus", HttpMethod.PUT)
                .name("adminconsole.vpn.profile.status.device.set");
        server
                .uri("/api/adminconsole/vpn/profile/status/{device}", openVpnController)
                .action("getVpnStatusByDevice", HttpMethod.GET)
                .name("adminconsole.vpn.getVpnStatusByDevice.route");

        // ** New Adminconsole: TOR
        server
                .uri("/api/adminconsole/tor/countries", anonymousController)
                .action("getTorCountries", HttpMethod.GET)
                .name("adminconsole.tor.route");
        server
                .uri("/api/adminconsole/tor/countries/selected", anonymousController)
                .action("setTorExitNodeCountries", HttpMethod.PUT)
                .name("adminconsole.tor.route");
        server
                .uri("/api/adminconsole/tor/countries/selected", anonymousController)
                .action("getCurrentTorExitNodeCountries", HttpMethod.GET)
                .name("adminconsole.tor.route");
        server
                .uri("/api/adminconsole/tor/config/{deviceId}", anonymousController)
                .action("getConfigById", HttpMethod.GET)
                .name("adminconsole.tor.getConfig.route");
        server
                .uri("/api/adminconsole/tor/config/{deviceId}", anonymousController)
                .action("putConfigById", HttpMethod.PUT)
                .name("adminconsole.tor.putConfig.route");
        server
                .uri("/api/adminconsole/tor/newidentity", anonymousController)
                .action("getNewTorIdentity", HttpMethod.PUT)
                .name("adminconsole.tor.getNewTorIdentity.route");

        // ** New Adminconsole: SSL Manual Recording (old experts tools)
        server
                .uri("/api/adminconsole/recording/toggle", recordingController)
                .action("recordingStartStop", HttpMethod.POST)
                .name("adminconsole.recording.start.stop");
        server
                .uri("/api/adminconsole/recording/status", recordingController)
                .action("getRecordingStatus", HttpMethod.GET)
                .name("adminconsole.recording.get.status");
        server
                .uri("/api/adminconsole/recording/result", recordingController)
                .action("getRecordedDomainList", HttpMethod.GET)
                .name("adminconsole.recording.get.recorded.domain.list");

        // ** New Adminconsole: OPEN VPN (eBlocker mobile)
        server
                .uri("/api/adminconsole/openvpn/status", openVpnServerController)
                .action("getOpenVpnServerStatus", HttpMethod.GET)
                .name("adminconsole.vpn.server.status.get");
        server
                .uri("/api/adminconsole/openvpn/status", openVpnServerController)
                .action("setOpenVpnServerStatus", HttpMethod.POST)
                .name("adminconsole.vpn.server.status.set");
        server
                .uri("/api/adminconsole/openvpn/status", openVpnServerController)
                .action("resetOpenVpnServerStatus", HttpMethod.DELETE)
                .name("adminconsole.vpn.server.status.delete");
        server
                .uri("/api/adminconsole/openvpn/certificates", openVpnServerController)
                .action("getCertificates", HttpMethod.GET)
                .name("adminconsole.vpn.server.certificates.get");
        server
                .uri("/api/adminconsole/openvpn/certificates/generateDownloadUrl/{deviceId}/{deviceType}", openVpnServerController)
                .action("generateDownloadUrl", HttpMethod.GET)
                .name("adminconsole.vpn.server.certificates.generateDownloadUrl.get");
        server
                .uri("/api/adminconsole/openvpn/certificates/downloadClientConf/{deviceId}", openVpnServerController)
                .action("downloadClientConf", HttpMethod.GET)
                .name("adminconsole.vpn.server.certificates.downloadClientConf.get")
                .noSerialization();
        server
                .uri("/api/adminconsole/openvpn/enable/{deviceId}", openVpnServerController)
                .action("enableDevice", HttpMethod.POST)
                .name("adminconsole.vpn.server.enable.device.post");
        server
                .uri("/api/adminconsole/openvpn/disable/{deviceId}", openVpnServerController)
                .action("disableDevice", HttpMethod.POST)
                .name("adminconsole.vpn.server.disable.device.post");
        server
                .uri("/api/adminconsole/openvpn/privateNetworkAccess/{deviceId}", openVpnServerController)
                .action("setPrivateNetworkAccess", HttpMethod.PUT)
                .name("adminconsole.vpn.server.privateNetworkAccess.device.put");
        server
                .uri("/api/adminconsole/upnpn/{port}", openVpnServerController)
                .action("setPortForwarding", HttpMethod.PUT)
                .name("adminconsole.vpn.upnp.portForwarding");

        // ** New Adminconsole: eBlocker Mobile test connection
        server
                .uri("/api/adminconsole/openvpn/test", mobileConnectionCheckController)
                .action("start", HttpMethod.POST)
                .name("adminconsole.vpn.test.start");
        server
                .uri("/api/adminconsole/openvpn/test", mobileConnectionCheckController)
                .action("stop", HttpMethod.DELETE)
                .name("adminconsole.vpn.test.stop");
        server
                .uri("/api/adminconsole/openvpn/test", mobileConnectionCheckController)
                .action("getStatus", HttpMethod.GET)
                .name("adminconsole.vpn.test.status");

        server
                .uri("/api/adminconsole/openvpn/dns", mobileDnsCheckController)
                .action("check", HttpMethod.POST)
                .name("adminconsole.vpn.test.dns");

        // ** New Adminconsole: save customer info (for remind-me-again VPN offer)
        server
                .uri("/api/adminconsole/customerInfo", customerInfoController)
                .action("get", HttpMethod.GET)
                .name("adminconsole.customerInfo.get.route");
        server
                .uri("/api/adminconsole/customerInfo", customerInfoController)
                .action("save", HttpMethod.POST)
                .name("adminconsole.customerInfo.save.route");
        server
                .uri("/api/adminconsole/customerInfo", customerInfoController)
                .action("delete", HttpMethod.DELETE)
                .name("adminconsole.customerInfo.delete.route");

        // ** New Adminconsole: Analysis Tool (Filter / HTTP Expert Tools)
        server
                .uri("/api/adminconsole/recorder", transactionRecorderController)
                .action("start", HttpMethod.POST)
                .name("adminconsole.transactionRecorder.start.route");
        server
                .uri("/api/adminconsole/recorder", transactionRecorderController)
                .action("stop", HttpMethod.DELETE)
                .name("adminconsole.transactionRecorder.stop.route");
        server
                .uri("/api/adminconsole/recorder", transactionRecorderController)
                .action("info", HttpMethod.GET)
                .name("adminconsole.transactionRecorder.info.route");
        server
                .uri("/api/adminconsole/recorder/results", transactionRecorderController)
                .action("getAll", HttpMethod.GET)
                .name("adminconsole.transactionRecorder.getall.route");
        server
                .uri("/api/adminconsole/recorder/whatifmode", transactionRecorderController)
                .action("getWhatIfMode", HttpMethod.GET)
                .name("adminconsole.transactionRecorder.whatifmode.get.route");
        server
                .uri("/api/adminconsole/recorder/whatifmode", transactionRecorderController)
                .action("setWhatIfMode", HttpMethod.PUT)
                .name("adminconsole.transactionRecorder.whatifmode.put.route");
        server
                .uri("/api/adminconsole/recorder/results/csv", transactionRecorderController)
                .action("getAllAsCSV", HttpMethod.GET)
                .name("adminconsole.transactionRecorder.getallascsv.route")
                .noSerialization();
        server
                .uri("/api/adminconsole/recorder/results/{domain}", transactionRecorderController)
                .action("get", HttpMethod.GET)
                .name("adminconsole.transactionRecorder.get.route");

        // ** New Adminconsole: Filter
        server
                .uri("/api/adminconsole/stats/total", filterStatisticsController)
                .action("getTotalStats", HttpMethod.GET)
                .name("adminconsole.stats.total.get");

        server
                .uri("/api/adminconsole/stats/domain", filterStatisticsController)
                .action("getBlockedDomainsStats", HttpMethod.GET)
                .name("adminconsole.blockedStats.filter.get");

        // ** New Adminconsole: Pausing the current device:
        server
                .uri("/api/adminconsole/device/pause", deviceController)
                .action("getPauseByDeviceId", HttpMethod.GET)
                .name("adminconsole.device.get.pause");
        server
                .uri("/api/adminconsole/device/pause", deviceController)
                .action("setPauseByDeviceId", HttpMethod.PUT)
                .name("adminconsole.device.put.pause");

        // ** New Adminconsole: LED settings:
        server
                .uri("/api/adminconsole/led", ledSettingsController)
                .action("getSettings", HttpMethod.GET)
                .name("adminconsole.led.settings.get");
        server
                .uri("/api/adminconsole/led", ledSettingsController)
                .action("updateSettings", HttpMethod.POST)
                .name("adminconsole.led.settings.update");

        server
                .uri("/api/adminconsole/console/ip", controlBarController)
                .action("getConsoleIp", HttpMethod.GET)
                .name("public.adminconsole.console.ip.route");

        // ** New Adminconsole: Splash settings:
        server
                .uri("/api/adminconsole/splash", splashController)
                .action("get", HttpMethod.GET)
                .name("adminconsole.splash.get");
        server
                .uri("/api/adminconsole/splash", splashController)
                .action("set", HttpMethod.POST)
                .name("adminconsole.splash.update");

        // ** new adminconsole: tasks
        server
                .uri("/api/adminconsole/tasks/log", tasksController)
                .action("getLog", HttpMethod.GET)
                .name("adminconsole.tasks.log");
        server
                .uri("/api/adminconsole/tasks/viewConfig", tasksController)
                .action("getViewConfig", HttpMethod.GET)
                .name("adminconsole.tasks.view.config.get");
        server
                .uri("/api/adminconsole/tasks/viewConfig", tasksController)
                .action("setViewConfig", HttpMethod.PUT)
                .name("adminconsole.tasks.view.config.set");
        server
                .uri("/api/adminconsole/tasks/stats", tasksController)
                .action("getPoolStats", HttpMethod.GET)
                .name("adminconsole.tasks.stats.get");
    }

    private void addControlBarRoutes() {
        // public: Must be available, even before ControlBar is open.
        // There is a certain level of protection due to the pageContextId!
        server
                .uri("/filter/badge/{pageContextId}", filterController)
                .action("getBadge", HttpMethod.GET)
                .name("public.filter.badge.route");

        server
                .uri("/controlbar/console/ip", controlBarController)
                .action("getConsoleIp", HttpMethod.GET)
                .name("public.controlbar.console.ip.route");

        // ** New Controlbar, with 'api' prefix
        // ** New Controlbar: Trackers / Ads
        server
                .uri("/api/filter/stats/{pageContextId}", filterController)
                .action("getStats", HttpMethod.GET)
                .name("controlbar.filter.getStats.route");
        server
                .uri("/api/filter/config", filterController)
                .action("getConfig", HttpMethod.GET)
                .name("controlbar.filter.getConfig.route");
        server
                .uri("/api/filter/config", filterController)
                .action("putConfig", HttpMethod.PUT)
                .name("controlbar.filter.putConfig.route");
        server
                .uri("/api/filter/blockedAds/{pageContextId}", filterController)
                .action("getBlockedAdsSet", HttpMethod.GET)
                .name("controlbar.filter.getBlockedAdsSet.route");
        server
                .uri("/api/whitelist/{pageContextId}", domainWhiteListController)
                .action("getDomainStatus", HttpMethod.GET)
                .name("controlbar.whitelist.getDomainStatus.route");
        server
                .uri("/api/whitelist/{pageContextId}", domainWhiteListController)
                .method(HttpMethod.PUT)
                .name("controlbar.whitelist.save.route");
        server
                .uri("/api/filter/blockedTrackings/{pageContextId}", filterController)
                .action("getBlockedTrackingsSet", HttpMethod.GET)
                .name("controlbar.filter.getBlockedTrackingsSet.route");

        // ** New Controlbar: General "get * by device"
        server
                .uri("/api/controlbar/device", controlBarController)
                .action("getDevice", HttpMethod.GET)
                .name("controlbar.device.getDevice.route");
        server
                .uri("/api/controlbar/device/ads/{deviceId}", deviceController)
                .action("updateDeviceDnsAdsEnabledStatus", HttpMethod.PUT)
                .name("controlbar.device.dns.ads.update");
        server
                .uri("/api/controlbar/device/trackers/{deviceId}", deviceController)
                .action("updateDeviceDnsTrackersEnabledStatus", HttpMethod.PUT)
                .name("controlbar.device.dns.trackers.update");

        // ** New Controlbar: Users
        server
                .uri("/api/controlbar/users", controlBarController)
                .action("getUsers", HttpMethod.GET)
                .name("controlbar.users.getUsers.route");
        server
                .uri("/api/controlbar/users/operatinguser", controlBarController)
                .action("setOperatingUser", HttpMethod.PUT)
                .name("controlbar.users.setOperatingUser.route");
        server
                .uri("/api/controlbar/users/{id}/changepin", userController)
                .action("changePin", HttpMethod.POST)
                .name("controlbar.users.changePin.route");

        // ** New Controlbar: to get IP for Dashboard, Settings and Pause-link-dashboard
        server
                .uri("/api/controlbar/console/ip", controlBarController)
                .action("getConsoleIp", HttpMethod.GET)
                .name("controlbar.console.getConsoleIp.route");

        // ** New Controlbar: General, to get ProductInfo
        server
                .uri("/api/controlbar/registration", deviceRegistrationController)
                .action("registrationStatus", HttpMethod.GET)
                .name("controlbar.registration.status.route");

        // ** New Controlbar: IP-Anon
        server
                .uri("/api/vpn/profiles", openVpnController)
                .action("getProfiles", HttpMethod.GET)
                .name("controlbar.vpn.getProfiles.route");
        // get VPN status: in setVpnActivationState, but basically the poller
        server
                .uri("/api/vpn/profiles/{id}/status", openVpnController)
                .action("getVpnStatus", HttpMethod.GET)
                .name("controlbar.vpn.getVpnStatus.route");
        // updateVpnStatus /anonymous/vpn/profiles/status/me
        server
                .uri("/api/vpn/profiles/status/{device}", openVpnController)
                .action("getVpnStatusByDevice", HttpMethod.GET)
                .name("controlbar.vpn.getVpnStatusByDevice.route");
        // setVpnActivationState
        server
                .uri("/api/vpn/profiles/{id}/status/{device}", openVpnController)
                .action("setVpnDeviceStatus", HttpMethod.PUT)
                .name("controlbar.vpn.setVpnDeviceStatus.route");
        server
                .uri("/api/tor/config", anonymousController)
                .action("getConfig", HttpMethod.GET)
                .name("controlbar.tor.getConfig.route");
        server
                .uri("/api/tor/config", anonymousController)
                .action("putConfig", HttpMethod.PUT)
                .name("controlbar.tor.putConfig.route");
        server
                .uri("/api/tor/device/showwarnings", deviceController)
                .action("getShowWarnings", HttpMethod.GET)
                .name("controlbar.tor.device.getShowWarnings.route");
        server
                .uri("/api/tor/device/showwarnings", deviceController)
                .action("postShowWarnings", HttpMethod.POST)
                .name("controlbar.tor.device.postShowWarnings.route");
        server
                .uri("/api/tor/checkservices", anonymousController)
                .action("getTorCheckServices", HttpMethod.GET)
                .name("controlbar.tor.checkservices.route");
        server
                .uri("/api/tor/newidentity", anonymousController)
                .action("getNewTorIdentity", HttpMethod.PUT)
                .name("controlbar.tor.getNewTorIdentity.route");

        // ** New Controlbar: Cloaking
        server
                .uri("/api/useragent/list", userAgentController)
                .action("getAgentList", HttpMethod.GET)
                .name("controlbar.useragent.getAgentList.route");
        server
                .uri("/api/useragent/cloaked", userAgentController)
                .action("getCloakedUserAgentByDeviceId", HttpMethod.GET)
                .name("controlbar.useragent.getCloakedUserAgentByDeviceId.route");
        server
                .uri("/api/useragent/cloaked", userAgentController)
                .action("setCloakedUserAgentByDeviceId", HttpMethod.PUT)
                .name("controlbar.useragent.setCloakedUserAgentByDeviceId.route");
        server
                .uri("/api/device/pauseStatus", deviceController)
                .action("getPauseCurrentDevice", HttpMethod.GET)
                .name("controlbar.device.get.pausestatus");

        // ** New Controlbar: Pause
        server
                .uri("/api/device/pause", deviceController)
                .action("pauseCurrentDeviceIfNotYetPausing", HttpMethod.POST)
                .name("controlbar.device.get.pause");// Pausing the current device, if not yet pausing:
        server
                .uri("/api/device/dialogStatus", deviceController)
                .action("getPauseDialogStatus", HttpMethod.GET)
                .name("controlbar.device.getPauseDialogStatus.pause");
        server
                .uri("/api/device/dialogStatus", deviceController)
                .action("updatePauseDialogStatus", HttpMethod.POST)
                .name("controlbar.device.updatePauseDialogStatus.pause");
        server
                .uri("/api/device/dialogStatusDoNotShowAgain", deviceController)
                .action("getPauseDialogStatusDoNotShowAgain", HttpMethod.GET)
                .name("controlbar.device.getPauseDialogStatusShowAgain.pause");
        server
                .uri("/api/device/dialogStatusDoNotShowAgain", deviceController)
                .action("updatePauseDialogStatusDoNotShowAgain", HttpMethod.POST)
                .name("controlbar.device.updatePauseDialogStatusDoNotShowAgain.pause");

        // ** New Controlbar: Messages
        server
                .uri("/api/messages/action", messageCenterController)
                .action("executeMessageAction", HttpMethod.POST)
                .name("controlbar.messages.action.route");
        server
                .uri("/api/messages/hide", messageCenterController)
                .action("hideMessage", HttpMethod.POST)
                .name("controlbar.messages.hide.route");
        server
                .uri("/api/messages", messageCenterController)
                .action("getMessages", HttpMethod.GET)
                .name("controlbar.messages.get.route");
        server
                .uri("/api/messages/donotshowagain", messageCenterController)
                .action("setDoNotShowAgain", HttpMethod.PUT)
                .name("controlbar.messages.donotshowagain.put.route");

        // ** New Controlbar: OnlineTime
        server
                .uri("/api/controlbar/localtimestamp", timestampController)
                .action("getLocalTimestamp", HttpMethod.GET)
                .name("controlbar.timestamp.route");
        server
                .uri("/api/controlbar/parentalcontrol/usage", parentalControlController)
                .action("startUsage", HttpMethod.POST)
                .name("controlbar.parentalcontrol.usage.start");
        server
                .uri("/api/controlbar/parentalcontrol/usage", parentalControlController)
                .action("stopUsage", HttpMethod.DELETE)
                .name("controlbar.parentalcontrol.usage.stop");
        server
                .uri("/api/controlbar/parentalcontrol/usage", parentalControlController)
                .action("getUsage", HttpMethod.GET)
                .name("controlbar.parentalcontrol.usage");

        // ** New Controlbar: Filter
        server
                .uri("/api/controlbar/stats/filter", filterStatisticsController)
                .action("getStats", HttpMethod.GET)
                .name("public.controlbar.stats.filter.get");

        server
                .uri("/api/filter/meta", filterListsController)
                .action("getFilterMetaData", HttpMethod.GET)
                .name("controlbar.filterlists.metadata.get.route");

        // ** New Controlbar: Ssl
        server
                .uri("/api/controlbar/ssl/status", sslController)
                .action("getSslDashboardStatus", HttpMethod.GET)
                .name("controlbar.ssl.status.get");
    }

    private void addAdvicePageRoutes() {
        // ** advice page: to get IP for Dashboard, Settings and Pause-link-dashboard
        server
                .uri("/api/advice/console/ip", controlBarController)
                .action("getConsoleIp", HttpMethod.GET)
                .name("advice.console.getConsoleIp.route");

        // ** advice page: General, to get ProductInfo
        server
                .uri("/api/advice/registration", deviceRegistrationController)
                .action("registrationStatus", HttpMethod.GET)
                .name("advice.registration.status.route");

        // ** advice page: device
        server
                .uri("/api/advice/device", deviceController)
                .action("getCurrentDevice", HttpMethod.GET)
                .name("advice.device.get");
        server
                .uri("/api/advice/device/showWelcomeFlags", deviceController)
                .action("updateShowWelcomeFlags", HttpMethod.PUT)
                .name("advice.device.updateShowWelcomeFlags");
    }

    private void addErrorPageRoutes() {
        server
                .uri("/api/errorpage/device/pause", deviceController)
                .action("pauseCurrentDeviceIfNotYetPausing", HttpMethod.POST)
                .name("errorpage.device.set.pause");
    }

    private void addFilterListsRoutes() {
        server
                .uri("/filterlists", filterListsController)
                .action("getFilterLists", HttpMethod.GET)
                .name("public.controlbar.filterlists.get.route");// Public since used by squid error page

    }

    /**
     * Record and analyze HTTP(S) connections
     */
    private void addTransactionRecorderRoutes() {
        //TODO: Should not be public
        server
                .uri("/recorder/results/csv", transactionRecorderController)
                .action("getAllAsCSV", HttpMethod.GET)
                .name("public.transactionRecorder.getallascsv.route")
                .noSerialization();
    }

    private void addDashboardRoutes() {
        server
                .uri("/api/settings", settingsController)
                .action("getLocaleSettings", HttpMethod.GET)
                .name("public.dashboard.settings.get.route");

        server
                .uri("/api/settings/timezone", settingsController)
                .action("setTimeZone", HttpMethod.PUT)
                .name("dashboard.locale.put.timezone.route");

        server
                .uri("/api/token/{appContext}", authenticationController)
                .action("generateToken", HttpMethod.GET)
                .name("public.token.get.route");

        // Pausing the selected device:
        server
                .uri("/api/device/pause/{deviceId}", deviceController)
                .action("getPauseByDeviceId", HttpMethod.GET)
                .name("dashboard.device.get.pause")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        server
                .uri("/api/device/pause/{deviceId}", deviceController)
                .action("setPauseByDeviceId", HttpMethod.PUT)
                .name("dashboard.device.put.pause")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
        // Details about current device
        server
                .uri("/api/device", deviceController)
                .action("getCurrentDevice", HttpMethod.GET)
                .name("dashboard.device.get");

        server
                .uri("/api/device/{deviceId}", deviceController)
                .action("updateDeviceDashboard", HttpMethod.PUT)
                .name("dashboard.device.update")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID)
                .flag("DENY_CHILD_ACCESS");

        server
                .uri("/api/device/showWelcomeFlags", deviceController)
                .action("updateShowWelcomeFlags", HttpMethod.PUT)
                .name("dashboard.device.updateShowWelcomeFlags");

        server
                .uri("/api/parentalcontrol/usage", parentalControlController)
                .action("startUsage", HttpMethod.POST)
                .name("dashboard.parentalcontrol.usage.start");

        server
                .uri("/api/parentalcontrol/usage", parentalControlController)
                .action("stopUsage", HttpMethod.DELETE)
                .name("dashboard.parentalcontrol.usage.stop");

        server
                .uri("/api/parentalcontrol/usage", parentalControlController)
                .action("getUsage", HttpMethod.GET)
                .name("dashboard.parentalcontrol.usage");

        server
                .uri("/api/parentalcontrol/usage/{id}", parentalControlController)
                .action("getUsageByUserId", HttpMethod.GET)
                .name("dashboard.parentalcontrol.usage.by.id");

        server
                .uri("/api/dashboard/userprofiles", parentalControlController)
                .action("getProfiles", HttpMethod.GET)
                .name("dashboard.parentalcontrol.get.profiles.route");

        server
                .uri("/api/dashboard/searchEngineConfig", parentalControlController)
                .action("getSearchEngineConfiguration", HttpMethod.GET)
                .name("dashboard.parentalcontrol.searchEngine.get");

        server
                .uri("/api/userprofile", controlBarController)
                .action("getUserProfile", HttpMethod.GET)
                .name("dashboard.userprofile.route");

        server
                .uri("/api/dashboard/userprofile/maxusage/{id}", parentalControlController)
                .action("setMaxUsage", HttpMethod.POST)
                .name("dashboard.userprofile.set.maxusage.route");

        server
                .uri("/api/dashboard/userprofile/contentfilter/{id}", parentalControlController)
                .action("setContentFilter", HttpMethod.POST)
                .name("dashboard.userprofile.set.contentfilter.route");

        server
                .uri("/api/dashboard/userprofile/inetnetaccess/{id}", parentalControlController)
                .action("setInternetAccessStatus", HttpMethod.POST)
                .name("dashboard.userprofile.set.internetaccess.route");

        server
                .uri("/api/dashboard/userprofile/inetnetaccess/{id}", parentalControlController)
                .action("getInternetAccessStatus", HttpMethod.GET)
                .name("dashboard.userprofile.get.internetaccess.route");

        server
                .uri("/api/dashboard/userprofile/bonustime/{id}", parentalControlController)
                .action("addOnlineTimeForToday", HttpMethod.POST)
                .name("dashboard.userprofile.set.bonustime.route");
        server
                .uri("/api/dashboard/userprofile/bonustime/{id}", parentalControlController)
                .action("resetBonusTimeForToday", HttpMethod.DELETE)
                .name("dashboard.userprofile.delete.bonustime.route");

        server
                .uri("/api/filter/stats/device/{deviceId}", filterStatisticsController)
                .action("getStats", HttpMethod.GET)
                .name("dashboard.stats.route")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);

        server
                .uri("/api/filter/totalStats", filterStatisticsController)
                .action("getTotalStats", HttpMethod.GET)
                .name("dashboard.totalStats.route.get");

        server
                .uri("/api/filter/totalStats", filterStatisticsController)
                .action("resetTotalStats", HttpMethod.DELETE)
                .name("dashboard.totalStats.route.delete");

        server
                .uri("/api/filter/blockeddomains/{deviceId}", filterStatisticsController)
                .action("getBlockedDomainsStats", HttpMethod.GET)
                .name("dashboard.blockedStats.filter.get")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);

        server
                .uri("/api/filter/blockeddomains/{deviceId}", filterStatisticsController)
                .action("resetBlockedDomainsStats", HttpMethod.DELETE)
                .name("dashboard.blockedStats.filter.delete")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);

        // Registraion data
        server
                .uri("/api/registrationdata", deviceRegistrationController)
                .action("registrationStatus", HttpMethod.GET)
                .name("dashboard.registration.status.route");

        // Dashboard card data
        server
                .uri("/api/dashboardcard", dashboardCardController)
                .action("getDashboardCards", HttpMethod.GET)
                .name("dashboard.card.get.status");

        server
                .uri("/api/dashboardcard/columns", dashboardCardController)
                .action("setDashboardColumnsView", HttpMethod.PUT)
                .name("dashboard.card.save.columns");

        server
                .uri("/api/dashboardcard/columns", dashboardCardController)
                .action("getDashboardColumnsView", HttpMethod.GET)
                .name("dashboard.card.get.columns");

        server
                .uri("/api/dashboard/users", userController)
                .action("getUsers", HttpMethod.GET)
                .name("dashboard.users.getUsers.route");
        server
                .uri("/api/dashboard/users/operatinguser", controlBarController)
                .action("setOperatingUser", HttpMethod.PUT)
                .name("dashboard.users.setOperatingUser.route");
        server
                .uri("/api/dashboard/users/{id}/changepin", userController)
                .action("changePin", HttpMethod.POST)
                .name("dashboard.users.changePin.route");

        // eBlocker mobile
        server
                .uri("/api/dashboard/openvpn/filename/{deviceId}/{deviceType}", openVpnServerController)
                .action("getOpenVpnFileName", HttpMethod.GET)
                .name("dashboard.vpn.server.filename.get");
        server
                .uri("/api/dashboard/openvpn/status", openVpnServerController)
                .action("getOpenVpnServerStatus", HttpMethod.GET)
                .name("dashboard.vpn.server.status.get");
        server
                .uri("/api/dashboard/openvpn/status", openVpnServerController)
                .action("setOpenVpnServerStatus", HttpMethod.POST)
                .name("dashboard.vpn.server.status.set");
        server
                .uri("/api/dashboard/openvpn/status", openVpnServerController)
                .action("resetOpenVpnServerStatus", HttpMethod.DELETE)
                .name("dashboard.vpn.server.status.delete");
        server
                .uri("/api/dashboard/openvpn/certificates", openVpnServerController)
                .action("getCertificates", HttpMethod.GET)
                .name("dashboard.vpn.server.certificates.get");
        server
                .uri("/api/dashboard/openvpn/certificates/generateDownloadUrl/{deviceId}/{deviceType}", openVpnServerController)
                .action("generateDownloadUrl", HttpMethod.GET)
                .name("dashboard.vpn.server.certificates.generateDownloadUrl.get");
        server
                .uri("/api/dashboard/openvpn/certificates/downloadClientConf/{deviceId}", openVpnServerController)
                .action("downloadClientConf", HttpMethod.GET)
                .name("dashboard.vpn.server.certificates.downloadClientConf.get")
                .noSerialization();
        server
                .uri("/api/dashboard/customdomainfilter/{userId}", customDomainFilterConfigController)
                .action("getFilter", HttpMethod.GET)
                .name("dashboard.custom.domain.filter.get")
                .flag(DashboardAuthorizationProcessor.VERIFY_USER_ID);
        server
                .uri("/api/dashboard/customdomainfilter/{userId}", customDomainFilterConfigController)
                .action("setFilter", HttpMethod.PUT)
                .name("dashboard.custom.domain.filter.set")
                .flag(DashboardAuthorizationProcessor.VERIFY_USER_ID);

        // Dashboard Dns
        server
                .uri("/api/dashboard/dns/status", dnsController)
                .action("getStatus", HttpMethod.GET)
                .name("dashboard.dns.status.get");

        // Dashboard setting / setup card
        server
                .uri("/api/dashboard/network/info", networkController)
                .action("getSetupPageInfo", HttpMethod.GET)
                .name("dashboard.network.config.get.route");
        server
                .uri("/api/dashboard/registration", deviceRegistrationController)
                .action("registrationStatus", HttpMethod.GET)
                .name("dashboard.registration.status.route");

        // ** Dashboard IP-Anon
        server
                .uri("/api/dashboard/vpn/profiles", openVpnController)
                .action("getProfiles", HttpMethod.GET)
                .name("dashboard.vpn.getProfiles.route");
        // get VPN status: in setVpnActivationState, but basically the poller
        server
                .uri("/api/dashboard/vpn/profiles/{id}/status", openVpnController)
                .action("getVpnStatus", HttpMethod.GET)
                .name("dashboard.vpn.getVpnStatus.route");
        // updateVpnStatus /anonymous/vpn/profiles/status/me
        server
                .uri("/api/dashboard/vpn/profiles/status/{device}", openVpnController)
                .action("getVpnStatusByDevice", HttpMethod.GET)
                .name("dashboard.vpn.getVpnStatusByDevice.route");
        // setVpnActivationState
        server
                .uri("/api/dashboard/vpn/profiles/{id}/status/{device}", openVpnController)
                .action("setVpnDeviceStatus", HttpMethod.PUT)
                .name("dashboard.vpn.setVpnDeviceStatus.route");

        server // Similar to the one above - but used in a different place
                .uri("/api/dashboard/vpn/profiles/{id}/status-this", openVpnController)
                .action("setVpnThisDeviceStatus", HttpMethod.PUT)
                .name("dashboard.vpn.profile.status.device.set");// Called from squid error page

        server
                .uri("/api/dashboard/tor/config", anonymousController)
                .action("getConfig", HttpMethod.GET)
                .name("dashboard.tor.getConfig.route");
        server
                .uri("/api/dashboard/tor/config", anonymousController)
                .action("putConfig", HttpMethod.PUT)
                .name("dashboard.tor.putConfig.route");
        server
                .uri("/api/dashboard/tor/device/showwarnings", deviceController)
                .action("getShowWarnings", HttpMethod.GET)
                .name("dashboard.tor.device.getShowWarnings.route");
        server
                .uri("/api/dashboard/tor/device/showwarnings", deviceController)
                .action("postShowWarnings", HttpMethod.POST)
                .name("dashboard.tor.device.postShowWarnings.route");
        server
                .uri("/api/dashboard/tor/checkservices", anonymousController)
                .action("getTorCheckServices", HttpMethod.GET)
                .name("dashboard.tor.checkservices.route");
        server
                .uri("/api/dashboard/tor/newidentity", anonymousController)
                .action("getNewTorIdentity", HttpMethod.PUT)
                .name("dashboard.tor.getNewTorIdentity.route");

        // ** Dashboard: Cloaking
        server
                .uri("/api/dashboard/useragent/list", userAgentController)
                .action("getAgentList", HttpMethod.GET)
                .name("dashboard.useragent.getAgentList.route");
        server
                .uri("/api/dashboard/useragent/cloaked", userAgentController)
                .action("getCloakedUserAgentByDeviceId", HttpMethod.GET)
                .name("dashboard.useragent.getCloakedUserAgentByDeviceId.route");
        server
                .uri("/api/dashboard/useragent/cloaked", userAgentController)
                .action("setCloakedUserAgentByDeviceId", HttpMethod.PUT)
                .name("dashboard.useragent.setCloakedUserAgentByDeviceId.route");

        // ** Dashboard: SSL
        server
                .uri("/api/dashboard/ssl/whitelist", sslController)
                .action("addUrlToSSLWhitelist", HttpMethod.POST)
                .name("dashboard.whitelist.set.route");

        // ** Error pages
        server
                .uri("/api/dashboard/filterlists", filterListsController)
                .action("getFilterLists", HttpMethod.GET)
                .name("dashboard.filterlists.get.route");

        // Domain Recording
        server
                .uri("/api/dashboard/domain/recorder/{deviceId}", domainRecorderController)
                .action("getRecordedDomains", HttpMethod.GET)
                .name("dashboard.domainRecorder.get.route")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);

        server
                .uri("/api/dashboard/domain/recorder/{deviceId}", domainRecorderController)
                .action("resetRecording", HttpMethod.DELETE)
                .name("dashboard.domainRecorder.reset.route")
                .flag(DashboardAuthorizationProcessor.VERIFY_DEVICE_ID);
    }

    private void addAdminDashboardRoutes() {
        server
                .uri("/api/admindashboard/devices", deviceController)
                .action("getAllDevices", HttpMethod.GET)
                .name("admindashboard.devices.get");

        // Only accessible with valid token (pw must have been entered for admindashboard)
        server
                .uri("/api/admindashboard/authentication/renew/{appContext}", authenticationController)
                .action("renewToken", HttpMethod.GET)
                .name("admindashboard.authentication.renew.route");

    }

    private void addPageContextRoutes() {
        server
                .uri("/pagecontext/{id}/top", pageContextController)
                .action("reportTopContent", HttpMethod.POST)
                .name("public.pagecontext.top");
    }

    private void addReminderRoutes() {
        server
                .uri("/api/advice/reminder", reminderController)
                .action("getExpirationDate", HttpMethod.GET)
                .name("advice.reminder.get");
        server
                .uri("/api/advice/reminder", reminderController)
                .action("setNextReminder", HttpMethod.POST)
                .name("advice.reminder.post");
    }

    private void addConnectionCheckRoutes() {
        // Publicly available
        server
                .uri("/api/check/route", connectionCheckController)
                .action("routingTest", HttpMethod.GET)
                .name("public.connectioncheck.route");
    }

    private void addConfigurationBackupRoutes() {
        server
                .uri("/api/configbackup/export/eblocker-config.eblcfg", configBackupController)
                .action("exportConfiguration", HttpMethod.GET)
                .name("adminconsole.configbackup.export")
                .noSerialization();
        server
                .uri("/api/configbackup/import", configBackupController)
                .action("importConfiguration", HttpMethod.PUT)
                .name("adminconsole.configbackup.import");
    }

    private void addFeatureToggleRoutes() {
        server
                .uri("/api/featuretoggle/{name}", featureToggleController)
                .action("getFeatureToggle", HttpMethod.GET)
                .name("adminconsole.featuretoggle.get");
    }

    private void addBlockerRoutes() {
        server
                .uri("/api/blockers/", blockerController)
                .action("getBlockers", HttpMethod.GET)
                .name("adminconsole.blocker.getAll");

        server
                .uri("/api/blockers/{id}", blockerController)
                .action("getBlockerById", HttpMethod.GET)
                .name("adminconsole.blocker.get");

        server
                .uri("/api/blockers/", blockerController)
                .action("createBlocker", HttpMethod.POST)
                .name("adminconsole.blocker.post");

        server
                .uri("/api/blockers/{id}", blockerController)
                .action("updateBlocker", HttpMethod.PUT)
                .name("adminconsole.blocker.put");

        server
                .uri("/api/blockers/{id}", blockerController)
                .action("removeBlocker", HttpMethod.DELETE)
                .name("adminconsole.blocker.delete");
    }

    /**
     * Preprocessor
     */
    @Override
    public void process(Request request) {
        request.putAttachment("transactionIdentifier", new HttpTransactionIdentifier(request));
    }
}
