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

import com.google.common.base.Joiner;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eblocker.server.common.data.messagecenter.MessageContainer;
import org.eblocker.server.common.data.messagecenter.MessageSeverity;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Singleton
public class AppModuleRemovalMessageProvider extends AbstractMessageProvider {

    public static final String MESSAGE_APP_MODULE_REMOVAL_TITLE = "MESSAGE_APP_MODULE_REMOVAL_TITLE";
    public static final String MESSAGE_APP_MODULE_REMOVAL_CONTENT = "MESSAGE_APP_MODULE_REMOVAL_CONTENT";
    public static final String MESSAGE_APP_MODULE_REMOVAL_LABEL = "MESSAGE_APP_MODULE_REMOVAL_LABEL";
    public static final String MESSAGE_APP_MODULE_REMOVAL_URL = "MESSAGE_APP_MODULE_REMOVAL_URL";

    private Set<String> removedAppModules;

    @Inject
    public AppModuleRemovalMessageProvider() {
        removedAppModules = new HashSet<>();
    }

    @Override
    protected Set<Integer> getMessageIds() {
        return Collections.singleton(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId());
    }

    @Override
    protected void doUpdate(Map<Integer, MessageContainer> messageContainers) {
        if (removedAppModules.isEmpty()) {
            return;
        }
        messageContainers.remove(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId());
        Map<String, String> context = new HashMap<>();
        context.put("appNames", Joiner.on(", ").join(removedAppModules));
        removedAppModules.clear();

        MessageContainer message = createMessage(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId(),
            MESSAGE_APP_MODULE_REMOVAL_TITLE,
            MESSAGE_APP_MODULE_REMOVAL_CONTENT,
            MESSAGE_APP_MODULE_REMOVAL_LABEL,
            MESSAGE_APP_MODULE_REMOVAL_URL,
            context,
            false,
            MessageSeverity.INFO);
        messageContainers.put(MessageProviderMessageId.MESSAGE_APP_MODULES_REMOVAL_ID.getId(), message);
    }

    @Override
    public boolean executeAction(int messageId) {
        return true;
    }

    public void addRemovedAppModules(Set<String> names) {
        removedAppModules.addAll(names);
    }
}
