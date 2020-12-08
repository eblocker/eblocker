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
package org.eblocker.server.http.security;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Date;

public class PasswordResetToken {

    private final String resetToken;

    private final Date validTill;

    private final long shutdownGracePeriod;

    private final boolean autoUpdateActive;

    @JsonCreator
    public PasswordResetToken(
        @JsonProperty("resetToken") String resetToken,
        @JsonProperty("validTill") Date validTill,
        @JsonProperty("shutdownGracePeriod") Long shutdownGracePeriod,
        @JsonProperty("autoUpdateActive") Boolean autoUpdateActive) {
        this.resetToken = resetToken;
        this.validTill = validTill;
        this.shutdownGracePeriod = shutdownGracePeriod == null ? 300L : shutdownGracePeriod;
        this.autoUpdateActive = autoUpdateActive == null ? false : autoUpdateActive;
    }

    public String getResetToken() {
        return resetToken;
    }

    public Date getValidTill() {
        return validTill;
    }

    public long getShutdownGracePeriod() {
        return shutdownGracePeriod;
    }

    public boolean isAutoUpdateActive() {
        return autoUpdateActive;
    }
}
