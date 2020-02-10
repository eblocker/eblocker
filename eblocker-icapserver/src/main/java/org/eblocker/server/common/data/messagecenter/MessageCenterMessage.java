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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;
import java.util.Map;

public class MessageCenterMessage {

    private final int id;

    private final String titleKey;

    private final String contentKey;

    private final String actionButtonLabelKey;

    private final String actionButtonUrlKey;

    private final Map<String, String> titles;

    private final Map<String, String> contents;

    private final Map<String, String> context;

    private final Date date;

    private final boolean showDoNotShowAgain;

    private final MessageSeverity messageSeverity;

    @JsonCreator
    public MessageCenterMessage(
            @JsonProperty("id") Integer id,
            @JsonProperty("titleKey") String titleKey,
            @JsonProperty("contentKey") String contentKey,
            @JsonProperty("actionButtonLabelKey") String actionButtonLabelKey,
            @JsonProperty("actionButtonUrlKey") String actionButtonUrlKey,
            @JsonProperty("titles") Map<String, String> titles,
            @JsonProperty("contents") Map<String, String> contents,
            @JsonProperty("context") Map<String, String> context,
            @JsonProperty("date") Date date,
            @JsonProperty("showDoNotShowAgain") Boolean showDoNotShowAgain,
            @JsonProperty("messageSeverity") MessageSeverity messageSeverity) {
        this.id = id == null ? 0 : id;
        this.titleKey = titleKey;
        this.contentKey = contentKey;
        this.actionButtonLabelKey = actionButtonLabelKey;
        this.actionButtonUrlKey = actionButtonUrlKey;
        this.titles = titles;
        this.contents = contents;
        this.context = context;
        this.date = date;
        this.showDoNotShowAgain = showDoNotShowAgain == null ? true : showDoNotShowAgain;
        this.messageSeverity = messageSeverity == null ? MessageSeverity.INFO : messageSeverity;
    }

    public int getId() {
        return id;
    }

    public String getTitleKey() {
        return titleKey;
    }

    public String getContentKey() {
        return contentKey;
    }

    public String getActionButtonLabelKey() {
        return actionButtonLabelKey;
    }

    public String getActionButtonUrlKey() {
        return actionButtonUrlKey;
    }

    public Map<String, String> getTitles() {
        return titles;
    }

    public Map<String, String> getContents() {
        return contents;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public Date getDate() {
        return date;
    }

    public boolean isShowDoNotShowAgain() {
        return showDoNotShowAgain;
    }

    public MessageSeverity getMessageSeverity() {
        return this.messageSeverity;
    }
}
