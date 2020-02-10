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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.UserRole;
import org.eblocker.server.common.data.dashboard.AccessRight;
import org.eblocker.server.common.data.dashboard.DashboardColumnsView;
import org.eblocker.server.common.data.DataSource;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.UserModule;
import org.eblocker.server.common.data.UserProfileModule;
import org.eblocker.server.common.data.WhiteListConfig;
import com.google.inject.name.Named;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Predicate;

import java.util.stream.Collectors;

import org.eblocker.server.common.data.migrations.DefaultEntities;
import org.eblocker.server.common.data.systemstatus.SubSystem;
import org.eblocker.server.common.startup.SubSystemInit;
import org.eblocker.server.common.startup.SubSystemService;
import org.eblocker.server.http.security.PasswordUtil;
import org.restexpress.exception.BadRequestException;
import org.restexpress.exception.ConflictException;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@SubSystemService(value = SubSystem.EVENT_LISTENER, allowUninitializedCalls = false)
public class UserService {

    private static final Logger LOG = LoggerFactory.getLogger(UserService.class);
    private static final Logger STATUS = LoggerFactory.getLogger("STATUS");

    private final DataSource dataSource;
    private final DeviceService deviceService;
    private final DashboardCardService dashboardCardService;
    private final ConcurrentMap<Integer, UserModule> cache;
    private final List<UserChangeListener> listeners = new ArrayList<>();
    private final String standardUserTranslation;

    @Inject
    public UserService(DataSource datasource,
                       DeviceService deviceService,
                       DashboardCardService dashboardCardService,
                       @Named("parentalControl.standardUser.translation") String standardUserTranslation) {
        this.dataSource = datasource;
        this.deviceService = deviceService;
        this.dashboardCardService = dashboardCardService;
        cache = new ConcurrentHashMap<>(64, 0.75f, 2);
        this.standardUserTranslation = standardUserTranslation;

        this.deviceService.addListener(new DeviceService.DeviceChangeListener() {
            @Override
            public void onChange(Device device) {
                // nothing to do
            }

            @Override
            public void onDelete(Device device) {
                doDelete(device.getDefaultSystemUser());
            }

            @Override
            public void onReset(Device device) {
                doReset(device);
            }
        });
    }

    public Collection<UserModule> getUsers(boolean refresh) {
        if (refresh) {
            refresh();
        }
        return cache.values();
    }

    public UserModule createUser(
        Integer associatedProfileId,
        String name,
        String nameKey,
        LocalDate birthday,
        UserRole userRole,
        String newPin) {

        if (!isUniqueCustomerCreatedName(null, name)) {
            throw new ConflictException("Name of user must be unique");
        }

        int nextId = dataSource.nextId(UserModule.class);

        checkAndUpdateParentalControlData(nextId, userRole, null);

        UserModule newUser = new UserModule(
            nextId,
            associatedProfileId,
            name,
            nameKey,
            birthday,
            userRole,
            false,
            null,
            null,
            null, // leave null, we need the user created here to create adequate DashboardColumnsView
            null,
            null);

        // now set dashboardColumnsView based on new user's role
        newUser.setDashboardColumnsView(getDashboardForUser(newUser, newUser.getUserRole()));

        if (newPin != null){
            // New user was created with PIN
            newUser.changePin(newPin);
        }

        UserModule savedUser = saveAndCacheUser(newUser);
        notifyListeners(savedUser);

        return savedUser;
    }

    public UserModule updateUser(Integer id, Integer associatedProfileId,
                                 String name, String nameKey, LocalDate birthday, UserRole userRole, String newPin) {
        UserModule existingUser = findUpdatableUser(id);
        UserModule updatedUser;

        // ** create parentalControl card if userRole is child, update parent users dashboardCardView
        checkAndUpdateParentalControlData(id, userRole, existingUser.getUserRole());

        if (existingUser.isSystem()) {
            updatedUser = updateSystemUser(id, associatedProfileId, name, nameKey, newPin, null, existingUser);
        } else {
            updatedUser = updateRegularUser(id, associatedProfileId, name, nameKey, birthday, userRole, newPin, null, existingUser);
        }

        // ** First update the user, then get the dashboard:
        // We need the dashboard based on the new associatedProfileId
        DashboardColumnsView dashboardColumnsView = getDashboardForUser(updatedUser, userRole);
        updatedUser.setDashboardColumnsView(dashboardColumnsView);

        UserModule savedUser = dataSource.save(updatedUser, updatedUser.getId());
        cacheUser(savedUser);
        notifyListeners(savedUser);
        return savedUser;
    }

