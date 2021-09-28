package org.eblocker.server.common.data;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        EBLOCKER_NO_IP6,
        ALL_DEVICES_USE_AUTO_BLOCK_MODE,
        DEVICES_WITHOUT_AUTO_BLOCK_MODE,
        ALL_DEVICES_USE_MALWARE_FILTER,
        DEVICES_MALWARE_FILTER_DISABLED,
        ALL_DEVICES_USE_AUTO_CONTROLBAR,
        DEVICES_WITHOUT_AUTO_CONTROLBAR
    }
}
