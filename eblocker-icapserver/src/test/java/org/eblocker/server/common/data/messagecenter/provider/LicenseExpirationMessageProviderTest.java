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

import org.eblocker.server.common.data.events.Event;
import org.eblocker.server.common.data.events.EventLogger;
import org.eblocker.server.common.data.events.Events;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.registration.DeviceRegistrationProperties;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.when;

public class LicenseExpirationMessageProviderTest {

    @Mock
    private DeviceRegistrationProperties deviceRegistrationProperties;
    @Mock
    private EventLogger eventLogger;

    private static final int EXPIRATION_WARNING_THRESHOLD_DAY = 1;
    private static final int EXPIRATION_WARNING_THRESHOLD_WEEK = 7;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void test_messageAvailable() {
        when(deviceRegistrationProperties.isLicenseAboutToExpire()).thenReturn(true);
        when(deviceRegistrationProperties.getLicenseNotValidAfter()).thenReturn(new Date(new Date().getTime() + 1000L * 3600 * 24 * 7)); // Expires in one week

        LicenseExpirationMessageProvider licenseExpirationMessageProvider = new LicenseExpirationMessageProvider(
                deviceRegistrationProperties, eventLogger, EXPIRATION_WARNING_THRESHOLD_DAY,
                EXPIRATION_WARNING_THRESHOLD_WEEK);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // First call creates message
        //
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        MessageContainer messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());

        assertNotNull(messageContainer);
        assertEquals(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(), messageContainer.getMessage().getId());

        //
        // Second call leaves message as is
        //
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());

        assertNotNull(messageContainer);
        assertEquals(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(), messageContainer.getMessage().getId());
    }

    @Test
    public void test_messageNotAvailable() {
        when(deviceRegistrationProperties.isLicenseAboutToExpire()).thenReturn(false);

        LicenseExpirationMessageProvider licenseExpirationMessageProvider = new LicenseExpirationMessageProvider(
                deviceRegistrationProperties, eventLogger, EXPIRATION_WARNING_THRESHOLD_DAY,
                EXPIRATION_WARNING_THRESHOLD_WEEK);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // First call: no message
        //
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(0, messageContainers.size());
    }

    @Test
    public void test_messageAppears() {
        when(deviceRegistrationProperties.isLicenseAboutToExpire()).thenReturn(false).thenReturn(true);
        when(deviceRegistrationProperties.getLicenseNotValidAfter()).thenReturn(new Date(new Date().getTime() - 1000L * 3600 * 24 * 7)); // Expired one week ago

        LicenseExpirationMessageProvider licenseExpirationMessageProvider = new LicenseExpirationMessageProvider(
                deviceRegistrationProperties, eventLogger, EXPIRATION_WARNING_THRESHOLD_DAY,
                EXPIRATION_WARNING_THRESHOLD_WEEK);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // First call: no message
        //
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(0, messageContainers.size());

        //
        // Second call leaves message as is
        //
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        MessageContainer messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId());

        assertNotNull(messageContainer);
        assertEquals(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRED_ID.getId(), messageContainer.getMessage().getId());

        // Check system event logged
        ArgumentCaptor<Event> captor = ArgumentCaptor.forClass(Event.class);
        Mockito.verify(eventLogger).log(captor.capture());
        Event capturedEvent = captor.getValue();
        assertEquals(Events.licenseExpired().getType(), capturedEvent.getType());
    }

    @Test
    public void test_warningAppearsPrior() {
        when(deviceRegistrationProperties.isLicenseAboutToExpire()).thenReturn(false).thenReturn(true);

        LicenseExpirationMessageProvider licenseExpirationMessageProvider = new LicenseExpirationMessageProvider(
                deviceRegistrationProperties, eventLogger, EXPIRATION_WARNING_THRESHOLD_DAY,
                EXPIRATION_WARNING_THRESHOLD_WEEK);
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // First call: Not expiring soon
        //
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(0, messageContainers.size());

        //
        // Second call: Expiring within next 30 days
        //
        when(deviceRegistrationProperties.getLicenseNotValidAfter()).thenReturn(new Date(new Date().getTime() + 1000L * 3600 * 24 * 30)); // Expires in 30 days
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        MessageContainer messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());

        assertNotNull(messageContainer);
        assertEquals(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(), messageContainer.getMessage().getId());
        assertEquals(LicenseExpirationMessageProvider.MESSAGE_LICENSE_EXPIRING_MONTH_CONTENT, messageContainer.getMessage().getContentKey());

        //
        // Third call: Expiring within next week
        //
        when(deviceRegistrationProperties.getLicenseNotValidAfter()).thenReturn(new Date(new Date().getTime() + 1000L * 3600 * 24 * 7)); // Expires in 7 days
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());

        assertNotNull(messageContainer);
        assertEquals(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(), messageContainer.getMessage().getId());
        assertEquals(LicenseExpirationMessageProvider.MESSAGE_LICENSE_EXPIRING_WEEK_CONTENT, messageContainer.getMessage().getContentKey());

        //
        // Fourth call: Expiring within next day
        //
        when(deviceRegistrationProperties.getLicenseNotValidAfter()).thenReturn(new Date(new Date().getTime() + 1000L * 3600 * 24)); // Expires in 1 day
        licenseExpirationMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId());

        assertNotNull(messageContainer);
        assertEquals(MessageProviderMessageId.MESSAGE_LICENSE_EXPIRING_ID.getId(), messageContainer.getMessage().getId());
        assertEquals(LicenseExpirationMessageProvider.MESSAGE_LICENSE_EXPIRING_DAY_CONTENT, messageContainer.getMessage().getContentKey());

    }

}
