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

import org.eblocker.server.common.data.UserRole;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
@JsonTypeInfo(use=JsonTypeInfo.Id.CLASS, include=JsonTypeInfo.As.PROPERTY, property="@class")
public class UiCard {

    private final int id;
    private final String name;
    private final String requiredFeature;
    private final List<UserRole> requiredUserRoles;
    private final List<AccessRight> requiredAccessRights;

    public UiCard(@JsonProperty("id") int id,
                  @JsonProperty("name") String name,
                  @JsonProperty("requiredFeature") String requiredFeature,
                  @JsonProperty("requiredUserRoles") List<UserRole> requiredUserRoles,
                  @JsonProperty("requiredAccessRights") List<AccessRight> requiredAccessRights) {
        this.id = id;
        this.requiredFeature = requiredFeature;
        this.name = name;
        this.requiredUserRoles = requiredUserRoles;
        this.requiredAccessRights = requiredAccessRights;
    }

    public int getId() {
        return id;
    }

    public String getRequiredFeature() {
        return requiredFeature;
    }

    public String getName() {
        return name;
    }

    public List<UserRole> getRequiredUserRoles() {
        return requiredUserRoles;
    }

    public List<AccessRight> getRequiredAccessRights() {
        return requiredAccessRights;
    }

}
