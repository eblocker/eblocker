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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * A small wrapper for converting an Instant to a JSON object
 */
public class LocalTimestamp {
    private Instant instant;

    @JsonIgnore
    private LocalDateTime localDateTime;

    public LocalTimestamp(Instant instant) {
        this.instant = instant;
        this.localDateTime = LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }

    /**
     * The year of the local time
     */
    @JsonProperty
    public int getYear() {
        return localDateTime.getYear();
    }

    /**
     * The month of the local time
     */
    @JsonProperty
    public int getMonth() {
        return localDateTime.getMonthValue();
    }

    /**
     * The day of the local time
     */
    @JsonProperty
    public int getDay() {
        return localDateTime.getDayOfMonth();
    }

    /**
     * The day of the week, from 1 (Monday) to 7 (Sunday)
     */
    @JsonProperty
    public int getDayOfWeek() {
        return localDateTime.getDayOfWeek().getValue();
    }

    /**
     * The hour of the local time
     */
    @JsonProperty
    public int getHour() {
        return localDateTime.getHour();
    }

    /**
     * The minute of the local time
     */
    @JsonProperty
    public int getMinute() {
        return localDateTime.getMinute();
    }

    /**
     * The second of the local time
     */
    @JsonProperty
    public int getSecond() {
        return localDateTime.getSecond();
    }

    public Instant getInstant() {
        return instant;
    }

}
