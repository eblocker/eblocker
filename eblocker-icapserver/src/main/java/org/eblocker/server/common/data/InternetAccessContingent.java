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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class InternetAccessContingent {
	private Integer onDay;
	private Integer fromMinutes;
	private Integer tillMinutes;
	private Integer totalMinutes;

	public enum CONTINGENT_DAY {
		MONDAY(1), TUESDAY(2), WEDNESDAY(3), THURSDAY(4), FRIDAY(5), SATURDAY(6), SUNDAY(7), WEEKDAY(8), WEEKEND(9)
		;

        public final int value;
        private CONTINGENT_DAY(int n) { value = n; }        
	}

	@JsonCreator
	public InternetAccessContingent(
			@JsonProperty("onDay") Integer onDay,
			@JsonProperty("fromMinutes") Integer fromMinutes,
			@JsonProperty("tillMinutes") Integer tillMinutes,
			@JsonProperty("totalMinutes") Integer totalMinutes) {
		this.onDay = onDay;
		this.fromMinutes = fromMinutes;
		this.tillMinutes = tillMinutes;
		this.totalMinutes = totalMinutes;
	}
	
    //Getters and setters------------------------------------

	public Integer getFromMinutes() {
    	return fromMinutes;
    }
    
    public Integer getTillMinutes() {
    	return tillMinutes;
    }
    
    public Integer getTotalMinutes() {
    	return totalMinutes;
    }

	public Integer getOnDay() {
		return onDay;
	}

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((fromMinutes == null) ? 0 : fromMinutes.hashCode());
        result = prime * result + ((onDay == null) ? 0 : onDay.hashCode());
        result = prime * result + ((tillMinutes == null) ? 0 : tillMinutes.hashCode());
        result = prime * result + ((totalMinutes == null) ? 0 : totalMinutes.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        InternetAccessContingent other = (InternetAccessContingent) obj;
        if (fromMinutes == null) {
            if (other.fromMinutes != null)
                return false;
        } else if (!fromMinutes.equals(other.fromMinutes))
            return false;
        if (onDay == null) {
            if (other.onDay != null)
                return false;
        } else if (!onDay.equals(other.onDay))
            return false;
        if (tillMinutes == null) {
            if (other.tillMinutes != null)
                return false;
        } else if (!tillMinutes.equals(other.tillMinutes))
            return false;
        if (totalMinutes == null) {
            if (other.totalMinutes != null)
                return false;
        } else if (!totalMinutes.equals(other.totalMinutes))
            return false;
        return true;
    }
}
