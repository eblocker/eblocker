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
package org.eblocker.server.common.squid;

import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.Environment;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.Ip4Address;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.network.NetworkServices;
import org.eblocker.server.common.squid.acl.ConfigurableDeviceFilterAcl;
import org.eblocker.server.common.squid.acl.ConfigurableDeviceFilterAclFactory;
import org.eblocker.server.common.squid.acl.SquidAcl;
import org.eblocker.server.common.ssl.EblockerCa;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.ssl.SslTestUtils;
import org.eblocker.server.common.system.ScriptRunner;
import org.eblocker.server.http.security.JsonWebToken;
import org.eblocker.server.http.security.JsonWebTokenHandler;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.OpenVpnServerService;
import org.eblocker.server.http.service.TestClock;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.eblocker.server.icap.resources.SimpleResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class SquidConfigControllerTest {

    private static final String VPN_ACL_DIR_PATH = "vpn";
    private static final String X_FORWARD_IPS = "10.0.0.0/8, 1.2.3.4";
    private static final String CACHE_LOG = "/var/log/squid/cache.log";
    private static final String CONTROL_BAR_HOST = "controlbar.eblocker.com";
    private static final String CONTROL_BAR_FALLBACK_IP = "169.254.93.109";
    private static final String EBLOCKER_DNS_NAMES = "eblocker.box, block.box";

    private SquidConfigController controller;
    private String outputConfigFile;
    private String mimeAclFilePath;
    private String xForwardDomainsPath = "xforward.domains.acl";
    private String xForwardIpsPath = "xforward.ips.acl";
    private String sslKeyFilePath;
    private String sslCertFilePath;
    private Integer graceTimeBeforeReloads = 200;
    private Integer minimumTimeBetweenReloads = 2000;
    private ScriptRunner scriptRunner;
    private final String squidReconfigureScript = "squidReconfigureScript";
    private final String squidClearCertCacheScript = "squidClearCertCacheScript";
    private final String squidWorkers = "3";
    private ScheduledExecutorService executorService;
    private TestClock clock;
    private DataSource dataSource;
    private DeviceService deviceService;
    private SslService sslService;
    private SslService.SslStateListener sslStateListener;
    private EblockerCa eblockerCa;
    private Environment environment;

    private SquidAcl torClientsAcl;
    private SquidAcl sslClientsAcl;
    private SquidAcl disabledClientsAcl;
    private SquidAcl mobileClientsAcl;
    private SquidAcl mobileClientsPrivateNetworkAccessAcl;
    private ConfigurableDeviceFilterAcl filteredClientsAcl;

    private Map<String, ConfigurableDeviceFilterAcl> squidAclMocks = new HashMap<>();

    @Before
    public void setUp() throws Exception {
        mimeAclFilePath = makeTempPath("mimeAclFilePath");
        xForwardDomainsPath = makeTempPath("xfordward.domains.acl");
        xForwardIpsPath = makeTempPath("xfordward.Ips.acl");
        outputConfigFile = makeTempPath("squid-eblocker.conf");
        sslKeyFilePath = makeTempPath("ssl.key");
        sslCertFilePath = makeTempPath("ssl.cert");

        clock = new TestClock(LocalDateTime.now());
        scriptRunner = Mockito.mock(ScriptRunner.class);
        dataSource = Mockito.mock(DataSource.class);

        executorService = Mockito.mock(ScheduledExecutorService.class);

        // simulate that SSL is enabled and certificates are ready:
        sslService = Mockito.mock(SslService.class);
        Mockito.doAnswer(im -> sslStateListener = im.getArgument(0)).when(sslService).addListener(Mockito.any(SslService.SslStateListener.class));
        Mockito.when(sslService.isSslEnabled()).thenReturn(true);
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        eblockerCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        Mockito.when(sslService.getCa()).thenReturn(eblockerCa);

        executorService = Mockito.mock(ScheduledExecutorService.class);

        deviceService = Mockito.mock(DeviceService.class);
        environment = Mockito.mock(Environment.class);

        disabledClientsAcl = Mockito.mock(SquidAcl.class);
        mobileClientsAcl = Mockito.mock(SquidAcl.class);
        mobileClientsPrivateNetworkAccessAcl = Mockito.mock(SquidAcl.class);
        torClientsAcl = Mockito.mock(SquidAcl.class);
        sslClientsAcl = Mockito.mock(SquidAcl.class);
        filteredClientsAcl = Mockito.mock(ConfigurableDeviceFilterAcl.class);

        createController(false);
    }

    private String makeTempPath(String name) throws IOException {
        File tempFile = File.createTempFile(name, null);
        tempFile.delete();
        tempFile.deleteOnExit();
        return tempFile.getAbsolutePath();
    }

    @After
    public void tearDown() {
    }

    @Test
    public void initTestEnabled() throws Exception {
        // verify listener has been registered
        Assert.assertNotNull(sslStateListener);

        // make ssl-initialized callback
        sslStateListener.onInit(true);

        // verify that the init called from ssl context writes the necessary config files for the squid:
        compareToReference(outputConfigFile, "squid-eblocker-embedded.conf");
        compareToReference(mimeAclFilePath, "mimetypes.conf");
        compareToReference(xForwardDomainsPath, "xforward.domains.acl");
        compareToReference(xForwardIpsPath, "xforward.ips.acl");

        verify(torClientsAcl).update();
        verify(sslClientsAcl).update();
        verify(disabledClientsAcl).update();
        verify(filteredClientsAcl).update();
        verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));

        // verify correct key / cert has been written to disk
        Assert.assertTrue(Files.exists(Paths.get(sslKeyFilePath)));
        Assert.assertTrue(Files.exists(Paths.get(sslCertFilePath)));
        Assert.assertEquals(eblockerCa.getKey(), PKI.loadPrivateKey(new FileInputStream(sslKeyFilePath)));
        Assert.assertEquals(eblockerCa.getCertificate(), PKI.loadCertificate(new FileInputStream(sslCertFilePath)));
    }

    @Test
    public void initTestSslDisabled() {
        Mockito.when(sslService.getCa()).thenReturn(null);

        // verify listener has been registered
        Assert.assertNotNull(sslStateListener);

        // make ssl-initialized callback
        sslStateListener.onInit(false);

        // verify that the init called from ssl context writes the necessary config files for the squid:
        compareToReference(outputConfigFile, "squid-eblocker-no-ssl.conf");
        compareToReference(mimeAclFilePath, "mimetypes.conf");
        compareToReference(xForwardDomainsPath, "xforward.domains.acl");
        compareToReference(xForwardIpsPath, "xforward.ips.acl");

        verify(torClientsAcl).update();
        verify(sslClientsAcl).update();
        verify(disabledClientsAcl).update();
        verify(filteredClientsAcl).update();
        verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));

        // verify keys has not been written
        Assert.assertFalse(Files.exists(Paths.get(sslKeyFilePath)));
        Assert.assertFalse(Files.exists(Paths.get(sslCertFilePath)));
    }

    @Test
    public void initTestServerMode() {
        // Overrride default controller
        createController(true);
        // make ssl-initialized callback
        sslStateListener.onInit(true);

        // verify that the init called from ssl context writes the necessary config files for the squid:
        compareToReference(outputConfigFile, "squid-eblocker-server.conf");
    }

    @Test
    public void initCacheLogEnabledTest() {
        // set ssl recording as enabled
        Mockito.when(dataSource.getSslRecordErrors()).thenReturn(true);

        // Overrride default controller
        createController(false);

        // make ssl-initialized callback
        sslStateListener.onInit(true);

        // verify that the init called from ssl context writes the necessary config files for the squid:
        compareToReference(outputConfigFile, "squid-eblocker-cache-log.conf");
    }

    @Test
    public void testCaChange() throws Exception {
        // verify listener has been registered
        Assert.assertNotNull(sslStateListener);

        // make ssl-initialized callback
        sslStateListener.onInit(true);

        // verify correct key / cert has been written to disk
        Assert.assertTrue(Files.exists(Paths.get(sslKeyFilePath)));
        Assert.assertTrue(Files.exists(Paths.get(sslCertFilePath)));
        Assert.assertEquals(eblockerCa.getKey(), PKI.loadPrivateKey(new FileInputStream(sslKeyFilePath)));
        Assert.assertEquals(eblockerCa.getCertificate(), PKI.loadCertificate(new FileInputStream(sslCertFilePath)));

        // change ca
        EblockerCa alternativeCa = new EblockerCa(SslTestUtils.loadCertificateAndKey(SslTestUtils.ALTERNATIVE_CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD));
        Mockito.when(sslService.getCa()).thenReturn(alternativeCa);
        sslStateListener.onCaChange();
        Assert.assertEquals(alternativeCa.getKey(), PKI.loadPrivateKey(new FileInputStream(sslKeyFilePath)));
        Assert.assertEquals(alternativeCa.getCertificate(), PKI.loadCertificate(new FileInputStream(sslCertFilePath)));
        verify(scriptRunner).runScript(squidClearCertCacheScript);
    }

    @Test
    public void javaScriptTest() {
        // init service
        Assert.assertNotNull(sslStateListener);
        sslStateListener.onInit(true);

        controller.setSendJavascriptToIcapserver(true);
        compareToReference(mimeAclFilePath, "mimetypes-with-javascript.conf");
        // expecting two runs as it's already scheduled once in constructor
        verify(executorService, times(2)).schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testMinimumTimeBetweenReloads() {
        // init service
        Assert.assertNotNull(sslStateListener);
        sslStateListener.onInit(true);

        // forget initial run after initialization
        Mockito.reset(executorService);

        // create mock future
        MockFuture future = new MockFuture();
        Mockito.when(executorService.schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class)))
                .thenReturn(future);
        // run first squid reload
        clock.setInstant(Instant.now());
        controller.tellSquidToReloadConfig();

        // reload must have been scheduled after grace period
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        InOrder inOrder = Mockito.inOrder(executorService);
        inOrder.verify(executorService).schedule(captor.capture(), Mockito.eq((long) graceTimeBeforeReloads), Mockito.eq(TimeUnit.MILLISECONDS));
        // actually run update
        captor.getValue().run();

        // run second reload within grace period
        clock.setInstant(clock.instant().plus(100, ChronoUnit.MILLIS));
        future.setDelay(100L);  // previous reload (1) is still in grace period
        controller.tellSquidToReloadConfig();
        inOrder.verifyNoMoreInteractions();

        // run third reload after grace period but within minimum reload time
        clock.setInstant(clock.instant().plus(200, ChronoUnit.MILLIS));
        future.setDelay(-100L);  // reload (1) is in progress
        controller.tellSquidToReloadConfig();
        inOrder.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq(2000L), Mockito.eq(TimeUnit.MILLISECONDS));

        // run fourth reload within minimum reload time of first one -> should be ignored as one is already scheduled
        clock.setInstant(clock.instant().plus(100, ChronoUnit.MILLIS));
        future.setDelay(1900L); // previous scheduled reload (3) has not been started yet
        controller.tellSquidToReloadConfig();
        inOrder.verifyNoMoreInteractions();

        // run fifth reload after minimum reload time
        clock.setInstant(clock.instant().plus(2 * minimumTimeBetweenReloads, ChronoUnit.MILLIS));
        future.setDone(true);
        controller.tellSquidToReloadConfig();
        inOrder.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq((long) graceTimeBeforeReloads), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testScheduledReload() throws IOException, InterruptedException {
        // init service
        Assert.assertNotNull(sslStateListener);
        sslStateListener.onInit(true);

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).schedule(captor.capture(), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
        captor.getValue().run();
        Mockito.verify(scriptRunner).runScript(squidReconfigureScript);
    }

    @Test
    public void testVpnAclUpdates() throws IOException, InterruptedException {
        Set<Device> devices = Collections.singleton(new Device());
        controller.updateVpnDevicesAcl(1, devices);

        Assert.assertNotNull(squidAclMocks.get("vpn/vpn-1.acl"));
        Mockito.verify(squidAclMocks.get("vpn/vpn-1.acl")).setDevices(devices);
        Mockito.verify(squidAclMocks.get("vpn/vpn-1.acl")).update();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).schedule(captor.capture(), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testFilteredClientsAclUpdates() {
        Set<Device> devices = Collections.singleton(new Device());
        controller.updateDomainFilteredDevices(devices);

        Mockito.verify(filteredClientsAcl).setDevices(devices);
        Mockito.verify(filteredClientsAcl).update();

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService).schedule(captor.capture(), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    @Test
    public void testDeviceChangeUpdateAclNoChange() {
        ArgumentCaptor<DeviceService.DeviceChangeListener> changeListenerCaptor = ArgumentCaptor.forClass(DeviceService.DeviceChangeListener.class);
        Mockito.verify(deviceService).addListener(changeListenerCaptor.capture());

        changeListenerCaptor.getValue().onChange(new Device());

        verify(torClientsAcl).update();
        verify(sslClientsAcl).update();
        verify(disabledClientsAcl).update();
        verify(filteredClientsAcl).update();
        verify(mobileClientsAcl).update();
        verify(mobileClientsPrivateNetworkAccessAcl).update();
        Mockito.verifyZeroInteractions(executorService);
    }

    @Test
    public void testDeviceChangeUpdateAclChange() {
        SquidAcl[] aclMocks = { torClientsAcl, sslClientsAcl, disabledClientsAcl, filteredClientsAcl };

        ArgumentCaptor<DeviceService.DeviceChangeListener> changeListenerCaptor = ArgumentCaptor.forClass(DeviceService.DeviceChangeListener.class);
        Mockito.verify(deviceService).addListener(changeListenerCaptor.capture());

        for (int i = 0; i < aclMocks.length; ++i) {
            for (int j = 0; j < aclMocks.length; ++j) {
                if (i != j) {
                    Mockito.reset(aclMocks[j]);
                } else {
                    Mockito.when(aclMocks[j].update()).thenReturn(true);
                }
            }

            changeListenerCaptor.getValue().onChange(new Device());
        }

        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(executorService, Mockito.times(aclMocks.length)).schedule(captor.capture(), Mockito.anyLong(), Mockito.eq(TimeUnit.MILLISECONDS));
    }

    private void compareToReference(String file, String reference) {
        String expected = ResourceHandler.load(new SimpleResource(squidTestdata(reference)));
        String result = ResourceHandler.load(new SimpleResource(file));
        assertEquals(expected, result);
    }

    private String squidTestdata(String name) {
        return "classpath:test-data/squid/" + name;
    }

    private void createController(boolean serverMode) {
        String confTemplateFilePath = squidTestdata("ssl-template.conf");
        String confStaticFilePath = squidTestdata("static.conf");
        String confSslExclusivePath = squidTestdata("ssl.exclusive.conf");
        String confNoSslExclusivePath = squidTestdata("noSsl.exclusive.conf");

        NetworkInterfaceWrapper networkInterface = Mockito.mock(NetworkInterfaceWrapper.class);
        JsonWebTokenHandler jsonWebTokenHandler = Mockito.mock(JsonWebTokenHandler.class);
        NetworkServices networkServices = Mockito.mock(NetworkServices.class);
        OpenVpnServerService openVpnServerService = Mockito.mock(OpenVpnServerService.class);
        Mockito.when(openVpnServerService.isOpenVpnServerEnabled()).thenReturn(serverMode);
        Mockito.when(environment.isServer()).thenReturn(serverMode);

        ConfigurableDeviceFilterAclFactory squidAclFactory = path -> {
            ConfigurableDeviceFilterAcl acl = Mockito.mock(ConfigurableDeviceFilterAcl.class);
            squidAclMocks.put(path, acl);
            return acl;
        };

        // simulate dynamic configuration
        when(networkInterface.getFirstIPv4Address()).thenReturn(Ip4Address.parse("192.168.178.42"));

        JsonWebToken jwt = Mockito.mock(JsonWebToken.class);
        when(jwt.getToken()).thenReturn("unit-test-token");
        when(jsonWebTokenHandler.generateSquidToken()).thenReturn(jwt);

        controller = new SquidConfigController(
                sslClientsAcl,
                torClientsAcl,
                disabledClientsAcl,
                filteredClientsAcl,
                mobileClientsAcl,
                mobileClientsPrivateNetworkAccessAcl,
                VPN_ACL_DIR_PATH,
                mimeAclFilePath,
                xForwardDomainsPath,
                xForwardIpsPath,
                X_FORWARD_IPS,
                squidReconfigureScript,
                squidClearCertCacheScript,
                confTemplateFilePath,
                confStaticFilePath,
                confSslExclusivePath,
                confNoSslExclusivePath,
                outputConfigFile,
                CACHE_LOG,
                graceTimeBeforeReloads,
                minimumTimeBetweenReloads,
                sslKeyFilePath,
                sslCertFilePath,
                squidWorkers,
                CONTROL_BAR_HOST,
                CONTROL_BAR_FALLBACK_IP,
                EBLOCKER_DNS_NAMES,
                clock, scriptRunner, dataSource, sslService,
                networkInterface, jsonWebTokenHandler, executorService, networkServices,
                deviceService, squidAclFactory,
                openVpnServerService,
                environment);
    }

    private static class MockFuture<V> implements ScheduledFuture<V> {
        private boolean done;
        private long delay;

        @Override
        public long getDelay(TimeUnit unit) {
            return delay;
        }

        public void setDelay(long delay) {
            this.delay = delay;
        }

        @Override
        public int compareTo(Delayed o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        @Override
        public V get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}
