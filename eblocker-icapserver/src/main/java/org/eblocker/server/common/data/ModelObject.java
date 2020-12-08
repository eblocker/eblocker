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

import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;


/**
 * Base class for all data objects that are stored in the key-value store.
 * Objects are usually stored as a hash under a globally unique identifier.
 */
public class ModelObject {
    /**
     * A globally unique identifier for this object.
     */
    private String id;

    @Override
    public int hashCode() {
        return new HashCodeBuilder(13, 17)
            .append(getId())
            .toHashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof ModelObject)) {
            return false;
        }
        if (obj == this) {
            return true;
        }

        ModelObject rhs = (ModelObject) obj;
        return new EqualsBuilder()
            .append(getId(), rhs.getId())
            .isEquals();
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

}
