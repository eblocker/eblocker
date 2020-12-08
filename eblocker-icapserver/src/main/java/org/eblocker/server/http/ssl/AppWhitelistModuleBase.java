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
package org.eblocker.server.http.ssl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * This class will be a container for the information needed for the App/User-Agent modules which bundle
 * whitelistable URLs to enable using certain Apps or Software with the SSL feature. This is useful for applications, which
 * use HPKP to pin a public key and make sure by this to only establish TLS connections with specific TLS certificates (public keys)
 * <p>
 * Also contained are IPs to be whitelisted for the respective app to work since some apps use their proprietary protocoll over
 * a standard HTTPS port which cannot be whitelisted via regular SSL-whitelisting.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public abstract class AppWhitelistModuleBase {

    private Integer id;
    protected String name;
    private Map<String, String> description;
    private Map<String, String> labels;
    private boolean enabledPerDefault; // = false;
    private boolean enabled; // = false;
    private boolean builtin; // = true;
    private String version;
    private boolean modified; // = false;
    private boolean updatedVersionAvailable;
    private boolean hidden; // = false;

    protected static final int IP_RANGE_RANGE_THRESHOLD = 8;

    @JsonCreator
    public AppWhitelistModuleBase(
        @JsonProperty("id") Integer id,
        @JsonProperty("name") String name,
        @JsonProperty("description") Map<String, String> description,
        @JsonProperty("labels") Map<String, String> labels,
        @JsonProperty("enabledPerDefault") Boolean enabledPerDefault,
        @JsonProperty("enabled") Boolean enabled,
        @JsonProperty("builtin") Boolean builtin,
        @JsonProperty("modified") Boolean modified,
        @JsonProperty("version") String version,
        @JsonProperty("updatedVersionAvailable") Boolean updatedVersionAvailable,
        @JsonProperty("hidden") Boolean hidden
    ) {
        this.id = id;
        this.name = name;
        this.description = description == null ? Collections.emptyMap() : description;
        this.labels = labels == null ? Collections.emptyMap() : labels;
        this.enabledPerDefault = enabledPerDefault == null ? false : enabledPerDefault;
        this.enabled = enabled == null ? false : enabled;
        this.builtin = builtin == null ? true : builtin;
        this.modified = modified == null ? false : modified;
        this.version = version; // null is valid for older releases than < 0.9.4
        this.updatedVersionAvailable = updatedVersionAvailable == null ? false : updatedVersionAvailable;
        this.hidden = hidden == null ? false : hidden;
    }

    public AppWhitelistModuleBase(AppWhitelistModuleBase module) {
        this.id = module.getId();
        this.name = module.getName();
        this.description = module.getDescription();
        this.labels = module.getLabels();
        this.enabledPerDefault = module.isEnabledPerDefault();
        this.enabled = module.isEnabled();
        this.builtin = module.isBuiltin();
        this.modified = module.isModified();
        this.version = module.getVersion();
        this.updatedVersionAvailable = module.isUpdatedVersionAvailable();
        this.hidden = module.isHidden();
    }

    //Getters and setters------------------------------------

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setDescription(Map<String, String> description) {
        this.description = description;
    }

    public Map<String, String> getDescription() {
        return description;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Map<String, String> getLabels() {
        return labels;
    }

    public void setLabels(Map<String, String> labels) {
        this.labels = labels;
    }

    public void setEnabledPerDefault(Boolean enabledPerDefault) {
        this.enabledPerDefault = enabledPerDefault;
    }

    public boolean isEnabledPerDefault() {
        return enabledPerDefault;
    }

    public void setBuiltin(Boolean builtin) {
        this.builtin = builtin;
    }

    public boolean isBuiltin() {
        return builtin;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public boolean isUpdatedVersionAvailable() {
        return updatedVersionAvailable;
    }

    public void setUpdatedVersionAvailable(boolean updatedVersionAvailable) {
        this.updatedVersionAvailable = updatedVersionAvailable;
    }

    public void setModified(Boolean modified) {
        this.modified = modified;
    }

    public boolean isModified() {
        return modified;
    }

    public void setHidden(Boolean hidden) {
        this.hidden = hidden;
    }

    public boolean isHidden() {
        return hidden;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AppWhitelistModuleBase module = (AppWhitelistModuleBase) o;
        return Objects.equals(name, module.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    public enum State {
        DEFAULT,
        ENABLED,
        DISABLED,
        MODULE_NOT_FOUND
    }

    @Override
    public String toString() {
        return String.format("AppWhitelistModule '%s' (id=%d)", name, id);
    }

}
