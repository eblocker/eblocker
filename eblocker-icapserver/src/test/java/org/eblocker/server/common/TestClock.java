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
package org.eblocker.server.common;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/**
 * Clock implementation for unit tests.
 */
public class TestClock extends Clock {

    private Instant instant;
    private ZoneId zoneId;

    public TestClock(ZonedDateTime zonedDateTime) {
        this.instant = zonedDateTime.toInstant();
        this.zoneId = zonedDateTime.getZone();
    }

    public TestClock(LocalDateTime localDateTime) {
        setZonedDateTime(localDateTime.atZone(ZoneId.systemDefault()));
    }

    public TestClock(Instant instant, ZoneId zoneId) {
        this.instant = instant;
        this.zoneId = zoneId;
    }

    public void setInstant(Instant instant) {
        this.instant = instant;
    }

    public void setLocalDateTime(LocalDateTime localDateTime) {
        setZonedDateTime(localDateTime.atZone(zoneId));
    }

    public void setZonedDateTime(ZonedDateTime zonedDateTime) {
        this.instant = zonedDateTime.toInstant();
        this.zoneId = zonedDateTime.getZone();
    }

    @Override
    public ZoneId getZone() {
        return zoneId;
    }

    @Override
    public Clock withZone(ZoneId zone) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Instant instant() {
        return instant;
    }
}
