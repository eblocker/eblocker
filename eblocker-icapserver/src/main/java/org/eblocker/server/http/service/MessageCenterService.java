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
package org.eblocker.server.http.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.registration.ProductFeature;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageProvider;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import org.eblocker.server.common.data.messagecenter.provider.AppModuleRemovalMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.CertificateExpirationMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.CertificateUntrustedMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.DailyNewsMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.EventMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.FilterListsOutdatedMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.LicenseExpirationMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.LocalDnsIsNotGatewayMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.MessageProviderMessageId;
import org.eblocker.server.common.data.messagecenter.provider.ReleaseNotesMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.RouterCompatibilityMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.SslSupportMessageProvider;
import org.eblocker.server.common.data.messagecenter.provider.UnreliableDnsServerMessageProvider;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.ssl.SslCertificateClientInstallationTracker;
import org.eblocker.server.common.ssl.SslService;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.restexpress.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * MessageCenterService
 *
 * <ul>
 *     <li>Asynchronously add/update/delete messages by executing set of MessageFactories.</li>
 *     <li>Provide cache of current messages.</li>
 *     <li>Remember, which device saw a certain message already.</li>
 *     <li>Persist, which device saw a certain message already.</li>
 *     <li>Remember, if a certain message has been flagged as "never show again".</li>
 * </ul>
 */
@Singleton
@SubSystemService(value = SubSystem.BACKGROUND_TASKS, initPriority = -1)
public class MessageCenterService {

    private static final Logger LOG = LoggerFactory.getLogger(MessageCenterService.class);

    private final List<MessageProvider> messageProviders;

    private final Map<Integer, MessageContainer> cache = new ConcurrentHashMap<>();

    private final DataSource dataSource;

    @Inject
    private SslCertificateClientInstallationTracker tracker;

    @Inject
    private SslService sslService;

    private DeviceService deviceService;

    private ProductInfoService productInfoService;

    private UserService userService;

    private ParentalControlService parentalControlService;

    @Inject
    public MessageCenterService(
            DataSource dataSource,
            EventMessageProvider eventMessageProvider,
            SslSupportMessageProvider sslSupportMessageProvider,
            LicenseExpirationMessageProvider licenseExpirationMessageProvider,
            ReleaseNotesMessageProvider releaseNotesMessageProvider,
            RouterCompatibilityMessageProvider routerCompatibilityMessageProvider,
            DailyNewsMessageProvider dailyNewsMessageProvider,
            CertificateExpirationMessageProvider certificateExpirationMessageProvider,
            CertificateUntrustedMessageProvider certificateUntrustedMessageProvider,
            LocalDnsIsNotGatewayMessageProvider localDnsIsNotGatewayMessageProvider,
            UnreliableDnsServerMessageProvider unreliableDnsServerMessageProvider,
            AppModuleRemovalMessageProvider appModuleRemovalMessageProvider,
            FilterListsOutdatedMessageProvider filterListsOutdatedMessageProvider,
            ProductInfoService productInfoService,
            DeviceService deviceService,
            UserService userService,
            ParentalControlService parentalControlService) {
        this(dataSource,
                Arrays.asList(
                        eventMessageProvider,
                        sslSupportMessageProvider,
                        licenseExpirationMessageProvider,
                        releaseNotesMessageProvider,
                        routerCompatibilityMessageProvider,
                        dailyNewsMessageProvider,
                        certificateExpirationMessageProvider,
                        certificateUntrustedMessageProvider,
                        localDnsIsNotGatewayMessageProvider,
                        unreliableDnsServerMessageProvider,
                        appModuleRemovalMessageProvider,
                        filterListsOutdatedMessageProvider
                ),
                productInfoService,
                deviceService,
                userService,
                parentalControlService
        );
    }

    public MessageCenterService(DataSource dataSource, List<MessageProvider> messageProviders,
                                ProductInfoService productInfoService, DeviceService deviceService, UserService userService,
                                ParentalControlService parentalControlService) {
        this.dataSource = dataSource;
        this.messageProviders = messageProviders;
        this.productInfoService = productInfoService;
        this.deviceService = deviceService;
        this.userService = userService;
        this.parentalControlService = parentalControlService;
    }

    @SubSystemInit
    public void init() {
        loadMessages();
        updateMessages();
    }

    public List<MessageCenterMessage> getMessagesForDevice(String deviceId) {
        Device dev = dataSource.getDevice(deviceId);
        List<MessageCenterMessage> messages = new ArrayList<>();
        // If the device is used by a PCed user, do not return any messages at all
        if (productInfoService.hasFeature(ProductFeature.FAM)) {
            int opUserId = deviceService.getDeviceById(deviceId).getOperatingUser();
            UserModule opUser = userService.getUserById(opUserId);
            if (opUser != null) {
                UserProfileModule opUserProfile = parentalControlService.getProfile(opUser.getAssociatedProfileId());
                if (opUserProfile != null && (opUserProfile.isControlmodeMaxUsage() || opUserProfile.isControlmodeTime()
                        || opUserProfile.isControlmodeUrls())) {
                    return Collections.emptyList();
                }
            }
        }
        // User is not restricted, return actual messages
        for (MessageContainer messageContainer : cache.values()) {
            MessageCenterMessage message = messageContainer.getMessage();
            MessageSeverity severity = message.getMessageSeverity();

            // Show message if visible and if not Info when info messages are disabled and if not alert when alert messages are disabled
            if (messageContainer.getVisibility().isForDevice(deviceId) &&
                    !(severity != null && severity.equals(MessageSeverity.INFO)  && dev != null && !dev.isMessageShowInfo()) &&
                    !(severity != null && severity.equals(MessageSeverity.ALERT) && dev != null && !dev.isMessageShowAlert())
            ) {
                messages.add(message);
            }
        }
        return messages;
    }

