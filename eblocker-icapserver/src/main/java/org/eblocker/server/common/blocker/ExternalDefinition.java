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

import java.util.Objects;

public class ExternalDefinition {
    private int id;
    private String name;
    private String description;
    private Category category;
    private Type type;
    private Integer referenceId;
    private Format format;
    private String url;
    private UpdateInterval updateInterval;
    private UpdateStatus updateStatus;
    private String updateError;
    private String file;
    private boolean enabled;
    private String filterType;

    @JsonCreator
    public ExternalDefinition(@JsonProperty("id") int id,
                              @JsonProperty("name") String name,
                              @JsonProperty("description") String description,
                              @JsonProperty("category") Category category,
                              @JsonProperty("type") Type type,
                              @JsonProperty("referenceId") Integer referenceId,
                              @JsonProperty("format") Format format,
                              @JsonProperty("url") String url,
                              @JsonProperty("updateInterval") UpdateInterval updateInterval,
                              @JsonProperty("updateStatus") UpdateStatus updateStatus,
                              @JsonProperty("updateError") String updateError,
                              @JsonProperty("file") String file,
                              @JsonProperty("enabled") boolean enabled,
                              @JsonProperty("filterType") String filterType) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.type = type;
        this.referenceId = referenceId;
        this.format = format;
        this.url = url;
        this.updateInterval = updateInterval;
        this.updateStatus = updateStatus;
        this.updateError = updateError;
        this.file = file;
        this.enabled = enabled;
        this.filterType = filterType == null ? "blacklist" : filterType;
        ;
    }

    public int getId() {
        return id;
    }

    public void setId(int id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public Type getType() {
        return type;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public Integer getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(Integer referenceId) {
        this.referenceId = referenceId;
    }

    public Format getFormat() {
        return format;
    }

    public void setFormat(Format format) {
        this.format = format;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public UpdateInterval getUpdateInterval() {
        return updateInterval;
    }

    public void setUpdateInterval(UpdateInterval updateInterval) {
        this.updateInterval = updateInterval;
    }

    public UpdateStatus getUpdateStatus() {
        return updateStatus;
    }

    public void setUpdateStatus(UpdateStatus updateStatus) {
        this.updateStatus = updateStatus;
    }

    public String getUpdateError() {
        return updateError;
    }

    public void setUpdateError(String updateError) {
        this.updateError = updateError;
    }

    public String getFile() {
        return file;
    }

    public void setFile(String file) {
        this.file = file;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getFilterType() {
        return this.filterType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ExternalDefinition that = (ExternalDefinition) o;
        return id == that.id &&
            enabled == that.enabled &&
            Objects.equals(name, that.name) &&
            Objects.equals(description, that.description) &&
            category == that.category &&
            type == that.type &&
            Objects.equals(referenceId, that.referenceId) &&
            format == that.format &&
            Objects.equals(url, that.url) &&
            updateInterval == that.updateInterval &&
            updateStatus == that.updateStatus &&
            Objects.equals(updateError, that.updateError) &&
            Objects.equals(file, that.file) &&
            Objects.equals(filterType, that.filterType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, name, description, category, type, referenceId, format, url, updateInterval, updateStatus, updateError, file, enabled, filterType);
    }
}
