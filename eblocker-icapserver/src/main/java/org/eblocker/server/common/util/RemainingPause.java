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
package org.eblocker.server.common.util;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class RemainingPause {
	private final Long pausing;
	private boolean pausingAllowed;

	@JsonCreator
	public RemainingPause(@JsonProperty("pausing") Long pausing) {
		this.pausing = pausing;
	}

	public Long getPausing() {
		return pausing;
	}

    public boolean isPausingAllowed() {
        return pausingAllowed;
    }

    public void setPausingAllowed(boolean pausingAllowed) {
        this.pausingAllowed = pausingAllowed;
    }
}