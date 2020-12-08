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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;

/**
 * This class is used to wrap the map of TOR exit node country names -> country codes to an object than can be used to easily generate a
 * JSON representation. So basically it has no functionality, it just stores and wraps the information.
 */
public class ExitNodeCountry {

    private String name;
    private String code;

    public ExitNodeCountry(String name, String code) {
        this.name = name;
        this.code = code;
    }

    @JsonProperty
    public String getName() {
        return name;
    }

    @JsonProperty
    public String getCode() {
        return code;
    }


    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        if (obj.getClass() != getClass()) {
            return false;
        }

        ExitNodeCountry rhs = (ExitNodeCountry) obj;
        return new EqualsBuilder()
            .append(name, rhs.name)
            .append(code, rhs.code)
            .isEquals();
    }

    @Override
    public int hashCode() {
        return new HashCodeBuilder(35, 13)
            .append(name)
            .append(code)
            .toHashCode();
    }
}
