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
package org.eblocker.server.common.data.dns;

import java.time.Instant;

public class ResolverEvent {
    private final Instant instant;
    private final String nameServer;
    private final String status;
    private final Long elapsed;

    public ResolverEvent(String value) {
        String[] values = value.split(",");
        this.instant = Instant.ofEpochMilli((long)(Double.parseDouble(values[0]) * 1000));
        this.nameServer = values[1];
        this.status = values[2];
        this.elapsed = values.length == 4 ? (long)(Double.parseDouble(values[3]) * 1000) : null;
    }

    public ResolverEvent(Instant instant, String nameServer, String status, Long elapsed) {
        this.instant = instant;
        this.nameServer = nameServer;
        this.status = status;
        this.elapsed = elapsed;
    }

    public Instant getInstant() {
        return instant;
    }

    public String getNameServer() {
        return nameServer;
    }

    public String getStatus() {
        return status;
        }

    public Long getDuration() {
        return elapsed;
    }
}
