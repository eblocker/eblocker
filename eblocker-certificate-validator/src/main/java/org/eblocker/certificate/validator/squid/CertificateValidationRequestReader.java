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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.util.Map;

/**
 * Use this by adding the following lines into the squid config:
 * sslcrtvalidator_program "/opt/eblocker-network/certificate-validator/bin/squid-certificate-validator.sh"
 * sslcrtvalidator_children 1 startup=1 idle=1 concurrency=0
 * <p>
 * <p>
 * The protocol SEEMS to be specified here: http://wiki.squid-cache.org/Features/SslServerCertValidator
 * As it turns out, the last delimiter byte 0x01 is not being sent by our squid version (3.5.12) even though they explicitly explain it here:
 * "...line refers to a logical input. body may contain \n characters so each line in this format is delimited by a 0x01 byte instead of the standard \n byte...."
 * In addition to this strange non documented behaviour, the information about the host IP address is already placed in the first line before any \n or whatsoever 'host=...'
 * <p>
 * Note: When we configure squid to not use concurrency with our ssl certificate validator (e.g. "sslcrtvalidator_children 1 startup=1 idle=1 concurrency=0") it will not send AND expect any references
 * to IDs (i.e. no request ID and therefore no response ID, because the program is only working on one request at a time)
 * <p>
 * <p>
 * FIXME : report those problems with e.g. the missing 0x01 byte to the Squid community?
 */
public class CertificateValidationRequestReader extends CertificateValidationMessageReader {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateValidationRequestReader.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    public CertificateValidationRequestReader(boolean useConcurrency) {
        super(useConcurrency);
    }

    /**
     * @param in
     * @return null if end of inputstream is reached
     */
    public CertificateValidationRequest read(BufferedReader reader) {
        try {
            CertificateValidationRequest request = (CertificateValidationRequest) parseMessage(reader);
            return request;
        } catch (Exception e) {
            STATUS_LOG.warn("Caught exception while reading request", e);
        }

        return null;
    }

    @Override
    protected CertificateValidationMessage buildMessage(long ID, String messageType, Map<String, String> map) {
        LOG.debug("Host: {} cipher: {} proto_version: {} error names : {} error_cert_: {}",
                map.get("host"),
                map.get("cipher"),
                map.get("proto_version"),
                getErrorNames(map).length,
                getErrorCertIds(map).length
        );

        return new CertificateValidationRequest(
                ID,
                map.get("proto_version"),
                map.get("cipher"),
                map.get("host"),
                getCertificates(map),
                getErrorNames(map),
                getErrorCertIds(map),
                useConcurrency
        );
    }
}