    public boolean deleteUser(Integer userId) {
        if (userId == null) {
            throw new BadRequestException("Cannot delete user without valid user id.");
        }
        // make sure it is not a system user
        if (cache.get(userId).isSystem()){
            throw new BadRequestException("Cannot delete user " + userId + ", because it is a system user.");
        }

        // may remove parental control card and update all parent's dashboardColumnView
        checkAndUpdateParentalControlData(userId, null, null);

        return doDelete(userId);
    }

    private UserModule updateSystemUser(Integer id,
                                        Integer associatedProfileId,
                                        String name, String nameKey,
                                        String newPin, DashboardColumnsView dashboardColumnsView,
                                        UserModule oldUser) {
        // For system user only updating plugAndPlayFilterWhitelistId is allowed
        if (!Objects.equals(oldUser.getAssociatedProfileId(), associatedProfileId)
            || !Objects.equals(oldUser.getName(), name)
            || !Objects.equals(oldUser.getNameKey(), nameKey)
            || newPin != null) {
            throw new BadRequestException("Cannot update user " + id + ", because it is a built-in user.");
        }
        UserModule user = new UserModule(
            oldUser.getId(),
            oldUser.getAssociatedProfileId(),
            oldUser.getName(),
            oldUser.getNameKey(),
            oldUser.getBirthday(),
            oldUser.getUserRole(),
            oldUser.isSystem(),
            oldUser.getPin(),
            oldUser.getWhiteListConfigByDomains(),
            dashboardColumnsView,
            oldUser.getCustomBlacklistId(),
            oldUser.getCustomWhitelistId());
        return user;
    }

    private UserModule updateRegularUser(Integer id,
                                         Integer associatedProfileId,
                                         String name, String nameKey,
                                         LocalDate birthday, UserRole userRole,
                                         String newPin, DashboardColumnsView dashboardColumnsView,
                                         UserModule oldUser) {
        // Check ID of associated profile actually references an existing profile
        if (dataSource.get(UserProfileModule.class,	associatedProfileId) == null) {
            throw new BadRequestException("User " + id
                + " references non-existing profile "
                + associatedProfileId);
        }
        // Check associated profile can be selected (limbo profile cannot!)
        if (associatedProfileId == DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_ID) {
            throw new BadRequestException("User " + id + " references unassignable profile "
                + DefaultEntities.PARENTAL_CONTROL_LIMBO_PROFILE_ID);
        }

        // New name must be unique
        if (!isUniqueCustomerCreatedName(id, name)) {
            throw new ConflictException("Name of user must be unique");
        }

        // Create new instance, to avoid partial changes in cache, if anything goes wrong.
        UserModule user = new UserModule(
            id,
            associatedProfileId,
            name,
            nameKey,
            birthday,
            userRole,
            false,
            oldUser.getPin(),
            oldUser.getWhiteListConfigByDomains(),
            dashboardColumnsView,
            oldUser.getCustomBlacklistId(),
            oldUser.getCustomWhitelistId()
        );
        if (newPin != null) {
            user.changePin(newPin);
        }
        return user;
    }

    public UserModule updateUser(Integer id, DashboardColumnsView columns) {
        UserModule user = findUpdatableUser(id);
        // we want to update the columns of that user. Only initialize if columns is null.
        DashboardColumnsView tmp = columns != null ? columns : getDashboardForUser(user, user.getUserRole());
        user.setDashboardColumnsView(tmp);
        return saveAndCacheUser(user);
    }

    public UserModule updateUser(Integer id, Map<String, WhiteListConfig> whiteListConfigMap) {
        UserModule user = findUpdatableUser(id);

        if (whiteListConfigMap == null) {
            user.setWhiteListConfigByDomains(new HashMap<>());
        } else {
            user.setWhiteListConfigByDomains(whiteListConfigMap);
        }

        return saveAndCacheUser(user);
    }

