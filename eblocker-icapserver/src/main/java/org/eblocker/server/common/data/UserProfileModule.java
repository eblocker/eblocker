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

import java.time.DayOfWeek;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonValue;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserProfileModule {

    public enum InternetAccessRestrictionMode {
        NONE,
        BLACKLIST,
        WHITELIST;

        @JsonValue
        public int toValue() {
            return ordinal();
        }
    }

	// General attributes
    private Integer id;
    private String name;
    private String description;
    private String nameKey;
    private String descriptionKey;

    private boolean builtin;
    private boolean standard;
    private boolean hidden;

    private boolean parentalControlSettingValidated;
    private boolean forSingleUser;

    // Attributes regarding internet access/usage
    private boolean controlmodeUrls;
    private boolean controlmodeTime;
    private boolean controlmodeMaxUsage;

    // allows to block internet access no matter the restrictions
    private Boolean internetBlocked;
    // allows to give user extra time on top of maxUsageTimeByDay
    private BonusTimeUsage bonusTimeUsage;

    private InternetAccessRestrictionMode internetAccessRestrictionMode;
    private Set<Integer> accessibleSitesPackages;
    private Set<Integer> inaccessibleSitesPackages;
    private Set<InternetAccessContingent> internetAccessContingents;
    private Map<DayOfWeek, Integer> maxUsageTimeByDay;
    
    @JsonCreator
    public UserProfileModule(
            @JsonProperty("id") Integer id,
            @JsonProperty("name") String name,
            @JsonProperty("description") String description,
            @JsonProperty("nameKey") String nameKey,
            @JsonProperty("descriptionKey") String descriptionKey,
            @JsonProperty("standard") Boolean standard,
            @JsonProperty("hidden") Boolean hidden,
            @JsonProperty("accessibleSitesPackages") Set<Integer> accessibleSitesPackages,
            @JsonProperty("inaccessibleSitesPackages") Set<Integer> inaccessibleSitesPackages,
            @JsonProperty("internetAccessRestrictionMode") InternetAccessRestrictionMode internetAccessRestrictionMode,
            @JsonProperty("internetAccessContingents") Set<InternetAccessContingent> internetAccessContingents,
            @JsonProperty("maxUsageTimeByDay") Map<DayOfWeek, Integer> maxUsageTimeByDay,
            @JsonProperty("parentalControlSettingValidated") Boolean isParentalControlSettingValidated,
            @JsonProperty("internetBlocked") Boolean internetBlocked,
            @JsonProperty("bonusTimeUsage") BonusTimeUsage bonusTimeUsage
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.nameKey = nameKey;
        this.descriptionKey = descriptionKey;
        this.standard = standard == null ? false : standard;
        this.hidden = hidden == null ? false : hidden;
        this.parentalControlSettingValidated = isParentalControlSettingValidated == null ? false : isParentalControlSettingValidated;
        this.accessibleSitesPackages = accessibleSitesPackages == null ? Collections.emptySet() : accessibleSitesPackages;
        this.inaccessibleSitesPackages = inaccessibleSitesPackages == null ? Collections.emptySet() : inaccessibleSitesPackages;
        this.internetAccessRestrictionMode = internetAccessRestrictionMode;
        this.internetAccessContingents = internetAccessContingents == null ? Collections.emptySet() : internetAccessContingents;
        this.maxUsageTimeByDay = maxUsageTimeByDay == null ? new HashMap<>() : maxUsageTimeByDay;
        this.forSingleUser = false;
        this.internetBlocked = internetBlocked == null ? false : internetBlocked; // requirement for parental control card: should only be set by UI
        this.bonusTimeUsage = bonusTimeUsage;
    }
    
    //Getters and setters------------------------------------

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getNameKey() {
        return nameKey;
    }

    public void setNameKey(String nameKey) {
        this.nameKey = nameKey;
    }

    public String getDescriptionKey() {
        return descriptionKey;
    }

    public void setDescriptionKey(String descriptionKey) {
        this.descriptionKey = descriptionKey;
    }

    public boolean isStandard() {
        return standard;
    }

    public void setStandard(boolean standard) {
        this.standard = standard;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public void setAccessibleSitesPackages(Set<Integer> accessibleSitesPackages) {
    	Set<Integer> tmpPackageList = new HashSet<>();
    	for (Integer filter : accessibleSitesPackages) {
    		if (filter != null) {
    			tmpPackageList.add(filter);
    		}
    	}
    	this.accessibleSitesPackages = tmpPackageList;
    }
    
    public Set<Integer> getAccessibleSitesPackages() {
    	return accessibleSitesPackages;
    }
    
    public void setInaccessibleSitesPackages(Set<Integer> inaccessibleSitesPackages) {
    	Set<Integer> tmpPackageList = new HashSet<>();
    	for (Integer filter : inaccessibleSitesPackages) {
    		if (filter != null) {
    			tmpPackageList.add(filter);
    		}
    	}
    	this.inaccessibleSitesPackages = tmpPackageList;
    }
    
    public Set<Integer> getInaccessibleSitesPackages() {
    	return inaccessibleSitesPackages;
    }
    
    public Set<InternetAccessContingent> getInternetAccessContingents(){
    	return this.internetAccessContingents;
    }
    
    public void setInternetAccessContingents(Set<InternetAccessContingent> internetAccessContingents) {
		this.internetAccessContingents = internetAccessContingents;
    }

	public boolean isBuiltin() {
		return this.builtin;
	}
	
	public void setBuiltin(boolean builtin) {
		this.builtin = builtin;
	}

    public boolean isControlmodeUrls() {
        return controlmodeUrls;
    }

    public void setControlmodeUrls(boolean controlmodeUrls) {
        this.controlmodeUrls = controlmodeUrls;
    }

    public boolean isControlmodeTime() {
		return controlmodeTime;
	}

	public void setControlmodeTime(boolean controlmodeTime) {
		this.controlmodeTime = controlmodeTime;
	}

    public boolean isControlmodeMaxUsage() {
        return controlmodeMaxUsage;
    }

    public void setControlmodeMaxUsage(boolean controlmodeMaxUsage) {
        this.controlmodeMaxUsage = controlmodeMaxUsage;
    }

    public InternetAccessRestrictionMode getInternetAccessRestrictionMode() {
		return internetAccessRestrictionMode;
	}

	public void setInternetAccessRestrictionMode(
            InternetAccessRestrictionMode internetAccessRestrictionMode) {
		this.internetAccessRestrictionMode = internetAccessRestrictionMode;
	}

	public Map<DayOfWeek, Integer> getMaxUsageTimeByDay() {
        return maxUsageTimeByDay;
    }

    public void setMaxUsageTimeByDay(Map<DayOfWeek, Integer> maxUsageTimeByDay) {
        this.maxUsageTimeByDay = maxUsageTimeByDay;
    }

    public Boolean isInternetBlocked() {
        return internetBlocked;
    }

    public void setInternetBlocked(boolean internetBlocked) {
        this.internetBlocked = internetBlocked;
    }

    public BonusTimeUsage getBonusTimeUsage() {
        return bonusTimeUsage;
    }

    public void setBonusTimeUsage(BonusTimeUsage bonusTimeUsage) {
        this.bonusTimeUsage = bonusTimeUsage;
    }

    @Override
	public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
		UserProfileModule that = (UserProfileModule) o;
		return builtin == that.builtin &&
				standard == that.standard &&
				Objects.equals(id, that.id) &&
				Objects.equals(nameKey, that.nameKey);
	}

	@Override
	public int hashCode() {
		return Objects.hash(id, nameKey, builtin, standard);
	}

    public boolean isParentalControlSettingValidated() {
        return parentalControlSettingValidated;
    }

    public void setParentalControlSettingValidated(boolean parentalControlSettingValidated) {
        this.parentalControlSettingValidated = parentalControlSettingValidated;
    }

    public boolean isForSingleUser() {
        return forSingleUser;
    }

    public void setForSingleUser(boolean isForSingleUser) {
        this.forSingleUser = isForSingleUser;
    }

    public UserProfileModule copy() {
        UserProfileModule copy = new UserProfileModule(this.getId(), this.getName(), this.getDescription(),
                this.getNameKey(), this.getDescriptionKey(), this.isStandard(), this.isHidden(),
                this.getAccessibleSitesPackages(), this.getInaccessibleSitesPackages(), this.getInternetAccessRestrictionMode(),
                this.getInternetAccessContingents(), this.maxUsageTimeByDay, this.parentalControlSettingValidated,
            this.internetBlocked, this.bonusTimeUsage);
        copy.controlmodeMaxUsage = this.isControlmodeMaxUsage();
        copy.controlmodeTime = this.isControlmodeTime();
        copy.controlmodeUrls = this.isControlmodeUrls();
        copy.builtin = this.isBuiltin();
        copy.setForSingleUser(this.isForSingleUser());
        return copy;
    }
}
