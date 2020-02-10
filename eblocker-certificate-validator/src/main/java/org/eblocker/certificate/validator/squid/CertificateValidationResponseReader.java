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
import java.io.InputStream;
import java.io.StringReader;
import java.util.Map;
import java.util.Scanner;

public class CertificateValidationResponseReader extends CertificateValidationMessageReader {
    private static final Logger LOG = LoggerFactory.getLogger(CertificateValidationRequestReader.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    public CertificateValidationResponseReader(boolean useConcurrency) {
        super(useConcurrency);
    }

    public CertificateValidationResponse read(BufferedReader reader)  {
        try {
        	CertificateValidationResponse response = (CertificateValidationResponse) parseMessage(reader);
        	return response;
        }catch(Exception e){
            STATUS_LOG.warn("Caught exception while reading response:", e);
        }
        return null;
    }

    @Override
    protected CertificateValidationMessage buildMessage(long ID, String messageType, Map<String, String> map) {
        boolean success = messageType.equalsIgnoreCase(CertificateValidationResponse.CERTIFICATE_VALIDATION_RESULT_OK);

        return new CertificateValidationResponse(
				ID,
                getErrorNames(map),
                getErrorCertIds(map),
                useConcurrency,
                success,
                getErrorReasons(map)
        );
    }

	public CertificateValidationResponse readSquidFormat(InputStream in) {
        Scanner scanner = new Scanner(in);
        scanner.useDelimiter("\\x01");
        String message = scanner.next();
        LOG.debug("message: {}", message);
        BufferedReader reader = new BufferedReader(new StringReader(message), 2048);
        return read(reader);
	}
}
