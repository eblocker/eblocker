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
package org.eblocker.server.common.blocker;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

public class Blocker {

    private final Integer id;
    private final Map<String, String> name;
    private final Map<String, String> description;
    private final BlockerType type;
    private final Category category;
    private final Long lastUpdate;
    private final boolean providedByEblocker;
    private final String url;
    private final String content;
    private final Format format;
    private final String error;
    private final UpdateInterval updateInterval;
    private final UpdateStatus updateStatus;
    private final boolean enabled;
    private final String filterType;

    @JsonCreator
    public Blocker(@JsonProperty("id") Integer id,
                   @JsonProperty("name") Map<String, String> name,
                   @JsonProperty("description") Map<String, String> description,
                   @JsonProperty("type") BlockerType type,
                   @JsonProperty("category") Category category,
                   @JsonProperty("lastUpdate") Long lastUpdate,
                   @JsonProperty("providedByEblocker") boolean providedByEblocker,
                   @JsonProperty("url") String url,
                   @JsonProperty("content") String content,
                   @JsonProperty("format") Format format,
                   @JsonProperty("error") String error,
                   @JsonProperty("updateInterval") UpdateInterval updateInterval,
                   @JsonProperty("updateStatus") UpdateStatus updateStatus,
                   @JsonProperty("enabled") boolean enabled,
                   @JsonProperty("filterType") String filterType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.type = type;
        this.category = category;
        this.lastUpdate = lastUpdate;
        this.providedByEblocker = providedByEblocker;
        this.url = url;
        this.content = content;
        this.format = format;
        this.error = error;
        this.updateInterval = updateInterval;
        this.updateStatus = updateStatus;
        this.enabled = enabled;
        this.filterType = filterType == null ? "blacklist" : filterType;
    }

    public Integer getId() {
        return id;
    }

    public Map<String, String> getName() {
        return name;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public BlockerType getType() {
        return type;
    }

    public Category getCategory() {
        return category;
    }

    public Long getLastUpdate() {
        return lastUpdate;
    }

    public boolean isProvidedByEblocker() {
        return providedByEblocker;
    }

    public String getUrl() {
        return url;
    }

    public String getContent() {
        return content;
    }

    public Format getFormat() {
        return format;
    }

    public String getError() {
        return error;
    }

    public UpdateInterval getUpdateInterval() {
        return updateInterval;
    }

    public UpdateStatus getUpdateStatus() {
        return updateStatus;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String getFilterType() {
        return this.filterType;
    }
}
