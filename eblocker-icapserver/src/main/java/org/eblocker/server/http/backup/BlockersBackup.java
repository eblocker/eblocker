/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.http.backup;

import org.eblocker.server.common.blocker.Blocker;
import org.eblocker.server.common.blocker.TypeId;
import org.eblocker.server.common.data.CompressionMode;

import java.util.List;
import java.util.Map;

public class BlockersBackup {
    private List<Blocker> blockers;
    private Map<Integer, TypeId> typeIds; // IDs of blockers are only temporary. So we need to map them to (Type, ID) tuples.

    private boolean captivePortalRedirectorState;
    private boolean dntHeaderState;
    private boolean httpReferrerRemovingState;
    private boolean webRtcBlockingState;
    private CompressionMode compressionMode;

    public List<Blocker> getBlockers() {
        return blockers;
    }

    public void setBlockers(List<Blocker> blockers) {
        this.blockers = blockers;
    }

    public Map<Integer, TypeId> getTypeIds() {
        return typeIds;
    }

    public void setTypeIds(Map<Integer, TypeId> typeIds) {
        this.typeIds = typeIds;
    }

    public boolean getCaptivePortalRedirectorState() {
        return captivePortalRedirectorState;
    }

    public void setCaptivePortalRedirectorState(boolean captivePortalRedirectorState) {
        this.captivePortalRedirectorState = captivePortalRedirectorState;
    }

    public boolean getDntHeaderState() {
        return dntHeaderState;
    }

    public void setDntHeaderState(boolean dntHeaderState) {
        this.dntHeaderState = dntHeaderState;
    }

    public boolean getHttpReferrerRemovingState() {
        return httpReferrerRemovingState;
    }

    public void setHttpReferrerRemovingState(boolean httpReferrerRemovingState) {
        this.httpReferrerRemovingState = httpReferrerRemovingState;
    }

    public boolean getWebRtcBlockingState() {
        return webRtcBlockingState;
    }

    public void setWebRtcBlockingState(boolean webRtcBlockingState) {
        this.webRtcBlockingState = webRtcBlockingState;
    }

    public CompressionMode getCompressionMode() {
        return compressionMode;
    }

    public void setCompressionMode(CompressionMode compressionMode) {
        this.compressionMode = compressionMode;
    }
}
