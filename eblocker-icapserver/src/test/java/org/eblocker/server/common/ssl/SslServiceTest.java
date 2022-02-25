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
package org.eblocker.server.common.ssl;

import com.google.common.io.ByteStreams;
import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.CertificateAndKey;
import org.eblocker.crypto.pki.PKI;
import org.eblocker.server.common.data.CaOptions;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.DistinguishedName;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.eblocker.server.icap.resources.EblockerResource;
import org.eblocker.server.icap.resources.ResourceHandler;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class SslServiceTest {

    private final int caRenewWeeks = 4;
    private final int maxValidityInMonths = 24;
    private final String dnFormat = "%s/%s/%s/%s";
    private final int caKeySize = 2048;
    private Path keyStorePath;
    private Path renewalKeyStorePath;

    private CertificateAndKey unitTestCaCertificateAndKey;

    private DataSource dataSource;
    private DeviceRegistrationProperties deviceRegistrationProperties;

    private ScheduledExecutorService executorService;
    private List<MockScheduledFuture> scheduledFutures = new ArrayList<>();

    private SslService.SslStateListener listener;
    private SslService sslService;

    private int friendlyNameLength = 12;
    private String friendlyNameFallback = "eBlocker-Certificate";

    @Before
    public void setup() throws Exception {
        // setup mocks
        dataSource = Mockito.mock(DataSource.class);
        deviceRegistrationProperties = Mockito.mock(DeviceRegistrationProperties.class);

        executorService = Mockito.mock(ScheduledExecutorService.class);
        Mockito.when(executorService.schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class))).then(im -> {
            MockScheduledFuture future = new MockScheduledFuture(im.getArgument(0), TimeUnit.MILLISECONDS.convert(im.getArgument(1), im.getArgument(2)));
            scheduledFutures.add(future);
            return future;
        });

        listener = Mockito.mock(SslService.SslStateListener.class);

        // setup keystore location
        keyStorePath = Files.createTempFile("ssl-service-test-ca", ".jks");
        Files.delete(keyStorePath);

        renewalKeyStorePath = Files.createTempFile("ssl-service-test-future-ca", ".jks");
        Files.delete(renewalKeyStorePath);

        // load unit test ca
        unitTestCaCertificateAndKey = SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD);
    }

    @After
    public void tearDown() throws IOException {
        Files.deleteIfExists(keyStorePath);
        Files.deleteIfExists(renewalKeyStorePath);
    }

    @Test
    public void testInitDisabledNoCa() throws PkiException {
        sslService = createService(null);

        sslService.init();

        Assert.assertFalse(sslService.isSslEnabled());
        Assert.assertFalse(sslService.isCaAvailable());
        Assert.assertNull(sslService.getCa());
        Mockito.verify(listener).onInit(false);

        // check no expiration tasks have been scheduled
        Assert.assertEquals(0, scheduledFutures.size());
    }

    @Test
    public void testInitDisabledExistingCa() throws PkiException {
        sslService = createService(SslTestUtils.CA_RESOURCE);

        sslService.init();

        Assert.assertFalse(sslService.isSslEnabled());
        Assert.assertTrue(sslService.isCaAvailable());
        Assert.assertNotNull(sslService.getCa());
        Assert.assertEquals(unitTestCaCertificateAndKey.getCertificate(), sslService.getCa().getCertificate());
        Assert.assertEquals(unitTestCaCertificateAndKey.getKey(), sslService.getCa().getKey());
        Mockito.verify(listener).onInit(false);

        // check expiration tasks have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());
    }

    @Test(expected = PkiException.class)
    public void testInitEnabledNoCa() throws PkiException {
        Mockito.when(dataSource.getSSLEnabledState()).thenReturn(true);

        sslService = createService(null);

        sslService.init();

        // check no expiration tasks have been scheduled
        Assert.assertEquals(0, scheduledFutures.size());
    }

    @Test
    public void testInitEnabledExistingCa() throws PkiException {
        Mockito.when(dataSource.getSSLEnabledState()).thenReturn(true);

        sslService = createService(SslTestUtils.CA_RESOURCE);

        sslService.init();

        Assert.assertTrue(sslService.isSslEnabled());
        Assert.assertTrue(sslService.isCaAvailable());
        Assert.assertNotNull(sslService.getCa());
        Assert.assertEquals(unitTestCaCertificateAndKey.getCertificate(), sslService.getCa().getCertificate());
        Assert.assertEquals(unitTestCaCertificateAndKey.getKey(), sslService.getCa().getKey());
        Mockito.verify(listener).onInit(true);

        // check expiration tasks have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());
    }

    @Test
    public void testGenerateCa() throws PkiException, CryptoException {
        sslService = createService(null);

        sslService.init();

        CaOptions options = createCaOptions("testGenerateCa", 12);
        sslService.generateCa(options);
        Assert.assertTrue(sslService.isCaAvailable());
        Assert.assertNotNull(sslService.getCa());

        Assert.assertTrue(PKI.getCN(sslService.getCa().getCertificate()).contains(options.getDistinguishedName().getCommonName()));

        long expectedNotValidAfter = ZonedDateTime.now().plus(12, ChronoUnit.MONTHS).toInstant().toEpochMilli();
        long validAfter = sslService.getCa().getCertificate().getNotAfter().getTime();
        Assert.assertTrue(Math.abs(expectedNotValidAfter - validAfter) < 300000);
        Mockito.verify(listener).onCaChange();

        // check expiration tasks have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());
    }

    @Test
    public void testRegenerateCa() throws PkiException, CryptoException {
        sslService = createService(SslTestUtils.CA_RESOURCE);

        sslService.init();

        CaOptions options = createCaOptions("testGenerateCa", 12);
        sslService.generateCa(options);
        Assert.assertTrue(sslService.isCaAvailable());
        Assert.assertNotNull(sslService.getCa());

        Assert.assertTrue(PKI.getCN(sslService.getCa().getCertificate()).contains(options.getDistinguishedName().getCommonName()));

        long expectedNotValidAfter = ZonedDateTime.now().plus(12, ChronoUnit.MONTHS).toInstant().toEpochMilli();
        long validAfter = sslService.getCa().getCertificate().getNotAfter().getTime();
        Assert.assertTrue(Math.abs(expectedNotValidAfter - validAfter) < 300000);
        Mockito.verify(listener).onCaChange();

        // check scheduled expiration tasks has been canceled and new ones scheduled
        Assert.assertEquals(4, scheduledFutures.size());
        Assert.assertTrue(scheduledFutures.get(0).isCancelled());
        Assert.assertTrue(scheduledFutures.get(1).isCancelled());
        Assert.assertFalse(scheduledFutures.get(2).isCancelled());
        Assert.assertFalse(scheduledFutures.get(3).isCancelled());
    }

    @Test
    public void testInitCaExpirationNoRenewalCa() throws PkiException {
        sslService = createService(SslTestUtils.EXPIRED_CA_RESOURCE, null);

        sslService.init();

        // check expiration task have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());

        Mockito.verify(listener, Mockito.never()).onCaChange();
        Assert.assertNotNull(sslService.getCa());
        Assert.assertTrue(sslService.getCa().getCertificate().getNotAfter().after(new Date()));
        Assert.assertTrue(Files.exists(keyStorePath));
        Assert.assertFalse(Files.exists(renewalKeyStorePath));
    }

    @Test
    public void testInitCaExpirationRenewalCa() throws PkiException, IOException {
        sslService = createService(SslTestUtils.EXPIRED_CA_RESOURCE, SslTestUtils.ALTERNATIVE_CA_RESOURCE);

        sslService.init();

        // check expiration task have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());

        Mockito.verify(listener, Mockito.never()).onCaChange();
        Assert.assertNotNull(sslService.getCa());
        Assert.assertTrue(sslService.getCa().getCertificate().getNotAfter().after(new Date()));
        Assert.assertArrayEquals(ByteStreams.toByteArray(ResourceHandler.getInputStream(SslTestUtils.ALTERNATIVE_CA_RESOURCE)), Files.readAllBytes(keyStorePath));
        Assert.assertFalse(Files.exists(renewalKeyStorePath));
    }

    @Test
    public void testRenewalCaGeneration() throws PkiException {
        sslService = createService(SslTestUtils.CA_RESOURCE, null);

        sslService.init();

        // check expiration task have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());

        // we just assume the first task is the generation task
        scheduledFutures.get(0).getRunnable().run();

        Assert.assertTrue(Files.exists(renewalKeyStorePath));
        Assert.assertTrue(sslService.isRenewalCaAvailable());
        Assert.assertNotNull(sslService.getRenewalCa());
    }

    @Test
    public void testCaExpirationNoRenewalCa() throws PkiException {
        sslService = createService(SslTestUtils.CA_RESOURCE, null);

        sslService.init();

        // check expiration task have been scheduled
        Assert.assertEquals(2, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());
        Assert.assertFalse(scheduledFutures.get(1).isCancelled());

        // we just assume the first task is the generation and the second the expiration task
        scheduledFutures.get(1).getRunnable().run();

        Mockito.verify(listener).onCaChange();
        Assert.assertFalse(Files.exists(renewalKeyStorePath));
    }

    @Test
    public void testCaExpirationRenewalCa() throws PkiException, IOException {
        sslService = createService(SslTestUtils.CA_RESOURCE, SslTestUtils.ALTERNATIVE_CA_RESOURCE);

        sslService.init();

        // check only expiration task have been scheduled
        Assert.assertEquals(1, scheduledFutures.size());
        Assert.assertFalse(scheduledFutures.get(0).isCancelled());

        // run expiration task
        scheduledFutures.get(0).getRunnable().run();

        Mockito.verify(listener).onCaChange();
        Assert.assertArrayEquals(ByteStreams.toByteArray(ResourceHandler.getInputStream(SslTestUtils.ALTERNATIVE_CA_RESOURCE)), Files.readAllBytes(keyStorePath));
        Assert.assertFalse(Files.exists(renewalKeyStorePath));
    }

    @Test
    public void testEnableSsl() throws PkiException {
        sslService = createService(SslTestUtils.CA_RESOURCE);

        sslService.init();

        sslService.enableSsl();

        Mockito.verify(listener).onEnable();
        Mockito.verify(dataSource).setSSLEnabledState(true);
    }

    @Test
    public void testDisableSsl() throws PkiException {
        Mockito.when(dataSource.getSSLEnabledState()).thenReturn(true);
        sslService = createService(SslTestUtils.CA_RESOURCE);

        sslService.init();
        sslService.disableSsl();

        Mockito.verify(listener).onDisable();
        Mockito.verify(dataSource).setSSLEnabledState(false);
    }

    @Test
    public void testGetDefaultCaOptions() throws PkiException {
        sslService = createService(null);

        sslService.init();
        CaOptions options = sslService.getDefaultCaOptions();
        Assert.assertEquals(maxValidityInMonths, options.getValidityInMonths().intValue());
        Assert.assertEquals("My eBlocker", options.getDistinguishedName().getCommonName());

        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn("unit-test");
        options = sslService.getDefaultCaOptions();
        Assert.assertEquals("unit-test", options.getDistinguishedName().getCommonName());
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedGenerateCa() throws PkiException {
        sslService = createService(null);
        sslService.generateCa(new CaOptions());
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedIsSslEnabeld() throws PkiException {
        sslService = createService(null);
        sslService.isSslEnabled();
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedEnableSsl() throws PkiException {
        sslService = createService(null);
        sslService.enableSsl();
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedDisableSsl() throws PkiException {
        sslService = createService(null);
        sslService.disableSsl();
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedGetCa() throws PkiException {
        sslService = createService(null);
        sslService.getCa();
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedIsCaAvailable() throws PkiException {
        sslService = createService(null);
        sslService.isCaAvailable();
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedIsFutureCaAvailable() {
        sslService = createService(null);
        sslService.isRenewalCaAvailable();
    }

    @Test(expected = IllegalStateException.class)
    public void testUninitializedGetFutureCa() {
        sslService = createService(null);
        sslService.getRenewalCa();
    }

    @Test(expected = IllegalStateException.class)
    public void testDuplicateInit() throws PkiException {
        sslService = createService(null);
        sslService.init();
        sslService.init();
    }

    @Test
    public void testFilenameGeneration() throws PkiException {
        String cname = "cn¹²³¼½¬{[]}";
        DistinguishedName dname = new DistinguishedName();
        dname.setCommonName(cname);
        CaOptions caOptions = new CaOptions();
        caOptions.setDistinguishedName(dname);

        Mockito.when(dataSource.get(CaOptions.class)).thenReturn(caOptions);
        sslService = createService(null);

        sslService.init();

        CaOptions options = createCaOptions("cn¹²³¼½¬{[]}þ", 12);
        sslService.generateCa(options);

        String generatedFilename = sslService.generateFileNameForCertificate();

        String expectedDateString = new SimpleDateFormat("yyyy-MM-dd")
                .format(sslService.getCa().getCertificate().getNotBefore());

        String expectedFilename = "eBlocker-Certificate-cn-" + expectedDateString + ".crt";

        Assert.assertEquals(expectedFilename, generatedFilename);
    }

    @Test
    public void testFilenameGenerationFallbackRegistrationProperties() throws PkiException {
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn("fallback-name′¹²³¼½¬{[]}");

        Mockito.when(dataSource.get(CaOptions.class)).thenReturn(null);
        sslService = createService(null);

        sslService.init();

        CaOptions options = createCaOptions("cn¹²³¼½¬{[]}þ", 12);
        sslService.generateCa(options);

        String generatedFilename = sslService.generateFileNameForCertificate();

        String expectedDateString = new SimpleDateFormat("yyyy-MM-dd")
                .format(sslService.getCa().getCertificate().getNotBefore());

        String expectedFilename = "eBlocker-Certificate-fallbackname-" + expectedDateString + ".crt";

        Assert.assertEquals(expectedFilename, generatedFilename);
    }

    @Test
    public void testFilenameGenerationFallback() throws PkiException {
        Mockito.when(deviceRegistrationProperties.getDeviceName()).thenReturn("");

        Mockito.when(dataSource.get(CaOptions.class)).thenReturn(null);
        sslService = createService(null);

        sslService.init();

        CaOptions options = createCaOptions("cn¹²³¼½¬{[]}þ", 12);
        sslService.generateCa(options);

        String generatedFilename = sslService.generateFileNameForCertificate();

        String expectedDateString = new SimpleDateFormat("yyyy-MM-dd")
                .format(sslService.getCa().getCertificate().getNotBefore());

        String expectedFilename = "eBlocker-Certificate-" + expectedDateString + ".crt";

        Assert.assertEquals(expectedFilename, generatedFilename);
    }

    private SslService createService(EblockerResource caResource) {
        return createService(caResource, null);
    }

    private SslService createService(EblockerResource caResource, EblockerResource renewalCaResource) {
        if (caResource != null) {
            copyResourceToFile(caResource, keyStorePath);
        }

        if (renewalCaResource != null) {
            copyResourceToFile(renewalCaResource, renewalKeyStorePath);
        }

        SslService sslService = new SslService(keyStorePath.toString(), SslTestUtils.UNIT_TEST_CA_PASSWORD,
                maxValidityInMonths, dnFormat, caKeySize, caRenewWeeks, renewalKeyStorePath.toString(), dataSource,
                deviceRegistrationProperties, executorService, friendlyNameLength, friendlyNameFallback);
        sslService.addListener(listener);
        return sslService;
    }

    private void copyResourceToFile(EblockerResource src, Path target) {
        try (InputStream is = ResourceHandler.getInputStream(src)) {
            try (FileOutputStream os = new FileOutputStream(target.toString())) {
                ByteStreams.copy(is, os);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private CaOptions createCaOptions(String commonName, int maxValidityInMonths) {
        DistinguishedName dn = new DistinguishedName();
        dn.setCommonName(commonName);
        CaOptions options = new CaOptions();
        options.setDistinguishedName(dn);
        options.setValidityInMonths(maxValidityInMonths);
        return options;
    }

    private class MockScheduledFuture<V> implements ScheduledFuture<V> {
        private Runnable runnable;
        private long delay;
        private boolean canceled = false;

        public MockScheduledFuture(Runnable runnable, long delay) {
            this.runnable = runnable;
            this.delay = delay;
        }

        public Runnable getRunnable() {
            return runnable;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delay, unit);
        }

        @Override
        public int compareTo(Delayed o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            canceled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return canceled;
        }

        @Override
        public boolean isDone() {
            return false;
        }

        @Override
        public V get() throws InterruptedException, ExecutionException {
            throw new UnsupportedOperationException();
        }

        @Override
        public V get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
            throw new UnsupportedOperationException();
        }
    }

}
