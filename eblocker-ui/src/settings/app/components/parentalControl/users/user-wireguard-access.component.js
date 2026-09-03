/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License.
 */

export default {
    templateUrl:
        'app/components/parentalControl/users/user-wireguard-access.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        user: '<'
    }
};

function Controller(
    $q,
    WireGuardService,
    UserService,
    NotificationService) {

    'ngInject';
    'use strict';

    const vm = this;

    vm.enabled = false;
    vm.isApplicable = false;
    vm.isLoading = false;
    vm.isUpdating = false;

    vm.setAccess = setAccess;
    vm.reload = reload;

    vm.$onInit = reload;

    vm.$onChanges = function(changes) {
        if (changes.user && !changes.user.isFirstChange()) {
            reload();
        }
    };

    function isRealUser(user) {
        return angular.isObject(user) &&
            angular.isNumber(user.id) &&
            user.system !== true;
    }

    function applyUser(user) {
        vm.isApplicable = isRealUser(user);

        if (!vm.isApplicable) {
            vm.enabled = false;
            return;
        }

        vm.enabled = user.wireGuardEnabled === true;
    }

    function reload() {
        applyUser(vm.user);

        if (!vm.isApplicable) {
            return $q.when(false);
        }

        vm.isLoading = true;

        return UserService.getAll(true)
            .then(function(response) {
                const users = angular.isArray(response.data) ?
                    response.data :
                    [];

                let current;

                users.some(function(user) {
                    if (user.id === vm.user.id) {
                        current = user;
                        return true;
                    }
                    return false;
                });

                if (!isRealUser(current)) {
                    vm.isApplicable = false;
                    vm.enabled = false;
                    return false;
                }

                vm.enabled = current.wireGuardEnabled === true;
                vm.user.wireGuardEnabled = vm.enabled;
                return vm.enabled;
            })
            .catch(function(response) {
                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD_USER_ACCESS.NOTIFICATION.LOAD_ERROR',
                    response
                );
                return vm.enabled;
            })
            .finally(function() {
                vm.isLoading = false;
            });
    }

    function setAccess() {
        if (!vm.isApplicable || vm.isUpdating) {
            return $q.when(vm.enabled);
        }

        const desired = vm.enabled === true;
        const previous = !desired;

        vm.isUpdating = true;

        return WireGuardService
            .setUserAuthorization(vm.user.id, desired)
            .then(function(response) {
                const persisted = response.data === true;

                vm.enabled = persisted;
                vm.user.wireGuardEnabled = persisted;
                UserService.invalidateCache();

                return persisted;
            })
            .catch(function(response) {
                vm.enabled = previous;
                vm.user.wireGuardEnabled = previous;

                NotificationService.error(
                    'ADMINCONSOLE.WIREGUARD_USER_ACCESS.NOTIFICATION.SAVE_ERROR',
                    response
                );

                return previous;
            })
            .finally(function() {
                vm.isUpdating = false;
            });
    }
}
