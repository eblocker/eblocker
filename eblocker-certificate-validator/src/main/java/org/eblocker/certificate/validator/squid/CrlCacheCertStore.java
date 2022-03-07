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
package org.eblocker.certificate.validator.squid;

import org.bouncycastle.asn1.ASN1OctetString;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DERIA5String;
import org.bouncycastle.asn1.x509.CRLDistPoint;
import org.bouncycastle.asn1.x509.DistributionPoint;
import org.bouncycastle.asn1.x509.DistributionPointName;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.cert.CRL;
import java.security.cert.CRLSelector;
import java.security.cert.CertSelector;
import java.security.cert.CertStoreException;
import java.security.cert.CertStoreParameters;
import java.security.cert.CertStoreSpi;
import java.security.cert.Certificate;
import java.security.cert.X509CRLSelector;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class CrlCacheCertStore extends CertStoreSpi {
    private static Logger log = LoggerFactory.getLogger(CrlCacheCertStore.class);
    private static Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private final CrlCache crlCache;

    public CrlCacheCertStore(CertStoreParameters parameters) throws InvalidAlgorithmParameterException {
        super(null);
        if (parameters == null || !(parameters instanceof Parameters)) {
            throw new InvalidAlgorithmParameterException("missing or invalid parameters");
        }
        crlCache = ((Parameters) parameters).getCrlCache();
    }

    @Override
    public Collection<? extends Certificate> engineGetCertificates(CertSelector selector) {
        return Collections.emptyList();
    }

    @Override
    public Collection<? extends CRL> engineGetCRLs(CRLSelector selector) throws CertStoreException {
        try {
            X509CRLSelector x509Selector = (X509CRLSelector) selector;
            List<String> crlUrls = extractCrls(new X509Certificate[]{ x509Selector.getCertificateChecking() });
            log.debug("found {} crl urls", crlUrls.size());
            return crlUrls.stream()
                    .filter(url -> url.startsWith("http"))
                    .map(crlCache::get)
                    .filter(Objects::nonNull) // TODO: this may be caused by a network error, how should this be handled?
                    .filter(x509Selector::match)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            STATUS_LOG.warn("failed to extract crls from certificate", e);
            throw new CertStoreException("failed to extract crls from certificate", e);
        }
    }

    private List<String> extractCrls(X509Certificate[] certificates) throws IOException {
        List<String> urls = new ArrayList<>();
        for (X509Certificate certificate : certificates) {
            urls.addAll(extractCrls(certificate));
        }
        return urls;
    }

    private List<String> extractCrls(X509Certificate certificate) throws IOException {
        CRLDistPoint crlDistPoint = getCrlExtensions(certificate);
        if (crlDistPoint == null) {
            return Collections.emptyList();
        }

        List<String> urls = new ArrayList<>();
        for (DistributionPoint point : crlDistPoint.getDistributionPoints()) {
            DistributionPointName pointName = point.getDistributionPoint();
            if (pointName.getType() == DistributionPointName.FULL_NAME) {
                GeneralName[] names = ((GeneralNames) pointName.getName()).getNames();
                for (GeneralName name : names) {
                    urls.add(DERIA5String.getInstance(name.getName()).getString());
                }
            }
        }
        return urls;
    }

    private CRLDistPoint getCrlExtensions(X509Certificate certificate) throws IOException {
        byte[] extension = certificate.getExtensionValue(Extension.cRLDistributionPoints.getId());
        if (extension == null) {
            return null;
        }

        try {
            ASN1OctetString octetString = ASN1OctetString.getInstance(extension);
            ASN1Primitive primitive = ASN1Primitive.fromByteArray(octetString.getOctets());
            return CRLDistPoint.getInstance(primitive);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new IOException("decoding failure", e);
        }
    }

    public static class Parameters implements CertStoreParameters {
        private final CrlCache crlCache;

        public Parameters(CrlCache crlCache) {
            this.crlCache = crlCache;
        }

        public CrlCache getCrlCache() {
            return crlCache;
        }

        @Override
        public Object clone() { //NOSONAR: is required by interface
            return new Parameters(crlCache);
        }
    }
}
