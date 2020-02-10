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
import com.google.inject.Singleton;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

@Singleton
public class RouterCompatibilityMessageProvider extends AbstractMessageProvider {

    public static final String MESSAGE_ROUTER_PROBLEMATIC_TITLE = "MESSAGE_ROUTER_PROBLEMATIC_TITLE";
    public static final String MESSAGE_ROUTER_PROBLEMATIC_CONTENT = "MESSAGE_ROUTER_PROBLEMATIC_CONTENT";
    public static final String MESSAGE_ROUTER_PROBLEMATIC_LABEL = "MESSAGE_ROUTER_PROBLEMATIC_LABEL";
    public static final String MESSAGE_ROUTER_PROBLEMATIC_URL = "MESSAGE_ROUTER_PROBLEMATIC_URL";

    private Boolean problematicProvider = null;

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        if (problematicProvider == null) {
            // Don't know yet, if we have a Fritzbox 7490 or not.
            // Leave messages as they are.
            return;
        }
        if (problematicProvider) {
            if (!messageContainers.containsKey(MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId())) {
                messageContainers.put(MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId(),
                        createMessage(
                                MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId(),
                                MESSAGE_ROUTER_PROBLEMATIC_TITLE,
                                MESSAGE_ROUTER_PROBLEMATIC_CONTENT,
                                MESSAGE_ROUTER_PROBLEMATIC_LABEL,
                                MESSAGE_ROUTER_PROBLEMATIC_URL
                        ));
            }
        } else {
            if (messageContainers.containsKey(MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId())) {
                messageContainers.remove(MessageProviderMessageId.MESSAGE_ROUTER_PROBLEMATIC_ID.getId());
            }
        }
    }

    public void setProblematicProvider(boolean problematicProvider) {
        this.problematicProvider = problematicProvider;
    }
}
