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

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserModuleTransport {
    private Integer id;
    private Integer associatedProfileId;
    private String name;
    private String nameKey;
    private LocalDate birthday;
    private UserRole userRole;
    private boolean system;
    private boolean containsPin;
    private String oldPin;
    private String newPin;

    @JsonCreator
    public UserModuleTransport(@JsonProperty("id") Integer id,
                               @JsonProperty("associatedProfileId") Integer associatedProfileId,
                               @JsonProperty("name") String name,
                               @JsonProperty("nameKey") String nameKey,
                               @JsonProperty("birthday") LocalDate birthday,
                               @JsonProperty("userRole") UserRole userRole,
                               @JsonProperty("system") boolean system,
                               @JsonProperty("containsPin") boolean containsPin,
                               @JsonProperty("oldPin") String oldPin,
                               @JsonProperty("newPin") String newPin) {
        this.id = id;
        this.associatedProfileId = associatedProfileId;
        this.name = name;
        this.nameKey = nameKey;
        this.birthday = birthday;
        this.userRole = userRole;
        this.system = system;
        this.containsPin = containsPin;
        this.oldPin = oldPin;

        this.newPin = newPin;
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getAssociatedProfileId() {
        return associatedProfileId;
    }

    public void setAssociatedProfileId(Integer associatedProfileId) {
        this.associatedProfileId = associatedProfileId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getNameKey() {
        return nameKey;
    }

    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system) {
        this.system = system;
    }

    public boolean containsPin() {
        return containsPin;
    }

    public void setContainsPin(boolean containsPin) {
        this.containsPin = containsPin;
    }

    public String getOldPin() {
        return oldPin;
    }

    public void setOldPin(String oldPin) {
        this.oldPin = oldPin;
    }

    public String getNewPin() {
        return newPin;
    }

    public void setNewPin(String newPin) {
        this.newPin = newPin;
    }

    public LocalDate getBirthday() {
        return birthday;
    }

    public void setBirthday(LocalDate birthday) {
        this.birthday = birthday;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void setUserRole(UserRole userRole) {
        this.userRole = userRole;
    }
}
