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
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class RouterCompatibilityMessageProviderTest {

    @Test
    public void test() {
        RouterCompatibilityMessageProvider routerCompatibilityMessageProvider = new RouterCompatibilityMessageProvider();
        Map<Integer, MessageContainer> messageContainers = new HashMap<>();

        //
        // No router detection result available, yet.
        //
        routerCompatibilityMessageProvider.doUpdate(messageContainers);

        assertEquals(0, messageContainers.size());

        //
        // Problematic router detected
        //
        routerCompatibilityMessageProvider.setProblematicProvider(true);

        routerCompatibilityMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());
        MessageContainer messageContainer = messageContainers.get(MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId());
        assertNotNull(messageContainer);

        //
        // Problematic router disappeared
        //
        routerCompatibilityMessageProvider.setProblematicProvider(false);

        routerCompatibilityMessageProvider.doUpdate(messageContainers);

        assertEquals(0, messageContainers.size());

        //
        // Problematic router is back
        //
        routerCompatibilityMessageProvider.setProblematicProvider(true);

        routerCompatibilityMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());

        //
        // New message provider, without detection result
        // --> Previous message should stay in map
        //
        routerCompatibilityMessageProvider = new RouterCompatibilityMessageProvider();

        routerCompatibilityMessageProvider.doUpdate(messageContainers);

        assertEquals(1, messageContainers.size());
    }
}