    public UserModule updateUser(Integer id, Integer customBlacklistId, Integer customWhitelistId) {
        UserModule user = findUpdatableUser(id);
        user.setCustomBlacklistId(customBlacklistId);
        user.setCustomWhitelistId(customWhitelistId);
        return saveAndCacheUser(user);
    }

    public UserModule updateUserDashboardView(Integer id) {
        UserModule user = findUpdatableUser(id);
        user.setDashboardColumnsView(getDashboardForUser(user, user.getUserRole()));
        return saveAndCacheUser(user);
    }

    public void updateAllDefaultDashboardView() {
        deviceService.getDevices(false).forEach(dev -> {
            updateUserDashboardView(dev.getDefaultSystemUser());
        });
    }

    public UserModule getUserById(int id){
    	UserModule user = cache.get(id);
    	if (user != null){
    		return user;
    	}
    	refresh();
    	return cache.get(id);
    }

    @SubSystemInit
    public void init() {
        refresh();
        assureConsistency();
    }

    public void refresh() {
        List<UserModule> users = dataSource.getAll(UserModule.class);
        updateCacheEntries(users);
        removeDeletedUsers(users);
    }

    private void assureConsistency() {
        boolean foundInconsistencies = false;
        Collection<Device> devices = deviceService.getDevices(true);

        //
        // Make sure, all devices have existing and consistent users assigned.
        //
        Set<Integer> defaultSystemUsers = new HashSet<>();
        for (Device device : devices) {
            foundInconsistencies = assureConsistency(device, defaultSystemUsers) || foundInconsistencies;
        }

        //
        // Find orphaned system users
        //
        Set<Integer> obsoleteSystemUsers = new HashSet<>();
        for (UserModule user: getUsers(false)) {
            if (user.isSystem() && user.getId() != DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID &&
                !defaultSystemUsers.contains(user.getId()) &&
                (user.getNameKey() == null || !user.getNameKey().equals(standardUserTranslation))) {
                obsoleteSystemUsers.add(user.getId());
            }
        }
        for (Integer userId : obsoleteSystemUsers) {
            LOG.warn("Removing obsolete system user {}", userId);
            dataSource.delete(UserModule.class, userId);
            cache.remove(userId);
            foundInconsistencies = true;
        }

        //
        // make sure standard user has standard profile assigned and that standard user is system user
        //
        getUsers(false).forEach(user -> {
            if (user.getNameKey() != null && user.getNameKey().equals(standardUserTranslation) &&
                (!user.getAssociatedProfileId().equals(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID) ||
                    !user.isSystem())) {
                user.setAssociatedProfileId(DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID);
                user.setSystem(true);
                saveAndCacheUser(user);
            }
        });

        //
        // make sure each user has updated DashboardView
        //
        getUsers(false).
            stream().
            filter(user -> user.getAssociatedProfileId() != null).
            forEach(user -> updateUserDashboardView(user.getId()));

        //
        // make sure all visible users have userRole
        //
        getUsers(false).forEach(this::assureConsistencyOfUserRole);

        STATUS.info("Checked device/user relation for {} devices: {}", devices.size(), foundInconsistencies ? "Inconsistencies found and removed" : "OK");
    }

    private void assureConsistencyOfUserRole(UserModule userModule) {
        if (!userModule.isSystem() && userModule.getAssociatedProfileId() != null && userModule.getUserRole() == null) {
            UserProfileModule profile = dataSource.get(UserProfileModule.class, userModule.getAssociatedProfileId());
            boolean hasRestrictions = profile.isControlmodeTime() || profile.isControlmodeUrls() || profile.isControlmodeMaxUsage();
            UserRole newRole = hasRestrictions ? UserRole.CHILD : UserRole.PARENT;
            userModule.setUserRole(newRole);
            saveAndCacheUser(userModule);
        }
    }

