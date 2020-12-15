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
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.http.service.DeviceService;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class SslSupportMessageProvider extends AbstractMessageProvider {

    private static final String MESSAGE_SSL_SUPPORT_INSTALL_TITLE = "MESSAGE_SSL_SUPPORT_INSTALL_TITLE";
    private static final String MESSAGE_SSL_SUPPORT_INSTALL_CONTENT = "MESSAGE_SSL_SUPPORT_INSTALL_CONTENT";
    private static final String MESSAGE_SSL_SUPPORT_INSTALL_LABEL = "MESSAGE_SSL_SUPPORT_INSTALL_LABEL";
    private static final String MESSAGE_SSL_SUPPORT_INSTALL_URL = "MESSAGE_SSL_SUPPORT_INSTALL_URL";

    private final DeviceService deviceService;

    @Inject
    public SslSupportMessageProvider(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        MessageContainer messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId());
        if (messageContainer == null) {
            messageContainer = createMessage(
                    MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId(),
                    MESSAGE_SSL_SUPPORT_INSTALL_TITLE,
                    MESSAGE_SSL_SUPPORT_INSTALL_CONTENT,
                    MESSAGE_SSL_SUPPORT_INSTALL_LABEL,
                    MESSAGE_SSL_SUPPORT_INSTALL_URL,
                    Collections.emptyMap(),
                    false,
                    MessageSeverity.INFO
            );
            messageContainers.put(MessageProviderMessageId.MESSAGE_SSL_SUPPORT_INSTALL_ID.getId(), messageContainer);
        }
        Collection<Device> devices = deviceService.getDevices(false);
        for (Device device : devices) {
            if (device.isSslEnabled()) {
                messageContainer.getVisibility().hideForDevice(device.getId());
            }
        }
    }

}
