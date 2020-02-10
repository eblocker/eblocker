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

import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.messagecenter.MessageCenterMessage;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.provider.AbstractMessageProvider;
import org.eblocker.registration.ProductFeature;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class MessageCenterServiceTest {

    private static final int MSG_1 = 1;
    private static final int MSG_2 = 2;
    private static final int MSG_3 = 3;

    private final TestMessageProvider prov1 = new TestMessageProvider(MSG_1);
    private final TestMessageProvider prov2 = new TestMessageProvider(MSG_2);
    private final TestMessageProvider prov3 = new TestMessageProvider(MSG_3);

    private static final String DEVICE_ID_1 = "device-1";
    private static final String DEVICE_ID_2 = "device-2";
    private static final String DEVICE_ID_3 = "device-3";

    @Mock
    private DataSource dataSource;

    private MessageCenterService messageCenterService;

    @Mock
    private ProductInfoService productInfoService;

    @Mock
    private DeviceService deviceService;

    @Mock
    private UserService userService;

    @Mock
    private ParentalControlService parentalControlService;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);

        // Create message center service with three providers
        messageCenterService = new MessageCenterService(dataSource, Arrays.asList(prov1, prov2, prov3),
                productInfoService, deviceService, userService, parentalControlService);
    }

    @Test
    public void test_update() {
        // No messages stored in DB
        when(dataSource.getAll(MessageContainer.class)).thenReturn(Collections.emptyList());
        // Device has no FAM-features
        when(productInfoService.hasFeature(any())).thenReturn(false);

        // No message to create or remove
        prov1.setNextAction(MessageAction.NOOP);
        prov1.setNextAction(MessageAction.NOOP);
        prov1.setNextAction(MessageAction.NOOP);
        messageCenterService.updateMessages();

        // Check for available messages
        assertEquals(0, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(0, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(0, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());


        // Create one message and update
        prov1.setNextAction(MessageAction.CREATE);
        messageCenterService.updateMessages();

        // Check for available messages
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());


        // Create another message and update
        prov2.setNextAction(MessageAction.CREATE);
        messageCenterService.updateMessages();

        // Check for available messages
        assertEquals(2, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(2, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(2, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());


        // Remove one message and update
        prov1.setNextAction(MessageAction.DELETE);
        messageCenterService.updateMessages();

        // Check for available messages
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());
    }

    @Test
    public void test_visibility() {
        // No messages stored in DB
        when(dataSource.getAll(MessageContainer.class)).thenReturn(Collections.emptyList());
        // Device has no FAM-features
        when(productInfoService.hasFeature(any())).thenReturn(false);

        // Create some messags
        prov1.setNextAction(MessageAction.CREATE);
        prov2.setNextAction(MessageAction.CREATE);
        prov3.setNextAction(MessageAction.CREATE);
        messageCenterService.updateMessages();

        // Check for available messages
        assertEquals(3, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(3, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(3, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());


        // Some messages seen by some devices
        messageCenterService.executeMessageAction(MSG_1, DEVICE_ID_1);
        messageCenterService.executeMessageAction(MSG_1, DEVICE_ID_2);
        messageCenterService.executeMessageAction(MSG_2, DEVICE_ID_1);

        // Check for available messages
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(2, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(3, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());


        // Hide message completely
        messageCenterService.setDoNotShowAgain(MSG_3, true);

        // Check for available messages
        assertEquals(0, messageCenterService.getMessagesForDevice(DEVICE_ID_1).size());
        assertEquals(1, messageCenterService.getMessagesForDevice(DEVICE_ID_2).size());
        assertEquals(2, messageCenterService.getMessagesForDevice(DEVICE_ID_3).size());

    }

    @Test
    public void testNoMessageForParentalControlledUser(){
        // Messages are present (to allow MessageCenterService to fail by returning them)
        MessageCenterMessage msg = new MessageCenterMessage(123, null, null, null, null, null, Collections.emptyMap(),
                Collections.emptyMap(), null, null, null);
        MessageContainer container = new MessageContainer(msg);
        List<MessageContainer> msgList = new ArrayList<>();
        msgList.add(container);
        when(dataSource.getAll(MessageContainer.class)).thenReturn(msgList);

        // Device has FAM-features
        when(productInfoService.hasFeature(any())).thenReturn(true);
        // Mocks lead to a PCed user
        Device device = Mockito.mock(Device.class);
        when(deviceService.getDeviceById(eq(DEVICE_ID_1))).thenReturn(device);
        int OPERATING_USER_ID = 10;
        when(device.getOperatingUser()).thenReturn(OPERATING_USER_ID);
        UserModule opUser = Mockito.mock(UserModule.class);
        when(userService.getUserById(eq(OPERATING_USER_ID))).thenReturn(opUser);
        int ASSOCIATED_PROFILE_ID = 11;
        when(opUser.getAssociatedProfileId()).thenReturn(ASSOCIATED_PROFILE_ID);
        UserProfileModule opUserProfile = Mockito.mock(UserProfileModule.class);
        when(parentalControlService.getProfile(eq(ASSOCIATED_PROFILE_ID))).thenReturn(opUserProfile);
        when(opUserProfile.isControlmodeMaxUsage()).thenReturn(true);
        when(opUserProfile.isControlmodeTime()).thenReturn(true);
        when(opUserProfile.isControlmodeUrls()).thenReturn(true);

        // Call function to test
        List<MessageCenterMessage> messages = messageCenterService.getMessagesForDevice(DEVICE_ID_1);

        // Verify
        // No message to be shown is returned
        assertTrue(messages.size() == 0);
        // Mocks have been called
        verify(productInfoService).hasFeature(eq(ProductFeature.FAM));
        verify(deviceService).getDeviceById(eq(DEVICE_ID_1));
        verify(device).getOperatingUser();
        verify(userService).getUserById(eq(OPERATING_USER_ID));
        verify(opUser).getAssociatedProfileId();
        verify(parentalControlService).getProfile(eq(ASSOCIATED_PROFILE_ID));
        verify(opUserProfile).isControlmodeMaxUsage();
        verify(opUserProfile, never()).isControlmodeTime();
        verify(opUserProfile, never()).isControlmodeUrls();
    }

    public enum MessageAction {
        CREATE,
        NOOP,
        DELETE,
        ;
    }

    /**
     * Simple message provider, which can easily be configured to create or delete a message
     */
    public class TestMessageProvider extends AbstractMessageProvider {

        private final int messageId;

        private MessageAction nextAction = MessageAction.NOOP;

        TestMessageProvider(int messageId) {
            this.messageId = messageId;
        }

        void setNextAction(MessageAction nextAction) {
            this.nextAction = nextAction;
        }

        @Override
        protected Set<Integer> getMessageIds() {
            return Collections.singleton(messageId);
        }

        @Override
        protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
            switch (nextAction) {

                case CREATE:
                    messageContainers.put(messageId, createMessage(messageId, null, null, null, null));
                    break;

                case DELETE:
                    messageContainers.remove(messageId);

                case NOOP:
                default:
            }
        }
    }
}