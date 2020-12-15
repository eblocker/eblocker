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
package org.eblocker.server.common.data.messagecenter.provider;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class CertificateUntrustedMessageProvider extends AbstractMessageProvider {

    private MessageContainer messageContainer;

    @Inject
    public CertificateUntrustedMessageProvider() {
        String MESSAGE_CERTIFICATE_UNTRUSTED_TITLE = "MESSAGE_CERTIFICATE_UNTRUSTED_TITLE";
        String MESSAGE_CERTIFICATE_UNTRUSTED_CONTENT = "MESSAGE_CERTIFICATE_UNTRUSTED_CONTENT";
        String MESSAGE_CERTIFICATE_UNTRUSTED_LABEL = "MESSAGE_CERTIFICATE_UNTRUSTED_LABEL";
        String MESSAGE_CERTIFICATE_UNTRUSTED_URL = "MESSAGE_CERTIFICATE_UNTRUSTED_URL";

        messageContainer = createMessage(
                MessageProviderMessageId.MESSAGE_CERTIFICATE_UNTRUSTED_WARNING.getId(),
                MESSAGE_CERTIFICATE_UNTRUSTED_TITLE,
                MESSAGE_CERTIFICATE_UNTRUSTED_CONTENT,
                MESSAGE_CERTIFICATE_UNTRUSTED_LABEL,
                MESSAGE_CERTIFICATE_UNTRUSTED_URL,
                Collections.emptyMap(),
                false,
                MessageSeverity.ALERT
        );
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_CERTIFICATE_UNTRUSTED_WARNING.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {

        // only add if not present already
        if (!messageContainers.containsKey(MessageProviderMessageId.MESSAGE_CERTIFICATE_UNTRUSTED_WARNING.getId())) {
            // For now add the message for all devices. We need the userAgent to determine whether to show this message or not.
            // This is done in getMessages in MessageCenterControllerImpl
            messageContainers.put(MessageProviderMessageId.MESSAGE_CERTIFICATE_UNTRUSTED_WARNING.getId(), messageContainer);
        }
    }

    @Override
    public boolean executeAction(int messageId) {
        return false;
    }

}
