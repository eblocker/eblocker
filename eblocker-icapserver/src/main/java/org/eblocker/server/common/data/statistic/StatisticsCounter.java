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
package org.eblocker.server.common.data.statistic;

import org.eblocker.server.common.data.IpAddress;

import java.time.Instant;

public class StatisticsCounter {
    private final Instant instant;
    private final String type;
    private final IpAddress ipAddress;
    private final String name;
    private final String reason;
    private final int value;

    public StatisticsCounter(Instant instant, String type, IpAddress ipAddress, String name, String reason, int value) {
        this.instant = instant;
        this.type = type;
        this.ipAddress = ipAddress;
        this.name = name;
        this.reason = reason;
        this.value = value;
    }

    public Instant getInstant() {
        return instant;
    }

    public String getType() {
        return type;
    }

    public IpAddress getIpAddress() {
        return ipAddress;
    }

    public String getName() {
        return name;
    }

    public String getReason() {
        return reason;
    }

    public int getValue() {
        return value;
    }
}
