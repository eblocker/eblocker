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

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.ProvisionException;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;
import org.eblocker.server.common.EblockerModule;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.JedisConsistencyCheck;
import org.eblocker.server.common.data.RedisBackupService;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.migrations.Migrations;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.ssl.SslTestListeners;
import org.eblocker.server.common.startup.StartupContractViolation;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.common.startup.SubSystemServiceIndex;
import org.eblocker.server.common.startup.SubSystemShutdown;
import org.eblocker.server.common.status.StartupStatusReporter;
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
import org.eblocker.server.http.controller.impl.AnonymousControllerImpl;
import org.eblocker.server.http.controller.impl.AppWhitelistModuleControllerImpl;
import org.eblocker.server.http.controller.impl.AuthenticationControllerImpl;
import org.eblocker.server.http.controller.impl.BlockerControllerImpl;
import org.eblocker.server.http.controller.impl.ConfigurationBackupControllerImpl;
import org.eblocker.server.http.controller.impl.ConnectionCheckControllerImpl;
import org.eblocker.server.http.controller.impl.ControlBarControllerImpl;
import org.eblocker.server.http.controller.impl.CustomDomainFilterConfigControllerImpl;
import org.eblocker.server.http.controller.impl.CustomerInfoControllerImpl;
import org.eblocker.server.http.controller.impl.DashboardCardControllerImpl;
import org.eblocker.server.http.controller.impl.DeviceControllerImpl;
import org.eblocker.server.http.controller.impl.DeviceRegistrationControllerImpl;
import org.eblocker.server.http.controller.impl.DnsControllerImpl;
import org.eblocker.server.http.controller.impl.DomainBlockingControllerImpl;
import org.eblocker.server.http.controller.impl.DomainRecorderControllerImpl;
import org.eblocker.server.http.controller.impl.DomainWhiteListControllerImpl;
import org.eblocker.server.http.controller.impl.EventControllerImpl;
import org.eblocker.server.http.controller.impl.FactoryResetControllerImpl;
import org.eblocker.server.http.controller.impl.FeatureControllerImpl;
import org.eblocker.server.http.controller.impl.FeatureToggleControllerImpl;
import org.eblocker.server.http.controller.impl.FilterControllerImpl;
import org.eblocker.server.http.controller.impl.FilterStatisticsControllerImpl;
import org.eblocker.server.http.controller.impl.LanguageControllerImpl;
import org.eblocker.server.http.controller.impl.LedSettingsControllerImpl;
import org.eblocker.server.http.controller.impl.MessageCenterControllerImpl;
import org.eblocker.server.http.controller.impl.MobileConnectionCheckControllerImpl;
import org.eblocker.server.http.controller.impl.MobileDnsCheckControllerImpl;
import org.eblocker.server.http.controller.impl.NetworkControllerImpl;
import org.eblocker.server.http.controller.impl.OpenVpnControllerImpl;
import org.eblocker.server.http.controller.impl.OpenVpnServerControllerImpl;
import org.eblocker.server.http.controller.impl.PageContextControllerImpl;
import org.eblocker.server.http.controller.impl.ParentalControlControllerImpl;
import org.eblocker.server.http.controller.impl.ParentalControlFilterListsControllerImpl;
import org.eblocker.server.http.controller.impl.ProductMigrationControllerImpl;
import org.eblocker.server.http.controller.impl.RecordingControllerImpl;
import org.eblocker.server.http.controller.impl.RedirectControllerImpl;
import org.eblocker.server.http.controller.impl.ReminderControllerImpl;
import org.eblocker.server.http.controller.impl.SSLControllerImpl;
import org.eblocker.server.http.controller.impl.SettingsControllerImpl;
import org.eblocker.server.http.controller.impl.SetupWizardControllerImpl;
import org.eblocker.server.http.controller.impl.SplashControllerImpl;
import org.eblocker.server.http.controller.impl.TasksControllerImpl;
import org.eblocker.server.http.controller.impl.TimestampControllerImpl;
import org.eblocker.server.http.controller.impl.TimezoneControllerImpl;
import org.eblocker.server.http.controller.impl.TosControllerImpl;
import org.eblocker.server.http.controller.impl.TransactionRecorderControllerImpl;
import org.eblocker.server.http.controller.impl.UpdateControllerImpl;
import org.eblocker.server.http.controller.impl.UserAgentControllerImpl;
import org.eblocker.server.http.controller.impl.UserControllerImpl;
import org.eblocker.server.http.controller.wrapper.ControllerWrapper;
import org.eblocker.server.http.security.DashboardAuthorizationProcessor;
import org.eblocker.server.http.security.DashboardAuthorizationProcessorImpl;
import org.eblocker.server.http.service.ShutdownService;
import org.eblocker.server.http.service.SystemStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.bridge.SLF4JBridgeHandler;

