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

public class RecordedUrl {
    private final String recordedDomain;
    private final String recordedIp;

    private WhitelistRecommendation recommendation = WhitelistRecommendation.RECOMMENDATION_NONE;

    public enum WhitelistRecommendation {
        RECOMMENDATION_NONE, RECOMMENDATION_WHITELIST, RECOMMENDATION_BUMP;
    }

    public RecordedUrl(String ip, String url) {
        this.recordedDomain = url;
        this.recordedIp = ip;
    }

    public RecordedUrl(RecordedSSLHandshake handshake) {
        this.recordedDomain = handshake.getServername();
        this.recordedIp = handshake.getIP();
    }

    public String getRecordedDomain() {
        return this.recordedDomain;
    }

    public String getRecordedIp() {
        return this.recordedIp;
    }

    public WhitelistRecommendation getWhitelistRecommendation() {
        return this.recommendation;
    }

    public void adjustWhitelistRecommendation(
            // TODO: also consider whether the app was whitelisted/bumped
            // during the recording
            boolean correspondingAppDataRecorded) {
        if (this.recommendation == WhitelistRecommendation.RECOMMENDATION_NONE) {
            if (correspondingAppDataRecorded) {
                this.recommendation = WhitelistRecommendation.RECOMMENDATION_BUMP;
            } else {
                this.recommendation = WhitelistRecommendation.RECOMMENDATION_WHITELIST;
            }
        } else if (this.recommendation == WhitelistRecommendation.RECOMMENDATION_BUMP) {
            if (correspondingAppDataRecorded) {
                // no adjustment required
            } else {
                this.recommendation = WhitelistRecommendation.RECOMMENDATION_WHITELIST;
            }
        }
    }

    @Override
    public boolean equals(Object o) {
        RecordedUrl u = (RecordedUrl) o;
        return (u != null
                && this.recommendation == u.getWhitelistRecommendation()
                && this.recordedDomain.equals(u.getRecordedDomain())
                && this.recordedIp.equals(u.getRecordedIp()));
    }

    @Override
    public int hashCode() {
        return this.getRecordedDomain().hashCode()
                + this.getRecordedIp().hashCode()
                + this.getWhitelistRecommendation().hashCode();
    }

}
