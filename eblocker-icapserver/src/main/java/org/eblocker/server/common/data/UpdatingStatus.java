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
package org.eblocker.server.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eblocker.server.common.update.AutomaticUpdaterConfiguration;

import java.util.List;

public class UpdatingStatus {
    private boolean updating = false;
    private boolean downloading = false;
    private boolean checking = false;
    private boolean recovering = false;
    private boolean updatesAvailable = false;
    private boolean automaticUpdatesActivated = false;
    private boolean automaticUpdatesAllowed = false;
    private boolean disabled = false;
    private boolean lastUpdateAttemptFailed = false;
    private String lastAutomaticUpdate = "";
    private String nextAutomaticUpdate = "";
    private String projectVersion;
    private String listsPacketVersion;
    private List<String> updateProgress;
    private List<String> updateablePackages;

    @SuppressWarnings("unused")
    private int beginHour;
    @SuppressWarnings("unused")
    private int beginMin;
    @SuppressWarnings("unused")
    private int endHour;
    @SuppressWarnings("unused")
    private int endMin;

    @JsonProperty
    public boolean isUpdating() {
        return updating;
    }

    @JsonProperty
    public boolean isDownloading() {
        return downloading;
    }

    @JsonProperty
    public boolean isChecking() {
        return checking;
    }

    @JsonProperty
    public boolean isRecovering() {
        return recovering;
    }

    @JsonProperty
    public boolean isDisabled() {
        return disabled;
    }

    public boolean isIdling() {
        return !checking && !downloading && !updating;
    }

    @JsonProperty
    public List<String> getUpdateProgress() {
        return updateProgress;
    }

    @JsonProperty
    public String getLastAutomaticUpdate() {
        return lastAutomaticUpdate;
    }

    @JsonProperty
    public String getNextAutomaticUpdate() {
        return nextAutomaticUpdate;
    }

    @JsonProperty
    public boolean getUpdatesAvailable() {
        return updatesAvailable;
    }

    @JsonProperty
    public boolean isAutomaticUpdatesActivated() {
        return automaticUpdatesActivated;
    }

    @JsonProperty
    public boolean isAutomaticUpdatesAllowed() {
        return automaticUpdatesAllowed;
    }

    @JsonProperty
    public String getProjectVersion() {
        return projectVersion;
    }

    public void setProjectVersion(String projectVersion) {
        this.projectVersion = projectVersion;
    }

    @JsonProperty
    public String getListsPacketVersion() {
        return this.listsPacketVersion;
    }

    @JsonProperty
    public List<String> getUpdateablePackages() {
        return this.updateablePackages;
    }

    @JsonProperty
    public boolean isLastUpdateAttemptFailed() {
        return lastUpdateAttemptFailed;
    }

    public void setLastUpdateAttemptFailed(boolean lastUpdateAttemptFailed) {
        this.lastUpdateAttemptFailed = lastUpdateAttemptFailed;
    }

    public void setListsPacketVersion(String listsPacketVersion) {
        this.listsPacketVersion = listsPacketVersion;
    }

    public void setConfig(AutomaticUpdaterConfiguration config) {
        if (config != null) {
            this.beginHour = config.getBeginHour();
            this.beginMin = config.getBeginMin();
            this.endHour = config.getEndHour();
            this.endMin = config.getEndMin();
        }
    }

    public void setLastAutomaticUpdate(String lastUpdate) {
        this.lastAutomaticUpdate = lastUpdate;
    }

    public void setNextAutomaticUpdate(String nextUpdate) {
        this.nextAutomaticUpdate = nextUpdate;
    }

    public void setUpdating(boolean updating) {
        this.updating = updating;
    }

    public void setDownloading(boolean downloading) {
        this.downloading = downloading;
    }

    public void setChecking(boolean checking) {
        this.checking = checking;
    }

    public void setRecovering(boolean recovering) {
        this.recovering = recovering;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public void setUpdatesAvailable(boolean updatesAvailable) {
        this.updatesAvailable = updatesAvailable;
    }

    public void activateAutomaticUpdates(boolean autoUpdates) {
        this.automaticUpdatesActivated = autoUpdates;
    }

    public void setAutomaticUpdatesAllowed(boolean allowed) {
        this.automaticUpdatesAllowed = allowed;
    }

    public void setUpdateProgress(List<String> list) {
        updateProgress = list;
    }

    public void setUpdateablePackages(List<String> packages) {
        updateablePackages = packages;
    }
}
