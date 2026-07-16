package org.eblocker.server.http.backup;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.dashboard.UiCard;
import org.eblocker.server.http.model.CustomDomainFilterConfig;

import java.util.List;
import java.util.Map;

public class UsersBackup {
    private List<Device> devices;
    private List<UserModule> users;
    private List<UserProfileModule> profiles;
    private List<UiCard> uiCards;
    // Map user IDs to custom domain filters:
    private Map<Integer, CustomDomainFilterConfig> customDomainFilters;

    public List<Device> getDevices() {
        return devices;
    }

    public void setDevices(List<Device> devices) {
        this.devices = devices;
    }

    public List<UserModule> getUsers() {
        return users;
    }

    public void setUsers(List<UserModule> users) {
        this.users = users;
    }

    public List<UserProfileModule> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<UserProfileModule> profiles) {
        this.profiles = profiles;
    }

    public List<UiCard> getUiCards() {
        return uiCards;
    }

    public void setUiCards(List<UiCard> uiCards) {
        this.uiCards = uiCards;
    }

    public Map<Integer, CustomDomainFilterConfig> getCustomDomainFilters() {
        return customDomainFilters;
    }

    public void setCustomDomainFilters(Map<Integer, CustomDomainFilterConfig> customDomainFilters) {
        this.customDomainFilters = customDomainFilters;
    }
}
