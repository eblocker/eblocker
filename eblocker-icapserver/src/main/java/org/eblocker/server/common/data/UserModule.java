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

import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.http.security.PasswordUtil;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserModule {
	// General attributes
	private Integer id;
	private Integer associatedProfileId;
	private String name;
	private String nameKey;
    private LocalDate birthday;
    private UserRole userRole;
	private boolean system;
	private byte[] pin;
	private Map<String, WhiteListConfig> whiteListConfigByDomains;

	private Integer customBlacklistId;
	private Integer customWhitelistId;

    private DashboardColumnsView dashboardColumnsView;

	@JsonCreator
	public UserModule(@JsonProperty("id") Integer id,
                      @JsonProperty("associatedProfileId") Integer associatedProfileId,
                      @JsonProperty("name") String name,
                      @JsonProperty("nameKey") String nameKey,
                      @JsonProperty("birthday") LocalDate birthday,
                      @JsonProperty("userRole") UserRole userRole,
                      @JsonProperty("system") boolean system,
                      @JsonProperty("pin") byte[] pin,
                      @JsonProperty("whiteListConfigByDomains") Map<String, WhiteListConfig> whiteListConfigByDomains,
                      @JsonProperty("dashboardColumnsView") DashboardColumnsView dashboardColumnsView,
                      @JsonProperty("customBlacklistId") Integer customBlacklistId,
                      @JsonProperty("customWhitelistId") Integer customWhitelistId) {
		this.id = id;
		this.associatedProfileId = associatedProfileId;
		this.name = name;
		this.nameKey = nameKey;
        this.birthday = birthday;
        this.userRole = userRole;
		this.system = system;
		this.pin = pin;
		this.whiteListConfigByDomains = whiteListConfigByDomains == null
				? new HashMap<>() /* do not use immutable Collections.emptyMap() here */
				: whiteListConfigByDomains;
        this.customBlacklistId = customBlacklistId;
        this.customWhitelistId = customWhitelistId;

        this.dashboardColumnsView = dashboardColumnsView;
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

	public byte[] getPin() {
		return pin;
	}

	public void setPin(byte[] pin) {
		this.pin = pin;
	}

	public void changePin(String newPin) {
		if (newPin == null || "".equals(newPin)) {
			pin = null;
		} else {
			pin = PasswordUtil.hashPassword(newPin);
		}
	}

	public Map<String, WhiteListConfig> getWhiteListConfigByDomains() {
		return whiteListConfigByDomains;
	}

	public void setWhiteListConfigByDomains(Map<String, WhiteListConfig> whiteListConfigByDomains) {
        this.whiteListConfigByDomains = (whiteListConfigByDomains == null ? new HashMap<>() : whiteListConfigByDomains);
	}

    public DashboardColumnsView getDashboardColumnsView() {
	    return this.dashboardColumnsView;
    }

    public void setDashboardColumnsView(DashboardColumnsView dashboardColumnsView) {
	    this.dashboardColumnsView = dashboardColumnsView;
    }

    public Integer getCustomBlacklistId() {
        return customBlacklistId;
    }

    public void setCustomBlacklistId(Integer customBlacklistId) {
        this.customBlacklistId = customBlacklistId;
    }

    public Integer getCustomWhitelistId() {
        return customWhitelistId;
    }

    public void setCustomWhitelistId(Integer customWhitelistId) {
        this.customWhitelistId = customWhitelistId;
    }

    @Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime
				* result
				+ ((associatedProfileId == null) ? 0 : associatedProfileId
						.hashCode());
		result = prime * result + ((id == null) ? 0 : id.hashCode());
		result = prime * result + ((name == null) ? 0 : name.hashCode());
		result = prime * result + ((nameKey == null) ? 0 : nameKey.hashCode());
		result = prime * result + (system ? 1231 : 1237);
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
		UserModule other = (UserModule) obj;
		if (associatedProfileId == null) {
			if (other.associatedProfileId != null)
				return false;
		} else if (!associatedProfileId.equals(other.associatedProfileId))
			return false;
		if (id == null) {
			if (other.id != null)
				return false;
		} else if (!id.equals(other.id))
			return false;
		if (name == null) {
			if (other.name != null)
				return false;
		} else if (!name.equals(other.name))
			return false;
		if (nameKey == null) {
			if (other.nameKey != null)
				return false;
		} else if (!nameKey.equals(other.nameKey))
			return false;
		if (system != other.system)
			return false;
		return true;
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