    public void hideMessage(int messageId, String deviceId) {
        MessageContainer messageContainer = cache.get(messageId);
        if (messageContainer == null) {
            throw new NotFoundException("Cannot find message with ID " + messageId);
        }
        messageContainer.getVisibility().hideForDevice(deviceId);
        save(messageContainer);
    }

    public void executeMessageAction(int messageId, String deviceId) {
        MessageContainer messageContainer = cache.get(messageId);
        if (messageContainer == null) {
            throw new NotFoundException("Cannot find message with ID " + messageId);
        }
        MessageProvider provider = getProvider(messageContainer);
        if (provider == null) {
            throw new NotFoundException("Cannot find provider for message with ID " + messageId);
        }
        if (provider.executeAction(messageId)) {
            messageContainer.getVisibility().hideForDevice(deviceId);
            save(messageContainer);
        }
    }

    public void setDoNotShowAgain(int messageId, boolean doNotShowAgain) {
        MessageContainer messageContainer = cache.get(messageId);
        if (messageContainer == null) {
            throw new NotFoundException("Cannot find message with ID " + messageId);
        }
        messageContainer.getVisibility().setDoNotShowAgain(doNotShowAgain);
        save(messageContainer);
    }

    /**
     * Load message containers from DB and refresh cache
     */
    private void loadMessages() {
        List<MessageContainer> messageContainers = dataSource.getAll(MessageContainer.class);
        synchronized (cache) {
            cache.clear();
            messageContainers.forEach(messageContainer -> cache.put(messageContainer.getMessage().getId(), messageContainer));
        }
    }

    /**
     * Find the responsible provider for the current message
     */
    private MessageProvider getProvider(MessageContainer messageContainer) {
        for (MessageProvider provider : messageProviders) {
            if (provider.isResponsibleFor(messageContainer)) {
                return provider;
            }
        }
        return null;
    }

    /**
     * Call message providers and ask them to update the messages.
     * They might add, modify or remove messages.
     * Update database, incl. removal of obsolete messages.
     */
    public void updateMessages() {
        synchronized (cache) {
            for (MessageProvider provider : messageProviders) {
                provider.update(cache);
            }
        }
        List<MessageContainer> previous = dataSource.getAll(MessageContainer.class);
        for (MessageContainer messageContainer : cache.values()) {
            save(messageContainer);
        }
        previous.removeAll(cache.values());
        for (MessageContainer messageContainer : previous) {
            dataSource.delete(MessageContainer.class, messageContainer.getMessage().getId());
        }
        LOG.info("Currently there are {} messages", cache.size());
    }

    private void save(MessageContainer messageContainer) {
        dataSource.save(messageContainer, messageContainer.getMessage().getId());
    }

    private int getMessageIndex(List<MessageCenterMessage> list, int messageId) {
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId() == messageId) {
                return i;
            }
        }
        return -1;
    }

    public List<MessageCenterMessage> checkAndReduceSSLExpirationMessage(List<MessageCenterMessage> messages, String deviceId, String userAgent) {
        SslCertificateClientInstallationTracker.Status isRenewalInstalled = tracker.isFutureCaCertificateInstalled(deviceId, userAgent);
        Device device = deviceService.getDeviceById(deviceId);
        List<MessageCenterMessage> messagesReduced = new ArrayList<>(messages);

        if (device == null ||
                !device.isSslEnabled() ||
                !sslService.isSslEnabled() ||
                !sslService.isRenewalCaAvailable() ||
                isRenewalInstalled == null ||
                isRenewalInstalled.equals(SslCertificateClientInstallationTracker.Status.INSTALLED) ||
                isRenewalInstalled.equals(SslCertificateClientInstallationTracker.Status.UNKNOWN)) {
            // Do not show SSL expiration message for this device / user agent
            // Default is to show the message.
            int index = getMessageIndex(messagesReduced, MessageProviderMessageId.MESSAGE_CERTIFICATE_EXPIRATION_WARNING.getId());
            if (index > -1) {
                messagesReduced.remove(index);
            } else {
                LOG.info("Should have removed the SSL certificate expiration warning, but could not find the message.");
            }
        }
        return messagesReduced;
    }

    public List<MessageCenterMessage> checkAndReduceSSLUntrustedMessage(List<MessageCenterMessage> messages, String deviceId, String userAgent) {
        SslCertificateClientInstallationTracker.Status isCaInstalled = tracker.isCaCertificateInstalled(deviceId, userAgent);
        Device device = deviceService.getDeviceById(deviceId);
        List<MessageCenterMessage> messagesReduced = new ArrayList<>(messages);

        if (device == null ||
                !device.isSslEnabled() ||
                !sslService.isSslEnabled() ||
                !sslService.isCaAvailable() ||
                isCaInstalled == null ||
                isCaInstalled.equals(SslCertificateClientInstallationTracker.Status.INSTALLED) ||
                isCaInstalled.equals(SslCertificateClientInstallationTracker.Status.UNKNOWN)) {
            // Do not show SSL untrusted message for this device / user agent
            // Default is to show the message.
            int index = getMessageIndex(messagesReduced, MessageProviderMessageId.MESSAGE_CERTIFICATE_UNTRUSTED_WARNING.getId());

            if (index > -1) {
                messagesReduced.remove(index);
            } else {
                LOG.info("Should have removed the SSL certificate untrusted warning, but could not find the message.");
            }
        }
        return messagesReduced;
    }
}
