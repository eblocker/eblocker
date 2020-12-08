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
package org.eblocker.server.common.update;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.strategicgains.syntaxe.annotation.IntegerValidation;

import java.time.LocalTime;


/**
 * This configuration holds the timeframe for automatic updates. It is defined via the beginning time (beginTimeHour:beginTimeMin) and the ending time (endTimeHour:endTimeMin)
 */
public class AutomaticUpdaterConfiguration {

    @IntegerValidation(name = "BeginHour", min = 0, max = 23)
    private int beginTimeHour;
    @IntegerValidation(name = "EndHour", min = 0, max = 23)
    private int endTimeHour;
    @IntegerValidation(name = "BeginMin", min = 0, max = 59)
    private int beginTimeMin;
    @IntegerValidation(name = "EndMin", min = 0, max = 59)
    private int endTimeMin;


    @JsonProperty
    public int getBeginHour() {
        return beginTimeHour;
    }

    public void setBeginHour(int beginHour) {
        this.beginTimeHour = beginHour;
    }

    @JsonProperty
    public int getBeginMin() {
        return beginTimeMin;
    }

    public void setBeginMin(int beginMin) {
        this.beginTimeMin = beginMin;
    }

    @JsonProperty
    public int getEndHour() {
        return endTimeHour;
    }

    public void setEndHour(int endHour) {
        this.endTimeHour = endHour;
    }

    @JsonProperty
    public int getEndMin() {
        return endTimeMin;
    }

    public void setEndMin(int endMin) {
        this.endTimeMin = endMin;
    }

    public LocalTime getNotBefore() {
        return LocalTime.of(beginTimeHour, beginTimeMin);
    }

    public LocalTime getNotAfter() {
        return LocalTime.of(endTimeHour, endTimeMin);
    }

    public String toJSONString() {
        return "{\"beginHour\":" + beginTimeHour + ",\"beginMin\":" + beginTimeMin + ",\"endHour\":" + endTimeHour + ",\"endMin\":" + endTimeMin + "}";
    }
}
