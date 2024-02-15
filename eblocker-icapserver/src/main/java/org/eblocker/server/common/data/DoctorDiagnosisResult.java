/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
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

import java.util.Objects;

public class DoctorDiagnosisResult {
    private final Severity severity;
    private final Audience audience;
    private final Tag tag;
    private final String dynamicInfo;

    public DoctorDiagnosisResult(Severity severity, Audience audience, Tag tag, String dynamicInfo) {
        this.severity = severity;
        this.audience = audience;
        this.tag = tag;
        this.dynamicInfo = dynamicInfo;
    }

    public Severity getSeverity() {
        return severity;
    }

    public Audience getAudience() {
        return audience;
    }
    
    @JsonProperty
    public int getSeverityOrder() {
        return severity.order;
    }

    public Tag getTag() {
        return tag;
    }

    public String getDynamicInfo() {
        return dynamicInfo;
    }

    public enum Audience {
        NOVICE, EXPERT, EVERYONE
    }

    public enum Severity {
        FAILED_PROBE(0),
        ANORMALY(1),
        RECOMMENDATION_NOT_FOLLOWED(2),
        HINT(3),
        GOOD(4);
        private final int order;

        Severity(int order) {
            this.order = order;
        }
    }

    public enum Tag {
        NON_GOOD_DNS_SERVER,
        ALL_DNS_SERVERS_GOOD,
        PROBLEMATIC_ROUTER,
        CHILDREN_WITHOUT_RESTRICTIONS,
        AUTOMATIC_NETWORK_MODE,
        GOOD_NETWORK_MODE,
        AUTOMATIC_UPDATES_ENABLED,
        AUTOMATIC_UPDATES_DISABLED,
        NO_DONOR_LICENSE,
        SYSTEM_UPDATE_NEVER_RAN,
        SYSTEM_UPDATE_RAN,
        HTTPS_ENABLED,
        LAST_SYSTEM_UPDATE_OLDER_2_DAYS,
        ATA_ENABLED,
        ATA_HAS_WWW_DOMAINS,
        ATA_NOT_ENABLED,
        HTTPS_NOT_ENABLED,
        STANDARD_PATTERN_FILTER_ENABLED,
        STANDARD_PATTERN_FILTER_DISABLED,
        DDG_FILTER_ENABLED_OVERBLOCKING,
        DDG_FILTER_DISABLED,
        COOKIE_CONSENT_FILTER_ENABLED_OVERBLOCKING,
        COOKIE_CONSENT_FILTER_DISABLED,
        EBLOCKER_ENABLED_FOR_NEW_DEVICES,
        EBLOCKER_DISABLED_FOR_NEW_DEVICES,
        EBLOCKER_DNS_RESOLVE_BROKEN,
        EBLOCKER_DNS_RESOLVE_WORKING,
        EBLOCKER_IP4_PING_BROKEN,
        EBLOCKER_IP4_PING_WORKING,
        EBLOCKER_IP6_ENABLED,
        EBLOCKER_IP6_DISABLED,
        EBLOCKER_IP6_PING_BROKEN,
        EBLOCKER_IP6_PING_WORKING,
        ALL_DEVICES_USE_AUTO_BLOCK_MODE,
        DEVICES_WITHOUT_AUTO_BLOCK_MODE,
        ALL_DEVICES_USE_MALWARE_FILTER,
        DEVICES_MALWARE_FILTER_DISABLED,
        ALL_DEVICES_USE_AUTO_CONTROLBAR,
        DEVICES_WITHOUT_AUTO_CONTROLBAR,
        DNS_TOR_NOT_DISABLED,
        DNS_TOR_MAY_LEAD_TO_ERRORS,
        ADMIN_PASSWORD_NOT_SET,
        DEVICES_BLOCKING_TEST_DOMAIN,
        TEST_DOMAIN_HTTPS_WHITELISTED
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        DoctorDiagnosisResult that = (DoctorDiagnosisResult) o;
        return severity == that.severity && audience == that.audience && tag == that.tag && Objects.equals(dynamicInfo, that.dynamicInfo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(severity, audience, tag, dynamicInfo);
    }

    @Override
    public String toString() {
        return "DoctorDiagnosisResult{" +
                "severity=" + severity +
                ", audience=" + audience +
                ", tag=" + tag +
                ", dynamicInfo='" + dynamicInfo + '\'' +
                '}';
    }
}
