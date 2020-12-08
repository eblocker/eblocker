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
package org.eblocker.server.common.status;

public class Message {
    private MessageStatus status;
    private String text;

    public Message(MessageStatus status, String message) {
        this.status = status;
        this.text = message;
    }

    public String render() {
        return String.format("<div class=\"%s\">%s</div>", status.toString().toLowerCase(), text);
    }

    @Override
    public String toString() {
        return String.format("%s: %s", status.toString(), text);
    }
}
