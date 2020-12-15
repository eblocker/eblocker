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
package org.eblocker.server.common.data.systemstatus;

public enum SubSystem {

    HTTP_SERVER(1000),

    DATABASE_CLIENT(2000),

    EVENT_LISTENER(2500),

    BACKGROUND_TASKS(2700),

    ICAP_SERVER(3000),

    NETWORK_STATE_MACHINE(3500),

    HTTPS_SERVER(4000),

    SERVICES(4500),

    REST_SERVER(5000),

    // Overall state of the system (not a specific sub-system)
    EBLOCKER_CORE(9999),
    ;

    private final int order;

    SubSystem(int order) {
        this.order = order;
    }

    public int getOrder() {
        return order;
    }

}
