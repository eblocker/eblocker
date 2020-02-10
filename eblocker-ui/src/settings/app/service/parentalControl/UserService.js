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
export default function UserService(logger, $http, $q, $translate, NotificationService, DataCachingService,
                                    UserProfileService, LanguageService, USER_ROLES) {
    'ngInject';

    const  PATH = '/api/adminconsole/users';

    function saveNewUser(user){
        return $http.post(PATH, stripDownUser(user)).then(function(response){
            invalidateCache();
            return normalizeUser(response.data);
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.SAVE_NEW', response);
            $q.reject(response);
        });
    }

    function getUserRoles() {
        return USER_ROLES;
    }

    function stripDownUser(user){
        if (!user){
            return {};
        }

        let javaDate;
        if (user.birthday) {
            const date = angular.isNumber(user.birthday) ? new Date(user.birthday) : undefined;
            javaDate = angular.isDate(date) ? [date.getFullYear(), date.getMonth() + 1, date.getDate()] : undefined;
        }

        return {
            id: user.id,
            name: user.name,
            nameKey: user.nameKey,
            associatedProfileId: user.associatedProfileId,
            containsPin: (angular.isDefined(user.newPin) && user.newPin !== ''),
            newPin: user.newPin,
            // birthday to Java LocalDate, account for Month starting at 0 on Javascript Date
            birthday: javaDate,
            userRole: user.userRole
        };
    }

    function normalizeUser(user){
        if (angular.isUndefined(user)){
            return undefined;
        }
        if (angular.isArray(user.birthday)) {
            const b = user.birthday;
            // Java LocalDate to Javascript Date
            // b[0] Year
            // b[1] Month (January == 1)
            // b[2] Day
            const date = new Date(b[0], b[1] - 1, b[2], 0, 0, 0, 0);
            user.birthday = date.getTime();
        }

        // set user type sorting (parent, child, other, other-devices)
        if (user.nameKey === 'SHARED.USER.NAME.STANDARD_USER') {
            user.type = '~'; // highest ascii sorting key, sort standard user (other devices) at bottom
        } else {
            user.type = $translate.instant('SHARED.USER.TYPE.' + user.userRole);
        }

        user.assignedToDevices = [];
        return user;
    }

    let userCache;

    function getAll(reload){
        userCache = DataCachingService.loadCache(userCache, PATH, reload).then(function(response){
            const users = response.data;
            for (let index = 0; index < users.length; index++){
                normalizeUser(users[index]);
            }
            return $q.resolve({data: users});
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.GET_ALL', response);
            return $q.reject(response);
        });
        return userCache;
    }

    function invalidateCache() {
        userCache = undefined;
    }

    function updateUser(user){
        return $http.put(PATH, stripDownUser(user)).then(function(response){
            invalidateCache();
            return normalizeUser(response.data);
        }, function(response){
            // Error
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.SAVE_USER', response);
        });
    }

    function updateUserDashboardView(id) {
        return $http.put(PATH + '/dashboard/update/' + id).then(function(){
            invalidateCache();
            return true;
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.SAVE_USER', response);
            return $q.reject(false);
        });
    }

    function updateDashboardViewOfAllDefaultSystemUsers() {
        return $http.put(PATH + '/dashboard/updateall').then(function(){
            invalidateCache();
            return true;
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.SAVE_USER', response);
            return $q.reject(false);
        });
    }

    function deleteUser(id) {
        return $http.delete(PATH + '/' + id).then(function(){
            invalidateCache();
            return true;
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.DELETE_USER', response);
            return false;
        });
    }

    function deleteAllUsers(ids) {
        return $http.post(PATH + '/all', ids).then(function(){
            invalidateCache();
            return true;
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.DELETE_USER', response);
            return false;

        });
    }

    function savePin(user){
        return $http.post(PATH + '/' + user.id + '/pin', stripDownUser(user)).then(function(){
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.SET_PIN');
            return $q.reject(response);
        });
    }

    function resetPin(id) {
        return $http.delete(PATH + '/' + id + '/pin').then(function(response){
            // do nothing special
            NotificationService.info('ADMINCONSOLE.USER_SERVICE.INFO.RESET_PIN');
            return response;
        }, function(response){
            NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.RESET_PIN');
            return response;
        });
    }

    function uniqueName(name, id){
        return $http.get(PATH + '/unique?name=' + encodeURIComponent(name) +
            (angular.isDefined(id) ? '&id=' + id : '')).then(function(response){
                return response;
            }, function(response){
                if (response.status === 409){
                    return $q.reject(response);
                }else{
                    NotificationService.error('ADMINCONSOLE.USER_SERVICE.ERROR.UNIQUE', response);
                    return $q.reject(response);
                }
            });
    }

    /**
     * TODO: this function still does too much:
     * - set assignedToDevices and operatingDevices per user
     * - set isOnline per user that is online
     * - set displayName for each device
     * - update assignedToDevices of standard user
     * Cannot have single return value that way / should not update devices / should not update users directly, use
     * return instead
     */
    function processUserDeviceAssociations(userList, deviceList) {
        // temporary map to simplify association of devices
        const userMap = {};
        let standardUserId;
        const unassignedDevices = [];

        userList.forEach((user) => {
            user.assignedToDevices = [];
            user.operatingDevices = [];
            user.isOnline = 0;
            userMap[user.id] = user;

            if (user.nameKey === 'SHARED.USER.NAME.STANDARD_USER') {
                standardUserId = user.id;
            }
        });

        // assemble assigned / operated devices for each user
        deviceList.forEach((device) => {
            if (!device.isEblocker && !device.isGateway) {

                // ** Device is essentially unassigned (assigned to default system user)
                // --> so we also assign the device to the standard user. Standard user is a proxy user for all
                // unassigned devices. These devices have own user. But the profile of that user cannot be changed
                // in the new UI. So we use a standard user to allow the customer to update the standard profile,
                // which is used by all users of unassigned devices.
                if (device.assignedUser === device.defaultSystemUser) {
                    unassignedDevices.push(device);
                }

                device.displayName = getDeviceDisplayName(device);
                if (angular.isDefined(device.assignedUser) && angular.isDefined(userMap[device.assignedUser])) {
                    userMap[device.assignedUser].assignedToDevices.push(device);
                    if (isDeviceOnline(device) && angular.isObject(userMap[device.assignedUser])) {
                        userMap[device.assignedUser].isOnline = 1;
                    } else if (!angular.isObject(userMap[device.assignedUser])) {
                        logger.warn('User not found: ' + device.assignedUser + ' for device ', device);
                    }
                }
                if (angular.isDefined(device.operatingUser) && angular.isDefined(userMap[device.operatingUser])) {
                    userMap[device.operatingUser].operatingDevices.push(device);
                    if (isDeviceOnline(device) && angular.isObject(userMap[device.assignedUser])) {
                        userMap[device.assignedUser].isOnline = 1;
                    }
                }
            }
        });

        if (angular.isNumber(standardUserId) && angular.isObject(userMap[standardUserId])) {
            // set online state of standard user
            unassignedDevices.forEach((dev) => {
                if (isDeviceOnline(dev)) {
                    userMap[standardUserId].isOnline  = 1;
                }
            });

            userMap[standardUserId].assignedToDevices = unassignedDevices;
        }
    }

    function getDeviceDisplayName(device) {
        let hasName = angular.isDefined(device.name) && device.name !== '';
        let hasIpAddress = angular.isDefined(device.ipAddresses) && device.ipAddresses.length > 0;
        let hasVendor = angular.isDefined(device.vendor) && device.vendor !== '';
        if (hasName) {
            return device.name;
        }
        let address;
        if (hasIpAddress) {
            address = device.ipAddresses.join(', ');
        } else {
            address = device.hardwareAddress;
        }
        if (hasVendor) {
            return device.vendor + ' (' + address + ')';
        }
        return address;
    }

    function isDeviceOnline(device) {
        return device.isOnline;
    }

    function createUserWithProfile(userParam) {
        let newProfile;
        logger.debug('Get empty profile.');
        const emptyProfile = UserProfileService.getEmptyProfile();
        if (userParam.userRole === USER_ROLES.CHILD) {
            const age = angular.isNumber(userParam.birthday) ? LanguageService.calculateAge(userParam.birthday) : 0;
            newProfile = UserProfileService.getProfileWithParentalControlPresets(emptyProfile, age);
        } else {
            newProfile = emptyProfile;
        }

        let newUser = angular.copy(userParam);
        return saveNewUser(newUser).then(function success(user) {
            logger.debug('Saved user with ID ' + user.id);
            newProfile.name = 'PROFILE_FOR_USER_' + user.id;
            newUser = angular.copy(user);  // save, so we can update later in the chain
            return UserProfileService.saveNewProfile(newProfile);
        }).then(function success(profile) {
            logger.debug('Saved profile with ID ' + profile.id);
            newUser.associatedProfileId = profile.id;
            UserProfileService.invalidateCache();
            return updateUser(newUser);
        }).then(function success(response) {
            logger.debug('Updated user with ID ' + newUser.id + ' and associated profile ID ' +
                newUser.associatedProfileId);
            return response;
        }).catch(function error(response) {
            logger.error('Unable to save new user ', response);
            return response;
        });
    }

    function getEmptyUser() {
        return {
            id: undefined,
            system: false,
            name: '',
            nameKey: 'PARENTAL_CONTROL_USER_NAME',
            userRole: USER_ROLES.CHILD,
            associatedProfileId: 1
        };
    }

    return {
        saveNewUser: saveNewUser,
        createUserWithProfile: createUserWithProfile,
        getAll: getAll,
        getUserRoles: getUserRoles,
        updateUser: updateUser,
        updateUserDashboardView: updateUserDashboardView,
        updateDashboardViewOfAllDefaultSystemUsers: updateDashboardViewOfAllDefaultSystemUsers,
        deleteUser: deleteUser,
        savePin: savePin,
        resetPin: resetPin,
        uniqueName: uniqueName,
        invalidateCache: invalidateCache,
        processUserDeviceAssociations: processUserDeviceAssociations,
        getEmptyUser: getEmptyUser,
        deleteAllUsers: deleteAllUsers
    };
}
