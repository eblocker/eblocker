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

import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;
import com.google.common.base.Joiner;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class AppModuleRemovalMessageProviderTest {

    Map<Integer, MessageContainer> messages;
    AppModuleRemovalMessageProvider messageProvider;

    @Before
    public void setup() {
        messages = Mockito.mock(Map.class);
        messageProvider = new AppModuleRemovalMessageProvider();
    }

    @Test
    public void testNoMessageGenerated() {
        messageProvider.doUpdate(messages);

        Mockito.verify(messages, Mockito.never()).remove(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId());
        Mockito.verify(messages, Mockito.never()).put(Mockito.any(), Mockito.any());
    }
    
    @Test
    public void testMessagesGenerated() {
        Set<String> names = new HashSet<>();
        names.add("Alpha-App");
        names.add("Bravo-App");
        names.add("Charlie-App");
        
        Map<String, String> expectedContext = new HashMap<>();
        expectedContext.put("appNames", Joiner.on(", ").join(names));
        MessageContainer expectedMessage = messageProvider.createMessage(
                MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId(),
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_TITLE,
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_CONTENT,
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_LABEL,
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_URL,
                expectedContext,
                false,
                MessageSeverity.INFO);

        messageProvider.addRemovedAppModules(names);
        messageProvider.doUpdate(messages);

        Mockito.verify(messages).remove(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId());
        Mockito.verify(messages).put(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId(), expectedMessage);
        
        Mockito.reset(messages);
        
        // Create another message to make sure the MessageProvider does not buffer any old names
        Set<String> namesSecond = new HashSet<>();
        namesSecond.add("Aleph-App");
        namesSecond.add("Bet-App");
        namesSecond.add("Gimel-App");
        
        Map<String, String> expectedContextSecond = new HashMap<>();
        expectedContextSecond.put("appNames", Joiner.on(", ").join(namesSecond));
        MessageContainer expectedMessageSecond = messageProvider.createMessage(
                MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId(),
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_TITLE,
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_CONTENT,
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_LABEL,
                AppModuleRemovalMessageProvider.MESSAGE_APP_MODULE_REMOVAL_URL,
                expectedContextSecond,
                false,
                MessageSeverity.INFO);

        messageProvider.addRemovedAppModules(namesSecond);
        messageProvider.doUpdate(messages);

        Mockito.verify(messages).remove(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId());
        Mockito.verify(messages).put(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId(), expectedMessageSecond);
        
    }

}
