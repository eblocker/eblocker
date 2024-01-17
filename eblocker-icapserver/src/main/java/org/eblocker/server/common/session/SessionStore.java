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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.IpAddress;
import org.eblocker.server.common.exceptions.EblockerException;
import org.eblocker.server.common.network.NetworkInterfaceWrapper;
import org.eblocker.server.common.transaction.TransactionIdentifier;
import org.eblocker.server.http.service.DeviceService;
import org.eblocker.server.http.service.UserAgentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.Date;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

@Singleton
public class SessionStore {
    private static final Logger log = LoggerFactory.getLogger(SessionStore.class);
    private final Map<String, SessionImpl> store = new ConcurrentHashMap<>();
    private static final long MILLIS_TO_KEEP = 24 * 3600 * 1000L;
    private final NetworkInterfaceWrapper networkInterface;
    private final DeviceService deviceService;
    private final UserAgentService userAgentService;

    @Inject
    public SessionStore(
            NetworkInterfaceWrapper networkInterface,
            DeviceService deviceService,
            UserAgentService userAgentService
    ) {
        log.info("Creating a session store");
        this.networkInterface = networkInterface;
        this.deviceService = deviceService;
        this.userAgentService = userAgentService;
    }

    public Session getSession(TransactionIdentifier transactionId) {
        String userAgent = SessionIdUtil.normalizeUserAgent(transactionId.getUserAgent());
        IpAddress ip = SessionIdUtil.normalizeIp(transactionId.getOriginalClientIP(), networkInterface.getFirstIPv4Address());

        Device device = deviceService.getDeviceByIp(ip);
        if (device == null) {
            log.error("Could not find a device with IP {}. Cannot retrieve session", ip);
            throw new EblockerException("Can not retrieve a session for an unknown device (IP = " + ip + ")");
        }

        String sessionId = SessionIdUtil.getSessionId(device.getId(), userAgent, device.getOperatingUser());
        Session session = store.get(sessionId);
        if (session == null) {
            session = createSession(sessionId, userAgent, ip, device.getId(), device.getOperatingUser());
        }
        setLoggingContext(session);
        session.markUsed();
        return session;
    }

    /**
     * Return the session with sessionId. If there is none, return null.
     *
     * @param sessionId
     * @return
     */
    public Session findSession(String sessionId) {
        Session session = store.get(sessionId);
        if (session != null) {
            setLoggingContext(session);
            session.markUsed();
        }
        return session;
    }

    private Session createSession(String sessionId, String userAgent, IpAddress ip, String deviceId, Integer userId) {
        SessionImpl session;
        synchronized (store) {
            session = store.get(sessionId);
            if (session == null) {
                session = new SessionImpl(
                        sessionId,
                        userAgent,
                        ip,
                        deviceId,
                        userId,
                        userAgentService.getUserAgentInfo(userAgent)
                );
                store.put(sessionId, session);
            }
            session.markUsed();
            String cloakedUserAgent = userAgentService.getCloakedUserAgent(userId, deviceId);
            session.setOutgoingUserAgent(cloakedUserAgent);
        }
        return session;
    }

    protected void purgeSessions() {
        Date purge = new Date(new Date().getTime() - MILLIS_TO_KEEP);

        synchronized (store) {
            //List<String> purgedSessions = new LinkedList<String>();
            for (Entry<String, SessionImpl> entry : store.entrySet()) {
                if (entry.getValue().getLastUsed().before(purge)) {
                    String oldSessionKey = MDC.get("SESSION");
                    MDC.put("SESSION", (entry.getValue() == null ? "--------" : entry.getValue().getShortId()));
                    log.info("Purging session from memory");
                    //purgedSessions.add(entry.getKey());
                    store.remove(entry.getKey());
                    MDC.put("SESSION", oldSessionKey);
                }
            }
            //tell useragentspoofprocessor that sessions got purged
            //UserAgentSpoofProcessor.purgeSessions(purgedSessions)
        }

    }

    private void setLoggingContext(Session session) {
        // From now on, we know the corresponding page context
        MDC.put("SESSION", (session == null ? "--------" : session.getShortId()));
    }

}