    private boolean assureConsistency(Device device, Set<Integer> defaultSystemUsers) {
        boolean foundInconsistencies = false;
        //
        // If current default system user is invalid, create a new one:
        //   - user does not exist
        //   - user is not a system user
        //   - user is already assigned to another device
        //
        if (cache.get(device.getDefaultSystemUser()) == null ||
            !cache.get(device.getDefaultSystemUser()).isSystem() ||
            defaultSystemUsers.contains(device.getDefaultSystemUser())) {
            UserModule defaultSystemUser = saveAndCacheUser(createDefaultSystemUser(device.getId()));
            LOG.warn("Cannot find valid default system user {} for device {}, created new default system user {}",
                device.getDefaultSystemUser(), device.getId(), defaultSystemUser.getId());
            device.setDefaultSystemUser(defaultSystemUser.getId());
            foundInconsistencies = true;
        }
        //
        // If current assigned user is invalid, assign default system user:
        //   - user does not exist
        //   - assigned user is system user, but is not the correct default system user
        //
        if (cache.get(device.getAssignedUser()) == null ||
            (cache.get(device.getAssignedUser()).isSystem() && device.getAssignedUser() != device.getDefaultSystemUser())) {
            LOG.warn("Cannot find valid assigned user {} of device {}, replacing with default system user {}",
                device.getAssignedUser(), device.getId(), device.getDefaultSystemUser());
            device.setAssignedUser(device.getDefaultSystemUser());
            foundInconsistencies = true;
        }
        //
        // If current operating user is invalid, declare assigned user to operating user
        //   - user does not exist
        //   - assigned user is system user, but operating user is any other user (--> If assigned user is system, operating user cannot be changed!)
        //   - assigned user is not system user, but operating user is system user other than virtual "logged-out" user (--> Device cannot be taken by system user!)
        //
        if (cache.get(device.getOperatingUser()) == null ||
            (cache.get(device.getAssignedUser()).isSystem() && device.getOperatingUser() != device.getAssignedUser()) ||
            (!cache.get(device.getAssignedUser()).isSystem() && cache.get(device.getOperatingUser()).isSystem() && device.getOperatingUser() != DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID)) {
            LOG.warn("Cannot find valid operating user {} of device {}, replacing with assigned user {}",
                device.getOperatingUser(), device.getId(), device.getAssignedUser());
            device.setOperatingUser(device.getAssignedUser());
            foundInconsistencies = true;
        }
        if (foundInconsistencies) {
            deviceService.updateDevice(device);
        }
        defaultSystemUsers.add(device.getDefaultSystemUser());
        return foundInconsistencies;
    }

    public void addListener(UserChangeListener listener) {
    	listeners.add(listener);
	}

	private void notifyListeners(UserModule user) {
    	listeners.forEach(listener -> listener.onChange(user));
	}

    private void updateCacheEntries(List<UserModule> users) {
        users.forEach(this::cacheUser);
    }

    /**
     * Removes deleted users from cache
     * @param users list of all current known users
     */
    private void removeDeletedUsers(List<UserModule> users) {
        Iterator<UserModule> i = cache.values().iterator();
        while(i.hasNext()) {
            UserModule user = i.next();
            if (!users.contains(user)) {
                i.remove();
            }
        }
    }

    private void cacheUser(UserModule user) {
        cache.put(user.getId(), user);
    }

    private UserModule saveAndCacheUser(UserModule user) {
    	UserModule savedUser = dataSource.save(user, user.getId());
        cacheUser(savedUser);
    	return savedUser;
    }

    private void doReset(Device device) {
        UserModule defaultSystemUser = restoreDefaultSystemUser(device.getId());
        device.setDefaultSystemUser(defaultSystemUser.getId());
        device.setAssignedUser(defaultSystemUser.getId());
        device.setOperatingUser(defaultSystemUser.getId());
    }

    private boolean doDelete(int userId) {
        // make sure user is not assigned to any device or logged in to any device
        for (Device device : deviceService.getDevices(true)) {
            if (device.getAssignedUser() == userId
                || device.getOperatingUser() == userId
                || device.getDefaultSystemUser() == userId) {
                return false;
            }
        }
        dataSource.delete(UserModule.class, userId);
        cache.remove(userId);
        return true;
    }

    public UserModule createDefaultSystemUser(String name) {
        int userId = dataSource.nextId(UserModule.class);
        return createDefaultSystemUser(name, userId);
    }

    private UserModule createDefaultSystemUser(String name, int userId) {
        return new UserModule(
            userId,
            DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
            name,
            DefaultEntities.USER_SYSTEM_DEFAULT_NAME_KEY,
            null,
            null,
            true,
            null,
            new HashMap<>(),
            getDashboardForUser(null, null),
            null,
            null
        );
    }

    public UserModule restoreDefaultSystemUser(String name) {
        int userId = dataSource.nextId(UserModule.class);
        return restoreDefaultSystemUser(name, userId);
    }

