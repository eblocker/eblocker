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
package org.eblocker.server.http.controller.impl;

import com.google.inject.Inject;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageVisibility;
import org.eblocker.server.common.page.PageContextStore;
import org.eblocker.server.common.session.Session;
import org.eblocker.server.common.session.SessionStore;
import org.eblocker.server.http.controller.MessageCenterController;
import org.eblocker.server.http.server.SessionContextController;
import org.eblocker.server.http.service.MessageCenterService;
import org.restexpress.Request;
import org.restexpress.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class MessageCenterControllerImpl extends SessionContextController implements MessageCenterController {
    private static final Logger log = LoggerFactory.getLogger(MessageCenterControllerImpl.class);

    private final MessageCenterService messageCenterService;

    @Inject
    public MessageCenterControllerImpl(
            SessionStore sessionStore,
            PageContextStore pageContextStore,
            MessageCenterService messageCenterService
    ) {
        super(sessionStore, pageContextStore);
        this.messageCenterService = messageCenterService;
    }

    /**
     * Get all messages for one specific session
     */
    @Override
    public List<MessageCenterMessage> getMessages(Request request, Response response) {
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        String userAgent = session.getUserAgent();

        List<MessageCenterMessage> messages = messageCenterService.getMessagesForDevice(deviceId);
        messages = messageCenterService.checkAndReduceSSLExpirationMessage(messages, deviceId, userAgent);
        messages = messageCenterService.checkAndReduceSSLUntrustedMessage(messages, deviceId, userAgent);

        return messages;
    }

    /**
     * Get the number of messages for one specific session.
     * <p>
     * This is called by the JavaScript code injected into the parent page,
     * therefore we must allow access for all domains.
     */
    @Override
    public Integer getNumberOfMessages(Request request, Response response) {
        int n = getMessages(request, response).size();
        response.addHeader("Cache-Control", "private, no-cache, no-store");
        response.addHeader("Access-Control-Allow-Origin", "*");
        return n;
    }

    @Override
    public void hideMessage(Request request, Response response) {
        MessageVisibility messageVisibility = request.getBodyAs(MessageVisibility.class);
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        log.debug("Message ({}) hide button was clicked by IP: {}", messageVisibility.getMessageId(), session.getIp());
        messageCenterService.hideMessage(messageVisibility.getMessageId(), deviceId);
    }

    @Override
    public void executeMessageAction(Request request, Response response) {
        MessageVisibility messageVisibility = request.getBodyAs(MessageVisibility.class);
        Session session = getSession(request);
        String deviceId = session.getDeviceId();
        log.debug("Message ({}) action button was clicked by IP: {}", messageVisibility.getMessageId(), session.getIp());
        messageCenterService.executeMessageAction(messageVisibility.getMessageId(), deviceId);
    }

    /**
     * Apply the checkbox result and tell the message, that it got an update of the doNotShowAgain flag,
     * plus persist it in Redis
     */
    @Override
    public void setDoNotShowAgain(Request request, Response response) {
        MessageVisibility messageVisibility = request.getBodyAs(MessageVisibility.class);
        log.info("Received request: messageId: {}, doNotShowStatus: {}", messageVisibility.getMessageId(), messageVisibility.isDoNotShowAgain());
        messageCenterService.setDoNotShowAgain(messageVisibility.getMessageId(), messageVisibility.isDoNotShowAgain());
    }

}
