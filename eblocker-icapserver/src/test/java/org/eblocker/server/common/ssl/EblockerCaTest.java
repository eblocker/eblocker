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

import org.eblocker.crypto.pki.CertificateAndKey;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Date;

public class EblockerCaTest {

    private CertificateAndKey certificateAndKey;
    private EblockerCa eblockerCa;

    @Before
    public void setup() throws Exception {
        certificateAndKey = SslTestUtils.loadCertificateAndKey(SslTestUtils.CA_RESOURCE, SslTestUtils.UNIT_TEST_CA_PASSWORD);
        eblockerCa = new EblockerCa(certificateAndKey);
    }

    @Test
    public void getCertificate() throws Exception {
        Assert.assertEquals(certificateAndKey.getCertificate(), eblockerCa.getCertificate());
    }

    @Test
    public void getKey() throws Exception {
        Assert.assertEquals(certificateAndKey.getKey(), eblockerCa.getKey());
    }

    @Test
    public void generateServerCertificate() throws Exception {
        Date validUntil = Date.from(ZonedDateTime.of(2030, 12, 12, 12, 12, 12, 12, ZoneId.systemDefault()).toInstant());
        CertificateAndKey serverCertificateAndKey = eblockerCa.generateServerCertificate("common name", validUntil, Arrays.asList("10.10.10.10", "alt name"));

        serverCertificateAndKey.getCertificate().verify(eblockerCa.getCertificate().getPublicKey());
        Assert.assertEquals("CN=common name", serverCertificateAndKey.getCertificate().getSubjectDN().toString());
        Assert.assertEquals(3, serverCertificateAndKey.getCertificate().getSubjectAlternativeNames().size());
        Assert.assertTrue(serverCertificateAndKey.getCertificate().getSubjectAlternativeNames().contains(Arrays.asList(2, "alt name")));
        Assert.assertTrue(serverCertificateAndKey.getCertificate().getSubjectAlternativeNames().contains(Arrays.asList(2, "10.10.10.10")));
        Assert.assertTrue(serverCertificateAndKey.getCertificate().getSubjectAlternativeNames().contains(Arrays.asList(7, "10.10.10.10")));
    }

}
