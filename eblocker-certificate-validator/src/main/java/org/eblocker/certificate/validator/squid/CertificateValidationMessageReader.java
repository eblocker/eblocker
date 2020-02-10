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

import org.eblocker.crypto.CryptoException;
import org.eblocker.crypto.pki.PKI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CertificateValidationMessageReader {

    private static final Logger LOG = LoggerFactory.getLogger(CertificateValidationMessageReader.class);
    private static final Logger STATUS_LOG = LoggerFactory.getLogger("STATUS");

    private final String END_CERT = "-----END CERTIFICATE-----";
    private final String BEGIN_CERT ="-----BEGIN CERTIFICATE-----";

    protected static final String KEY_REGEX = "(host|proto_version|cipher|cert_\\d+|error_name_\\d+|error_cert_\\d+|error_reason_\\d+)=(.+)";
    protected static final Pattern KEY_PATTERN = Pattern.compile(KEY_REGEX);

    protected boolean useConcurrency = false; //multithreading or not?
    protected final Pattern firstLinePattern;

    public CertificateValidationMessageReader(boolean useConcurrency) {
    	this.useConcurrency = useConcurrency;
    	
    	firstLinePattern = Pattern.compile((useConcurrency ? "(\\d+) " : "") + "(cert_validate|OK|ERR|BH) (\\d+) (.*)");
    }

    protected class Header {
    	long ID = 0;
    	String messageType;
    	int bodySize;
    	String bodyStart;
    }
    
    protected Header parseHeader(String line) {
        Matcher matcher = firstLinePattern.matcher(line);
        Header result = new Header();
        if (matcher.matches()) {
        	if (useConcurrency) {
        		result.ID = Long.parseLong(matcher.group(1));
        		result.messageType = matcher.group(2);
        		result.bodySize = Integer.parseInt(matcher.group(3));
        		result.bodyStart = matcher.group(4);
        	} else {
        		result.messageType = matcher.group(1);
        		result.bodySize = Integer.parseInt(matcher.group(2));
        		result.bodyStart = matcher.group(3);        		
        	}
        	return result;
        } else {
        	return null;
        }
    }
    
    protected CertificateValidationMessage parseMessage(BufferedReader reader) throws IOException {
        List<String> bodyLines = new ArrayList<>();

        String headerLine = reader.readLine();
        LOG.debug("Read header line: {}", headerLine);

        if(headerLine == null) {
            return null; //EOF
        }

        Header header = parseHeader(headerLine);
        if (header == null) {
        	LOG.error("Could not parse message header from line: '{}'", headerLine);
        	return null;
        }

        LOG.debug("{}", header.bodyStart.length());

        if (header.bodySize == 0) {
            LOG.debug("no content, body read complete.");
            return buildMessage(header.ID, header.messageType, Collections.emptyMap());
        }

        //add first part with host=... from first line to body (where it belongs) and remember that we already read those bytes as well
        bodyLines.add(header.bodyStart);

        int alreadyRead = header.bodyStart.length() + 1; // one LF
        int left = header.bodySize - alreadyRead;

        LOG.debug("body size: {}, but already read {} bytes -> reading {} bytes", header.bodySize, alreadyRead, left);
        char[] body = new char[left];
        int read = 0;
        while (read < left) {
            read += reader.read(body, read, left - read);
        }
        reader.read(); //NOSONAR final LF

        LOG.debug("read {} of body (expected: {})", read, header.bodySize - alreadyRead);

        String content = new String(body);
        for (String line : content.split("\\n")) {
            bodyLines.add(line);
        }

        for (String line : bodyLines) {
            LOG.debug("read: {}", line);
        }

        //parse body content
        Map<String, String> map = readKeyValues(bodyLines);
        return buildMessage(header.ID, header.messageType, map);
    }
    
    protected CertificateValidationMessage buildMessage(long ID, String messageType, Map<String, String> map) {
		// TODO Auto-generated method stub
		return null;
	}

    /**
     * Apply the Key_Regex to the body content to map the information needed
     * @param contentLines
     * @return
     */
    public Map<String, String> readKeyValues(Iterable<String> contentLines) {
        Map<String, String> map = new HashMap<>();
        String key = null;
        StringBuilder multiLineValue = null;

        for (String line: contentLines) {
            if (multiLineValue != null) { //multiline value
                multiLineValue.append("\n").append(line);
                if (line.equals(END_CERT)) {
                    map.put(key, multiLineValue.toString());
                    multiLineValue = null;
                }
            } else {
                Matcher keyMatcher = KEY_PATTERN.matcher(line);
                if (keyMatcher.matches()) {
                    key = keyMatcher.group(1);
                    String val = keyMatcher.group(2);
                    if (val.equals(BEGIN_CERT)) {
                        multiLineValue = new StringBuilder();
                        multiLineValue.append(BEGIN_CERT);

                    } else {
                        LOG.debug("put: {} -> {}",key,val);
                        map.put(key, val);//single line value
                    }

                }
            }
        }
        return map;
    }

    protected X509Certificate[] getCertificates(Map<String, String> map) {
        List<X509Certificate> certificates = new ArrayList<>();
        int i = 0;
        while(map.containsKey("cert_"+i)) {
            try {
                X509Certificate certificate = PKI.loadCertificate(new ByteArrayInputStream(map.get("cert_" + i).getBytes()));
                certificates.add(certificate);
            } catch (CryptoException | IOException e) {
                STATUS_LOG.warn("Cannot read certificate, ignoring request parameter", e);
                LOG.warn("Cannot read certificate #{}, ignoring request parameter: {}", i, e);
            }
            i++;
        }
        return certificates.toArray(new X509Certificate[certificates.size()]);
    }

    protected String[] getErrorNames(Map<String, String> map) {
        LOG.debug("Getting error names...");
        return getStringValues(map, "error_name_");
    }

    protected String[] getErrorReasons(Map<String, String> map) {
        LOG.debug("Getting error reasons...");
        return getStringValues(map, "error_reason_");
    }

    protected String[] getStringValues(Map<String, String> map, String prefix) {
        List<String> values = new ArrayList<>();
        int i = 0;
        while (map.containsKey(prefix+i)) {
            values.add(map.get(prefix+i));
            i++;
        }
        return values.toArray(new String[values.size()]);
    }

    protected String[] getErrorCertIds(Map<String, String> map) {
        LOG.debug("Getting error certificate IDs...");
        return getStringValues(map, "error_cert_");
    }

    protected Integer[] getIntegerValues(Map<String, String> map, String prefix) {
        List<Integer> values = new ArrayList<>();
        int i = 0;
        while (map.containsKey(prefix+i)) {
            try {
                values.add(Integer.valueOf(map.get(prefix + i)));
            } catch (NumberFormatException e) {
                LOG.warn("Cannot parse {}id '{}', ignoring request parameter: {}", prefix, map.get(prefix + i), e.getMessage());
            }
            i++;
        }
        return values.toArray(new Integer[values.size()]);
    }

}
