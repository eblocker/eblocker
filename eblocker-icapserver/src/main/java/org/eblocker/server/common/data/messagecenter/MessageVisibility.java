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
package org.eblocker.server.common.data.messagecenter;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.HashSet;
import java.util.Set;

public class MessageVisibility {

    private int messageId;

    private boolean doNotShowAgain;

    private Set<String> notForDevices;

    @JsonCreator
    public MessageVisibility(
        @JsonProperty("messageId") Integer messageId,
        @JsonProperty("doNotShowAgain") Boolean doNotShowAgain,
        @JsonProperty("notForDevices") Set<String> notForDevices) {
        this.messageId = messageId == null ? -1 : messageId;
        this.doNotShowAgain = doNotShowAgain == null ? true : doNotShowAgain;
        this.notForDevices = notForDevices == null ? new HashSet<>() : new HashSet<>(notForDevices);
    }

    public MessageVisibility(int messageId) {
        this.messageId = messageId;
        this.doNotShowAgain = false;
        this.notForDevices = new HashSet<>();
    }

    public int getMessageId() {
        return messageId;
    }

    public boolean isDoNotShowAgain() {
        return doNotShowAgain;
    }

    public Set<String> getNotForDevices() {
        return notForDevices;
    }

    public void setMessageId(int messageId) {
        this.messageId = messageId;
    }

    public void setDoNotShowAgain(boolean doNotShowAgain) {
        this.doNotShowAgain = doNotShowAgain;
    }

    public void setNotForDevices(Set<String> notForDevices) {
        this.notForDevices = notForDevices;
    }

    @JsonIgnore
    public boolean isForDevice(String deviceId) {
        if (doNotShowAgain) {
            return false;
        }
        return !notForDevices.contains(deviceId);
    }

    public void hideForDevice(String deviceId) {
        notForDevices.add(deviceId);
    }

    public void showForDevice(String deviceId) {
        notForDevices.remove(deviceId);
    }

}
