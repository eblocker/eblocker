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
package org.eblocker.server.common.data.dashboard;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UiCardColumnPosition {

    private final int id;
    private final int column; // in two or three columned view this value is 2 or 3
    private final int index;
    private final boolean visible;
    private final boolean expanded;

    public UiCardColumnPosition(@JsonProperty("id") int id,
                                @JsonProperty("column") int column,
                                @JsonProperty("index") int index,
                                @JsonProperty("visible") boolean visible,
                                @JsonProperty("expanded") boolean expanded) {
        this.id = id;
        this.visible = visible;
        this.expanded = expanded;
        this.column = column;
        this.index = index;
    }

    public int getId() {
        return id;
    }

    public boolean isVisible() {
        return visible;
    }

    public boolean isExpanded() {
        return expanded;
    }

    public int getColumn() {
        return this.column;
    }

    public int getIndex() {
        return this.index;
    }
}
