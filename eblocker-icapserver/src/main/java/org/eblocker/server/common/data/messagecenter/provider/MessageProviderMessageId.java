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
package org.eblocker.server.common.data.messagecenter.provider;

public enum MessageProviderMessageId {
    MESSAGE_RELEASE_NOTES_ID(42),
    MESSAGE_PATCH_RELEASE_NOTES_ID(43),
    MESSAGE_LICENSE_EXPIRING_ID(100),
    MESSAGE_LICENSE_EXPIRED_ID(101),
    MESSAGE_SSL_SUPPORT_INSTALL_ID(1000),
    MESSAGE_ROUTER_PROBLEMATIC_ID(1001),
    MESSAGE_ALERT_EVENT_ID(1002),
    MESSAGE_DAILY_NEWS_ID(1003),
    MESSAGE_CERTIFICATE_EXPIRATION_WARNING(1004),
    MESSAGE_CERTIFICATE_UNTRUSTED_WARNING(1005),
    MESSAGE_DNS_LOCAL_DNS_IS_NOT_GATEWAY(1006),
    MESSAGE_DNS_UNRELIABLE_NAME_SERVER(1007),
    MESSAGE_APP_MODULES_REMOVAL_ID(1008),
    MESSAGE_FILTER_LISTS_OUTDATED_ID(1009);

    private final int id;

    MessageProviderMessageId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

}
