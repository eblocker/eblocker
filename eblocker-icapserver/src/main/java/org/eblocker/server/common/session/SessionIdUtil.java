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
package org.eblocker.server.common.session;

import org.apache.commons.codec.binary.Hex;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.exceptions.EblockerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class SessionIdUtil {
    private static final Logger log = LoggerFactory.getLogger(SessionIdUtil.class);

    private static final String HASH_ALGORITHM = "SHA-256";
    private static final String HASH_SALT = "1234567890";
    private static final String UNDEFINED_USER_AGENT = "<<undefined>>";

    private static final IpAddress LOCALHOST_IP6 = IpAddress.parse("::1");
    private static final IpAddress LOCALHOST_LINK_LOCAL_IP6 = IpAddress.parse("fe80::1");
    private static final IpAddress LOCALHOST_IP4 = IpAddress.parse("127.0.0.1");

    public static String getSessionId(String deviceId, String userAgent, Integer userId) {
        MessageDigest hash;
        try {
            hash = MessageDigest.getInstance(HASH_ALGORITHM);
        } catch (NoSuchAlgorithmException e) {
            String msg = "Cannot generate session ID: " + e.getMessage();
            log.error(msg);
            throw new EblockerException(msg, e);
        }
        hash.update(HASH_SALT.getBytes());
        hash.update("::".getBytes());
        hash.update(userAgent.getBytes());
        hash.update("::".getBytes());
        hash.update(deviceId.getBytes());
        hash.update("::".getBytes());
        hash.update((byte) (userId >> 24));
        hash.update((byte) (userId >> 16));
        hash.update((byte) (userId >> 8));
        hash.update(userId.byteValue());
        String sessionId = Hex.encodeHexString(hash.digest());
        return sessionId;
    }

    public static String normalizeUserAgent(String userAgent) {
        if (userAgent == null) {
            userAgent = UNDEFINED_USER_AGENT;
        }
        return userAgent;
    }

    /**
     * Normalizes local IP addresses (to simplify testing the Icapserver locally)
     * @param ip
     * @param eblockerIpAddress
     * @return
     */
    public static IpAddress normalizeIp(IpAddress ip, IpAddress eblockerIpAddress) {
        if (ip.equals(LOCALHOST_IP6) || ip.equals(LOCALHOST_LINK_LOCAL_IP6) || ip.equals(eblockerIpAddress)) {
            return LOCALHOST_IP4;
        }
        return ip;
    }
}