    public UserModule restoreDefaultSystemUser(String name, int userId) {
        UserModule defaultSystemUser = getUserById(userId);
        UserModule restoredDefaultSystemUser;
        if (defaultSystemUser == null) {
            restoredDefaultSystemUser = createDefaultSystemUser(name, userId);
            // within createDefaultSystemUser we do not yet have the default profile, so the getDashboardForUser call
            // will get the default dashboardColumnsView (not based on profile). Here we reset the the dashboardColumnsView
            // of the just created user, which is now based the correct profile.
            restoredDefaultSystemUser.setDashboardColumnsView(getDashboardForUser(restoredDefaultSystemUser, restoredDefaultSystemUser.getUserRole()));
        } else {
            restoredDefaultSystemUser = new UserModule(
                userId,
                DefaultEntities.PARENTAL_CONTROL_DEFAULT_PROFILE_ID,
                name,
                DefaultEntities.USER_SYSTEM_DEFAULT_NAME_KEY,
                null,
                null,
                true,
                defaultSystemUser.getPin(),
                defaultSystemUser.getWhiteListConfigByDomains() == null ? new HashMap<>() : defaultSystemUser.getWhiteListConfigByDomains(),
                getDashboardForUser(defaultSystemUser, defaultSystemUser.getUserRole()),
                defaultSystemUser.getCustomBlacklistId(),
                defaultSystemUser.getCustomWhitelistId()
            );
        }
        defaultSystemUser = saveAndCacheUser(restoredDefaultSystemUser);
        notifyListeners(defaultSystemUser);
        return defaultSystemUser;
    }

    public boolean isUniqueCustomerCreatedName(Integer id, String name){
		Predicate<UserModule> isCustomerCreatedUserWithSameNameButDifferentId = m ->
				!m.isSystem()
						&& m.getName() != null
						&& m.getName().equals(name)
						&& !m.getId().equals(id);
		return dataSource.getAll(UserModule.class).stream().noneMatch(isCustomerCreatedUserWithSameNameButDifferentId);
	}

	public void setPin(Integer userId, String newPin){
		UserModule user = dataSource.get(UserModule.class, userId);
		if (user==null){
			throw new BadRequestException("Invalid user id");
		}
		if (user.isSystem()){
			throw new BadRequestException("Cannot set PIN for user "
					+ user.getId() + ", because it is a built-in user.");
		}
		user.changePin(newPin);
		saveAndCacheUser(user);
	}

	public void changePin(Integer userId, String newPin, String oldPin){
		UserModule oldUser = getUserById(userId);
		// Does user whose PIN is to be changed exist?
		if (oldUser == null) {
			throw new BadRequestException("Invalid user id");
		}
		// Can user be changed?
		if (oldUser.isSystem()){
			throw new BadRequestException("Cannot change PIN for user "
					+ oldUser.getId() + ", because it is a built-in user.");
		}
		// Is change request authorized by current PIN (oldPin)? If a PIN is currently set, of course
		if (oldUser.getPin() != null
				&& (oldPin == null
					|| !PasswordUtil.verifyPassword(oldPin, oldUser.getPin()))) {
			throw new BadRequestException("Cannot change PIN for user "
				+ oldUser.getId() + ", bacause PIN verification failed.");
		}
		oldUser.changePin(newPin);
		saveAndCacheUser(oldUser);
	}

	public interface UserChangeListener {
		void onChange(UserModule user);
	}

    private UserModule findUpdatableUser(Integer id) {
        // Load user
        UserModule user = getUserById(id);

        // User must exist
        if (user == null) {
            throw new BadRequestException("User " + id + " does not exist and cannot be updated");
        }

        return user;
    }

	private void updateDashboardOfAllParents() {
        getAllParentUsers().forEach((user) -> {
            user.setDashboardColumnsView(getDashboardForUser(user, user.getUserRole()));
            saveAndCacheUser(user);
        });
    }

    private List<UserModule> getAllParentUsers() {
        return dataSource.getAll(UserModule.class).stream().
            filter(user -> user.getUserRole() != null && user.getUserRole().equals(UserRole.PARENT)).
            collect(Collectors.toList());
    }

