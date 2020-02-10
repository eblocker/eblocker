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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.math.BigInteger;
import java.security.cert.X509Certificate;

public class SslCertificateClientInstallationTrackerTest {

    private final BigInteger currentSerialNumber = BigInteger.valueOf(1000);
    private final BigInteger renewalSerialNumber = BigInteger.valueOf(2000);

    private SslService sslService;
    private SslCertificateClientInstallationTracker tracker;
    private SslService.SslStateListener listener;

    @Before
    public void setup() {
        sslService = Mockito.mock(SslService.class);
        Mockito.doAnswer(im -> listener = im.getArgument(0)).when(sslService).addListener(Mockito.any(SslService.SslStateListener.class));
        Mockito.when(sslService.isCaAvailable()).thenReturn(true);
        Mockito.when(sslService.isRenewalCaAvailable()).thenReturn(true);
        EblockerCa currentCa = createEblockerCaMock(currentSerialNumber);
        EblockerCa renewalCa = createEblockerCaMock(renewalSerialNumber);
        Mockito.when(sslService.getCa()).thenReturn(currentCa);
        Mockito.when(sslService.getRenewalCa()).thenReturn(renewalCa);
    }

    @Test
    public void testInit() {
        tracker = new SslCertificateClientInstallationTracker(sslService);

        Assert.assertNotNull(listener);

        listener.onInit(true);
    }

    @Test
    public void testMarking() {
        tracker = new SslCertificateClientInstallationTracker(sslService);
        listener.onInit(true);

        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.UNKNOWN, tracker.isCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));

        tracker.markCertificateAsInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)", currentSerialNumber, false);
        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.NOT_INSTALLED, tracker.isCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));

        tracker.markCertificateAsInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)", currentSerialNumber, true);
        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.INSTALLED, tracker.isCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));
    }

    @Test
    public void testCaChange() {
        tracker = new SslCertificateClientInstallationTracker(sslService);
        listener.onInit(true);

        tracker.markCertificateAsInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)", currentSerialNumber, true);
        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.INSTALLED, tracker.isCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));

        // change ca
        EblockerCa newCa = createEblockerCaMock(BigInteger.valueOf(3000));
        Mockito.when(sslService.getCa()).thenReturn(newCa);
        listener.onCaChange();

        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.UNKNOWN, tracker.isCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));
    }

    @Test
    public void testFutureCaChange() {
        tracker = new SslCertificateClientInstallationTracker(sslService);
        listener.onInit(true);

        tracker.markCertificateAsInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)", renewalSerialNumber, true);
        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.INSTALLED, tracker.isFutureCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));

        // change ca
        EblockerCa newCa = createEblockerCaMock(BigInteger.valueOf(3000));
        Mockito.when(sslService.getRenewalCa()).thenReturn(newCa);
        listener.onRenewalCaChange();

        Assert.assertEquals(SslCertificateClientInstallationTracker.Status.UNKNOWN, tracker.isFutureCaCertificateInstalled("device:000000000000", "NCSA Mosaic/1.0 (X11;SunOS 4.1.4 sun4m)"));
    }

    private EblockerCa createEblockerCaMock(BigInteger serialNumber) {
        X509Certificate certificate = Mockito.mock(X509Certificate.class);
        Mockito.when(certificate.getSerialNumber()).thenReturn(serialNumber);
        EblockerCa eblockerCa = Mockito.mock(EblockerCa.class);
        Mockito.when(eblockerCa.getCertificate()).thenReturn(certificate);
        return eblockerCa;
    }
}
