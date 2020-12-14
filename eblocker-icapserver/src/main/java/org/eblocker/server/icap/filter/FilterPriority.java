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
package org.eblocker.server.icap.filter;

public enum FilterPriority {

    HIGHEST,
    HIGH,
    MEDIUM,
    LOW,
    LOWEST,

    DEFAULT,
    ;

    public boolean isHigherOrEqual(FilterPriority priority) {
        return this.compareTo(priority) <= 0;
    }

    public boolean isHigher(FilterPriority priority) {
        return this.compareTo(priority) < 0;
    }

    public boolean isLowerOrEqual(FilterPriority priority) {
        return this.compareTo(priority) >= 0;
    }

    public boolean isLower(FilterPriority priority) {
        return this.compareTo(priority) > 0;
    }

    public static FilterPriority fromName(String name) {
        for (FilterPriority priority : FilterPriority.values()) {
            if (priority.name().equals(name)) {
                return priority;
            }
        }
        return null;
    }

}