    /**
     * Creates the DashboardColumnView for the user based on the user role.
     * Each userRole may get a different set of dashboard cards:
     * Parents can see parental-control-cards. Children can see child-cards (fragFinn, BlindeKuh).
     * @return dashboardColumnView based on user role
     */
	private DashboardColumnsView getDashboardForUser(UserModule user, UserRole userRole) {
	    // make sure that userRoles defaults to OTHER. This prevents a user from missing dashboard cards
        UserRole tmpUserRole = userRole == null ? UserRole.OTHER : userRole;
        DashboardColumnsView columns;
        List<AccessRight> accessRights = user == null ? Collections.emptyList() : getAccessRulesForUser(user);

         if (user == null || user.getDashboardColumnsView() == null) {
             columns = dashboardCardService.getNewDashboardCardColumns(tmpUserRole);
         } else {
             columns = user.getDashboardColumnsView();
         }

        return dashboardCardService.getUpdatedColumnsView(columns, tmpUserRole, accessRights);
    }

    /**
     * when a user has access restrictions, like a frag-finn-user, we explicitly define the cards
     * this user can see. All other cards will be hidden.
     * @param user
     * @return
     */
    private List<AccessRight> getAccessRulesForUser(UserModule user) {
        List<AccessRight> accessRights = new ArrayList<>();

        UserProfileModule profile = dataSource.get(UserProfileModule.class,	user.getAssociatedProfileId());

        if (profile == null) {
            return Collections.emptyList();
        }

        Set<Integer> accessible = profile.getAccessibleSitesPackages();

        if (accessible != null && accessible.stream().anyMatch(id -> id.equals(DefaultEntities.PARENTAL_CONTROL_FILTER_LIST_FRAG_FINN))) {
            accessRights.add(AccessRight.FRAG_FINN);
            accessRights.add(AccessRight.USER);
            accessRights.add(AccessRight.ONLINE_TIME);
        } else if (user.getId().equals(DefaultEntities.PARENTAL_CONTROL_LIMBO_USER_ID)) {
            accessRights.add(AccessRight.USER);
        }

        return accessRights;
    }

    /**
     *  The action create user, update user or delete user, may cause
     *    - a parental control card to be created or removed
     *       1) created if:
     *         a) a CHILD-user is created
     *         b) a PARENT/OTHER user is updated CHILD
     *       2) removed if:
     *         a) a CHILD-user is deleted
     *         b) a CHILD-user is updated to PARENT / OTHER
     *    - the parental control card to be added or removed from the dashboardColumnsView of each user
     *       - easiest way: just re-create the dashboardColumnsView based on all dashboard cards (including the
     *         new parental control card) and the user role of each user (including the created / updated user)
     * New/removed child user results in new/removed dashboard card for that user
     * Caused by: create user, remove user, update user (change of user type to/from child)
     * @param id user id. Used for child-user to set the referencingUserId on the parental control card
     * @param updatedRole the role of the new user or new role of an user being updated
     * @param oldRole the old role (only set when user is updated)
     */
    private void checkAndUpdateParentalControlData(int id, UserRole updatedRole, UserRole oldRole) {
        if (updatedRole != null && updatedRole.equals(UserRole.CHILD) &&
            (oldRole == null || !oldRole.equals(UserRole.CHILD))) {
            // newRole is CHILD and
            // either: oldRole is null (create)
            // or:     oldRole is not CHILD (update PARENT/OTHER to CHILD)
            dashboardCardService.createParentalControlCard(id, "PARENTAL_CONTROL", "FAM");
            updateDashboardOfAllParents();
        } else if ((updatedRole == null && oldRole == null) ||
            (updatedRole != null && !updatedRole.equals(UserRole.CHILD) &&
                oldRole != null && oldRole.equals(UserRole.CHILD))) {
            // either: both null (remove user)
            // or:     changed from CHILD to non CHILD (OTHER/PARENT)
            dashboardCardService.removeParentalControlCard(id);
            updateDashboardOfAllParents();
        }
    }

    public void restoreDefaultSystemUserAsUsers(Device device) {
        UserModule defaultSystemUser = restoreDefaultSystemUser(device.getId());
        device.setDefaultSystemUser(defaultSystemUser.getId());
        device.setAssignedUser(defaultSystemUser.getId());
        device.setOperatingUser(defaultSystemUser.getId());
    }
}