import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class EblockerServerApp {

    private static final Logger LOG = LoggerFactory.getLogger(EblockerServerApp.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private Injector injector;

    private SystemStatusService systemStatusService;
    private StartupStatusReporter statusReporter;
    private SubSystemServiceIndex subSystemServicesIndex;

    private EventLogger eventLogger;
    private ShutdownService shutdownService;

    // For the final overall system state
    private boolean haveWarnings = false;

    public static void main(String[] args) throws Exception {
        EblockerServerApp app = new EblockerServerApp();
        SLF4JBridgeHandler.removeHandlersForRootLogger();
        SLF4JBridgeHandler.install();
        java.util.logging.Logger.getLogger("").setLevel(Level.FINEST);

        //
        // Boot Phase
        // ==========
        // It should be taken care, that it is as unlikely as possible that
        // anything could go wrong during the boot phase.
        // If it does, the application will stop and there will be no direct
        // feedback to the user.
        //
        try {
            app.initBootPhase();
            app.startHttpServer();

        } catch (Throwable t) {
            // We catch Throwable and not just Exception, because we want to be sure that
            // the shutdown hooks are being called and all threads are stopped.
            app.startupFailed(t);
            System.exit(1);

        }

        //
        // Runtime Phase
        // =============
        // At least the HTTP server is now running with a few REST controllers.
        // If anything goes wrong from here on, we should not stop the core process,
        // but provide feedback to the user.
        // It might even be possible to provide hints and options to fix the system.
        //
        try {
            app.initRuntimePhase();
            app.openDatabaseConnection();
            app.wireEventListeners();
            app.startBackgroundTasks();
            app.startIcapServer();
            app.startNetworkStateMachine();
            app.startHttpsServer();
            app.startServices();
            app.injectRESTController();

            //
            // Start-up complete
            //
            app.startupCompleted();

        } catch (Exception e) {
            app.startupFailed(e);

        }

    }

    // ---

    private void initBootPhase() throws IOException {
        LOG.info("Initiating eBlocker Core start-up...");
        injector = Guice.createInjector(new EblockerModule());

        subSystemServicesIndex = injector.getInstance(SubSystemServiceIndex.class);
        subSystemServicesIndex.scan(injector.getBindings());
        systemStatusService = injector.getInstance(SystemStatusService.class);
        systemStatusService.setExecutionState(ExecutionState.BOOTING);
        statusReporter = injector.getInstance(StartupStatusReporter.class);
        shutdownService = injector.getInstance(ShutdownService.class);
        statusReporter.consoleStarted();

        systemStatusService
                .starting(SubSystem.HTTP_SERVER)
                .starting(SubSystem.HTTPS_SERVER)
                .starting(SubSystem.SERVICES)
                .starting(SubSystem.REST_SERVER)
                .starting(SubSystem.DATABASE_CLIENT)
                .starting(SubSystem.EVENT_LISTENER)
                .starting(SubSystem.BACKGROUND_TASKS)
                .starting(SubSystem.NETWORK_STATE_MACHINE)
                .starting(SubSystem.ICAP_SERVER)
                .starting(SubSystem.EBLOCKER_CORE);
    }

    private void startHttpServer() {
        STATUS.info("Starting HTTP server...");
        doStartHttpServer();
        statusReporter.testNetworkInterface();
        // No need to catch exception and set error status:
        // If starting the HTTP server fails, the user will anyway not get any feedback.
        String key = "httpPort";
        systemStatusService.ok(SubSystem.HTTP_SERVER, key, injector.getInstance(Key.get(Integer.class, Names.named(key))));
    }

    private void initRuntimePhase() {
        STATUS.info("Initiating eBlocker Core runtime...");
        eventLogger = injector.getInstance(EventLogger.class);
        shutdownService.setEventLogger(eventLogger);
    }

    private void openDatabaseConnection() throws RedisBackupService.RedisServiceException {
        STATUS.info("Opening database connection and checking schema version...");
        try {
            initSubSystemServices(SubSystem.DATABASE_CLIENT);

            RedisBackupService redisBackupService = injector.getInstance(RedisBackupService.class);
            redisBackupService.check();

            JedisConsistencyCheck consistencyCheck = injector.getInstance(JedisConsistencyCheck.class);
            consistencyCheck.run();

            String version = doCheckSchemaVersion();
            systemStatusService.ok(SubSystem.DATABASE_CLIENT, "version", version);
            shutdownService.setDataSource(injector.getInstance(DataSource.class));
        } catch (Exception e) {
            systemStatusService.error(SubSystem.DATABASE_CLIENT, e.getMessage());
            // w/o the database, we cannot start the console
            throw e;
        }
    }

    private void wireEventListeners() {
        STATUS.info("Wiring event listeners...");
        try {
            doWireParentalControlEventListeners();
            injector.getInstance(SslTestListeners.class);
            systemStatusService.ok(SubSystem.EVENT_LISTENER);

        } catch (Exception e) {
            processSubSystemWarning("Cannot wire event listeners", SubSystem.EVENT_LISTENER, e);

        }
    }

    private void startBackgroundTasks() {
        STATUS.info("Starting background services...");
        try {
            doStartBackgroundTasks();
            systemStatusService.ok(SubSystem.BACKGROUND_TASKS);

        } catch (Exception e) {
            processSubSystemWarning("Cannot start background tasks", SubSystem.BACKGROUND_TASKS, e);

        }
    }

    private void startIcapServer() {
        STATUS.info("Starting ICAP server...");
        try {
            doStartIcapServer();
            systemStatusService.ok(SubSystem.ICAP_SERVER);

        } catch (Exception e) {
            processSubSystemWarning("Cannot start ICAP server", SubSystem.ICAP_SERVER, e);

        }
    }

    private void startNetworkStateMachine() {
        STATUS.info("Starting network state machine...");
        try {
            doStartNetworkStateMachine();
            systemStatusService.ok(SubSystem.NETWORK_STATE_MACHINE);

        } catch (Exception e) {
            processSubSystemWarning("Cannot start network state machine", SubSystem.NETWORK_STATE_MACHINE, e);

        }
    }

    private void startHttpsServer() {
        STATUS.info("Starting HTTPS server (if available)...");
        try {
            boolean activated = doStartSslService();
            if (activated) {
                String key = "httpsPort";
                systemStatusService.ok(SubSystem.HTTPS_SERVER, key, injector.getInstance(Key.get(Integer.class, Names.named(key))));
            } else {
                systemStatusService.off(SubSystem.HTTPS_SERVER);
            }
        } catch (Exception e) {
            processSubSystemWarning("Cannot HTTPS server", SubSystem.HTTPS_SERVER, e);

        }
    }

    private void injectRESTController() {
        STATUS.info("Creating REST controller...");
        try {
            doInjectRESTController();
            systemStatusService.ok(SubSystem.REST_SERVER);

        } catch (Exception e) {
            processSubSystemWarning("Cannot create REST controller", SubSystem.REST_SERVER, e);

        }
    }

    private void startServices() {
        STATUS.info("Starting internal services...");
        try {
            doStartServices();
            systemStatusService.ok(SubSystem.SERVICES);
        } catch (Exception e) {
            processSubSystemWarning("Cannot start internal services", SubSystem.SERVICES, e);
        }
    }

    private void startupCompleted() {
        if (haveWarnings) {
            STATUS.warn("eBlocker Core started with warnings!");
            systemStatusService.setExecutionState(ExecutionState.RUNNING);
            systemStatusService.warn(SubSystem.EBLOCKER_CORE);
            statusReporter.startupCompletedWithWarning(systemStatusService.getWarnings());
            eventLogger.log(Events.serverIcapServerStartedWithWarnings());

        } else {
            STATUS.info("eBlocker Core start-up complete!");
            systemStatusService.setExecutionState(ExecutionState.RUNNING);
            systemStatusService.ok(SubSystem.EBLOCKER_CORE);
            statusReporter.startupCompleted();
            eventLogger.log(Events.serverIcapServerStarted());
        }
        // Prepare system status service to also handle UPDATING status
        systemStatusService.setSystemUpdater(injector.getInstance(SystemUpdater.class));
    }

    private void startupFailed(Throwable t) {
        LOG.error("Starting eBlocker Core failed", t);
        systemStatusService.setExecutionState(ExecutionState.ERROR);
        systemStatusService.error(SubSystem.EBLOCKER_CORE);
        if (statusReporter != null) {
            statusReporter.startupFailed(t);
        }
    }

    private void processSubSystemWarning(String msg, SubSystem subSystem, Exception e) {
        LOG.error(msg, e);
        systemStatusService.error(subSystem, e.getMessage());
        systemStatusService.addWarning(e);
        haveWarnings = true;
        if (e instanceof ProvisionException) {
            ProvisionException pe = (ProvisionException) e;
            Optional<Throwable> o = pe.getErrorMessages().stream().filter(m -> m.getCause() instanceof StartupContractViolation).map(Message::getCause).findAny();
            if (o.isPresent()) {
                throw (StartupContractViolation) o.get();
            }
        }
    }

    // ---

    private void doStartHttpServer() {
        initSubSystemServices(SubSystem.HTTP_SERVER);
    }

    private String doCheckSchemaVersion() {
        try {
            return Migrations.run();

        } catch (IOException e) {
            String msg = "Cannot check or upgrade database schema version";
            LOG.error("{}: {}", msg, e);
            throw new EblockerException(msg, e);
        }
    }

    private void doWireParentalControlEventListeners() {
        initSubSystemServices(SubSystem.EVENT_LISTENER);
    }

    private void doStartBackgroundTasks() {
        initSubSystemServices(SubSystem.BACKGROUND_TASKS);
    }

    private void doStartIcapServer() {
        initSubSystemServices(SubSystem.ICAP_SERVER);
    }

    private void doStartNetworkStateMachine() {
        initSubSystemServices(SubSystem.NETWORK_STATE_MACHINE);
    }

    private boolean doStartSslService() throws SslService.PkiException {
        initSubSystemServices(SubSystem.HTTPS_SERVER);

        SslService sslService = injector.getInstance(SslService.class);
        return sslService.isSslEnabled();
    }

    private void doStartServices() {
        initSubSystemServices(SubSystem.SERVICES);
    }

    private void doInjectRESTController() {
        injectController(AnonymousController.class, AnonymousControllerImpl.class);
        injectController(AppWhitelistModuleController.class, AppWhitelistModuleControllerImpl.class);
        injectController(AuthenticationController.class, AuthenticationControllerImpl.class);
        injectController(ControlBarController.class, ControlBarControllerImpl.class);
        injectController(DeviceController.class, DeviceControllerImpl.class);
        injectController(DeviceRegistrationController.class, DeviceRegistrationControllerImpl.class);
        injectController(DnsController.class, DnsControllerImpl.class);
        injectController(DomainBlockingController.class, DomainBlockingControllerImpl.class);
        injectController(DomainRecorderController.class, DomainRecorderControllerImpl.class);
        injectController(DomainWhiteListController.class, DomainWhiteListControllerImpl.class);
        injectController(EventController.class, EventControllerImpl.class);
        injectController(FactoryResetController.class, FactoryResetControllerImpl.class);
        injectController(FilterController.class, FilterControllerImpl.class);
        injectController(LanguageController.class, LanguageControllerImpl.class);
        injectController(MessageCenterController.class, MessageCenterControllerImpl.class);
        injectController(NetworkController.class, NetworkControllerImpl.class);
        injectController(OpenVpnController.class, OpenVpnControllerImpl.class);
        injectController(OpenVpnServerController.class, OpenVpnServerControllerImpl.class);
        injectController(PageContextController.class, PageContextControllerImpl.class);
        injectController(ParentalControlController.class, ParentalControlControllerImpl.class);
        injectController(ParentalControlFilterListsController.class, ParentalControlFilterListsControllerImpl.class);
        injectController(RecordingController.class, RecordingControllerImpl.class);
        injectController(RedirectController.class, RedirectControllerImpl.class);
        injectController(ReminderController.class, ReminderControllerImpl.class);
        injectController(SplashController.class, SplashControllerImpl.class);
        injectController(SSLController.class, SSLControllerImpl.class);
        injectController(SettingsController.class, SettingsControllerImpl.class);
        injectController(SetupWizardController.class, SetupWizardControllerImpl.class);
        injectController(TimestampController.class, TimestampControllerImpl.class);
        injectController(TimezoneController.class, TimezoneControllerImpl.class);
        injectController(TransactionRecorderController.class, TransactionRecorderControllerImpl.class);
        injectController(UpdateController.class, UpdateControllerImpl.class);
        injectController(UserAgentController.class, UserAgentControllerImpl.class);
        injectController(UserController.class, UserControllerImpl.class);
        injectController(ProductMigrationController.class, ProductMigrationControllerImpl.class);
        injectController(DashboardCardController.class, DashboardCardControllerImpl.class);
        injectController(CustomerInfoController.class, CustomerInfoControllerImpl.class);
        injectController(FeatureController.class, FeatureControllerImpl.class);
        injectController(MobileConnectionCheckController.class, MobileConnectionCheckControllerImpl.class);
        injectController(MobileDnsCheckController.class, MobileDnsCheckControllerImpl.class);
        injectController(TosController.class, TosControllerImpl.class);
        injectController(FilterStatisticsController.class, FilterStatisticsControllerImpl.class);
        injectController(CustomDomainFilterConfigController.class, CustomDomainFilterConfigControllerImpl.class);
        injectController(ConfigurationBackupController.class, ConfigurationBackupControllerImpl.class);
        injectController(LedSettingsController.class, LedSettingsControllerImpl.class);
        injectController(TasksController.class, TasksControllerImpl.class);
        injectController(FeatureToggleController.class, FeatureToggleControllerImpl.class);
        injectController(ConnectionCheckController.class, ConnectionCheckControllerImpl.class);
        injectController(BlockerController.class, BlockerControllerImpl.class);
        injectController(DashboardAuthorizationProcessor.class, DashboardAuthorizationProcessorImpl.class);
    }

    // ---

    @SuppressWarnings("unchecked")
    private <CTRL, IMPL extends CTRL> void injectController(Class<CTRL> clazz, Class<IMPL> implClazz) {
        CTRL controller = injector.getInstance(clazz);
        IMPL controllerImpl = injector.getInstance(implClazz);
        if (controller instanceof ControllerWrapper) {
            ((ControllerWrapper<IMPL>) controller).setControllerImpl(controllerImpl);
        }
    }

    private void initSubSystemServices(SubSystem subSystem) {
        LOG.debug("initializing sub-system-services for {}", subSystem);

        ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        Collection<Class<?>> serviceClasses = subSystemServicesIndex.getRegisteredServices(subSystem);
        Map<Integer, List<Class<?>>> serviceClassesByPriority = serviceClasses.stream()
                .collect(Collectors.groupingBy(c -> c.getAnnotation(SubSystemService.class).initPriority()));

        serviceClassesByPriority.keySet().stream().sorted().forEach(priority -> {
            LOG.info("Starting priority: {} classes: {}", priority, serviceClassesByPriority.get(priority));
            List<Future> futures = serviceClassesByPriority.get(priority)
                    .stream()
                    .map(c -> executor.submit(() -> {
                        LOG.debug("get instance of {}", c);
                        Object instance = injector.getInstance(c);

                        List<Method> initMethods = getDeclaredMethodsWithAnnotation(c, SubSystemInit.class);
                        initMethods.stream().forEach(method -> {
                            LOG.debug("calling subsystem init method: {} on: {}", method.getName(),
                                    instance.getClass().getName());
                            long start = System.currentTimeMillis();
                            callMethod(subSystem, instance, method);
                            long elapsed = System.currentTimeMillis() - start;
                            STATUS.info("{}/{}/{} executed in {}ms", subSystem, instance.getClass().getSuperclass().getSimpleName(), method.getName(), elapsed);
                        });

                        List<Method> shutdownMethods = getDeclaredMethodsWithAnnotation(c, SubSystemShutdown.class);
                        if (!shutdownMethods.isEmpty()) {
                            Runtime.getRuntime().addShutdownHook(
                                    new Thread(() -> shutdownMethods.forEach(method -> callMethod(subSystem, instance, method))));
                        }
                    })).collect(Collectors.toList());

            futures.forEach(future -> {
                try {
                    future.get();
                } catch (InterruptedException e) {
                    LOG.debug("interrupted", e);
                    Thread.currentThread().interrupt();
                } catch (ExecutionException e) {
                    LOG.error("Initializing subsystem {} failed!", subSystem, e);
                }
            });
        });
        executor.shutdown();
    }

    private List<Method> getDeclaredMethodsWithAnnotation(Class<?> clazz, Class<? extends Annotation> annotationClass) {
        List<Method> methods = new ArrayList<>();
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(annotationClass)) {
                methods.add(method);
            }
        }
        return methods;
    }

    private void callMethod(SubSystem subSystem, Object instance, Method method) {
        try {
            LOG.debug("{}/{}/{} calling method", subSystem, instance.getClass().getSuperclass().getSimpleName(), method.getName());
            method.invoke(instance);
        } catch (ReflectiveOperationException e) {
            LOG.error("{}/{}/{} failed", subSystem, instance.getClass().getSuperclass().getSimpleName(), method.getName(), e);
        }
    }
}
