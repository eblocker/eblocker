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
export default {
    templateUrl: 'app/components/anon/ip-anon.component.html',
    controller: AnonController,
    controllerAs: 'ctrl'
};

function AnonController($scope, $interval, $mdDialog, $translate, $window, logger,
                        VpnService, TorService, ConsoleService, NotificationService) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.closeDropdown = {};

    vm.activeProfile = {
        base: 'CONTROLBAR.MENU.ANON.LABEL',
        isTor: false,
        isVpn: false
        // img: '/img/icons/anonymization.svg'
    };

    vm.isAnonActive = isAnonActive;
    vm.toggleTor = toggleTor;
    vm.toggleVpn = toggleVpn;
    vm.updateShowWarningsTorActivation = updateShowWarningsTorActivation;
    vm.saveTorConfig = saveTorConfig;
    vm.deactivateVpnIfEnabled = deactivateVpnIfEnabled;

    vm.htmlProfiles = [
        {
            label: 'CONTROLBAR.MENU.ANON.MENU.ACTIONS.START_TOR',
            imageUrl: '/img/icons/ic_security_black_outline.svg',
            // imageUrl: '/img/icons/ic_security.svg',
            isActive: isTorActive,
            actionCallback: toggleTor
        }
    ];

    vm.options = [
        {
            label: 'CONTROLBAR.MENU.ANON.MENU.ACTIONS.CHECK_CONNECTION',
            imageUrl: '/img/icons/ic_network_check.svg',
            actionCallback: checkConnection
        },
        {
            label: 'CONTROLBAR.MENU.ANON.MENU.ACTIONS.CONFIGURE_CONNECTION',
            imageUrl: '/img/icons/ic_settings.svg',
            actionCallback: configureConnection
        },
        {
            label: 'CONTROLBAR.MENU.ANON.MENU.ACTIONS.GET_NEW_TOR_ID',
            imageUrl: '/img/icons/ic_autorenew.svg',
            divider: true,
            show: isTorActive,
            actionCallback: getNewTorIdentity
        }
    ];

    function loadVpnProfiles() {
        VpnService.getVpnProfiles().then(function success(response) {
            vm.profiles = response.data.filter(function(profile) {
                return !profile.temporary && profile.enabled;
            });
            vm.profiles.forEach((profile) => {
                vm.htmlProfiles.unshift({
                    label: profile.name,
                    // imageUrl: '/img/icons/ic_vpn_lock.svg',
                    // imageUrl: '/img/icons/ic_security.svg',
                    imageUrl: '/img/icons/ic_security_black_outline.svg',
                    actionCallback: toggleVpn,
                    isActive: isVpnActive,
                    vpnProfile: profile
                });
            });
        }, function error(response) {
            logger.error('Error loading VPN profiles', response);
        });
    }

    function loadTorConfig() {
        TorService.getDeviceConfig().then(function success(response) {
            vm.torConfig = response.data;
        }, function error(response) {
            logger.error('Error loading tor config', response);
        }).finally(function setActiveProfile() {
            updateActiveProfile();
        });
    }

    function loadVpnConfig() {
        VpnService.getVpnStatusByDevice('me').then(function success(response) {
            if (response.status === 200) {
                vm.vpnStatus = response.data;
            } else if (response.status === 204) {
                // No content from server..

                if (angular.isObject(vm.vpnStatus)) {
                    // .. but local status is still connected
                    let usedProfile  = vm.profiles.filter(function(profile) {
                        return profile.id === vm.vpnStatus.profileId;
                    });
                    if (usedProfile.length >= 1) {
                        let profile = usedProfile[0];
                        NotificationService.warning('CONTROLBAR.MENU.ANON.NOTIFICATION.CONNECTION_DOWN', profile);
                    }
                }
                // reset status to match server status
                vm.vpnStatus = undefined;
            }
        }, function error(response) {

        }).finally(function setActiveProfile() {
            updateActiveProfile();
        });
    }

    function updateActiveProfile() {
        if (angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor) {
            vm.activeProfile.base = 'CONTROLBAR.MENU.ANON.LABEL';
            vm.activeProfile.delimiter = ':';
            vm.activeProfile.suffix = 'CONTROLBAR.MENU.ANON.MENU.LABEL_TOR';
            vm.activeProfile.isTor = true;
            vm.activeProfile.isVpn = false;
            // vm.activeProfile.img = '/img/icons/ic_security.svg';
        } else if (angular.isObject(vm.vpnStatus) && vm.vpnStatus.active) {
            vm.activeProfile.base = 'CONTROLBAR.MENU.ANON.LABEL';
            vm.activeProfile.delimiter = ':';
            vm.activeProfile.suffix = getVpnNameByProfileId(vm.vpnStatus.profileId);
            vm.activeProfile.isTor = false;
            vm.activeProfile.isVpn = true;
            // vm.activeProfile.img = '/img/icons/ic_security.svg';
        } else {
            vm.activeProfile.base = 'CONTROLBAR.MENU.ANON.LABEL';
            vm.activeProfile.delimiter = undefined;
            vm.activeProfile.suffix = undefined;
            vm.activeProfile.isTor = false;
            vm.activeProfile.isVpn = false;
            // vm.activeProfile.img = '/img/icons/ic_security_black_outline.svg';
        }
    }

    function getVpnNameByProfileId(profileId) {
        var name = 'unknown';
        if (angular.isArray(vm.profiles)) {
            vm.profiles.forEach((profile) => {
                if (profile.id === profileId) {
                    name = profile.name;
                }
            });
        }
        return name;
    }

    function isAnonActive() {
        // values vm.torConfig and vm.vpnStatus are constantly updated by interval
        vm.torEnabled = angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor;
        vm.vpnEnabled = angular.isObject(vm.vpnStatus) && vm.vpnStatus.active;
        return vm.torEnabled || vm.vpnEnabled;
    }

    function createAnonUpdateTimer() {
        let updateTor = $interval(loadTorConfig, 2000);
        let updateVpn = $interval(loadVpnConfig, 2000);
        $scope.$on('$destroy', function() {
            $interval.cancel(updateTor);
            $interval.cancel(updateVpn);
        });
    }

    function isTorActive() {
        return angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor;
    }

    function isVpnActive(htmlProfile) {
        if (!angular.isObject(htmlProfile) || !angular.isObject(htmlProfile.vpnProfile)) {
            return false;
        }
        let vpn = htmlProfile.vpnProfile;
        if (!angular.isObject(vm.vpnStatus) || vm.vpnStatus.profileId !== vpn.id || !vm.vpnStatus.active) {
            return false;
        }
        return vm.vpnStatus.up;
    }

    /**
     * Activates Tor. If Tor is already active, deactivates Tor. If VPN is active
     * VPN is disabled before Tor is activated. Shows Warning dialog if required,
     * updates status flag for warnings.
     * @param event
     */
    function toggleTor(event) {
        if (angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor) {
            vm.saveTorConfig(false);
        } else {
            TorService.getShowWarnings().then(function success(response) {
                let showWarnings = response.data;
                if (showWarnings) {
                    showTorActivationDialog(showWarnings, event).then(function success(showWarnings) {
                        vm.deactivateVpnIfEnabled();
                        vm.updateShowWarningsTorActivation(showWarnings);
                        vm.saveTorConfig(true);
                    }, vm.updateShowWarningsTorActivation);
                } else {
                    vm.deactivateVpnIfEnabled();
                    vm.saveTorConfig(true);
                }
            }, function error(response) {
                logger.error('Unable to get show warnings for Tor dialog', response);
            });
        }
        vm.closeDropdown.now();
    }

    function deactivateVpnIfEnabled() {
        if (angular.isObject(vm.vpnStatus) && vm.vpnStatus.active) {
            saveVpnStatus(vm.vpnStatus.profileId, false);
        }
    }

    function toggleVpn(htmlProfile) {
        let vpnProfile = htmlProfile.vpnProfile;

        let activate = !angular.isObject(vm.vpnStatus) || (vm.vpnStatus.profileId !== vpnProfile.id);
        if (activate && vm.torConfig.sessionUseTor) {
            // Deactivate Tor, if VPN activated
            saveTorConfig(false);
        }
        if (activate) {
            showVpnActivationDialog(vpnProfile);
        } else {
            saveVpnStatus(vpnProfile.id, false);
        }
        vm.closeDropdown.now();
    }

    function saveTorConfig(activate) {
        TorService.setDeviceConfig({sessionUseTor: activate}).then(function success() {
            if (activate) {
                logger.info('Tor has been activated.');
                // NotificationService.info('CONTROLBAR.MENU.ANON.NOTIFICATION.TOR_CONNECT');
            } else {
                logger.info('Tor has been deactivated.');
                // NotificationService.info('CONTROLBAR.MENU.ANON.NOTIFICATION.TOR_DISCONNECT');
            }
            vm.torConfig.sessionUseTor = activate;
        });
    }

    function saveVpnStatus(profileId, activate) {
       VpnService.setVpnDeviceStatus(profileId, 'me', activate).then(function success() {
           if (activate) {
               logger.info('VPN has been connected.');
               // NotificationService.info('CONTROLBAR.MENU.ANON.NOTIFICATION.VPN_CONNECT');
           } else {
               logger.info('VPN has been disconnected.');
               // NotificationService.info('CONTROLBAR.MENU.ANON.NOTIFICATION.VPN_DISCONNECT');
           }
       });
    }

    function updateShowWarningsTorActivation(bool) {
        TorService.setShowWarnings(bool);
    }

    function checkConnection() {
        // showTorVerifyConnectionDialog();
        const link = $translate.instant('SHARED.ANONYMIZATION.IP_ANON.TOR_CHECK_URL');
        $window.open(link, '_blank');
        vm.closeDropdown.now();
    }

    function configureConnection() {
        ConsoleService.goToConsoleAnon(false);
    }

    function getNewTorIdentity() {
        TorService.getNewTorIdentity().then(function success() {
            NotificationService.info('CONTROLBAR.MENU.ANON.NOTIFICATION.TOR_ID_RENEW_SUCCESS');
        }, function error(response) {
            NotificationService.error('CONTROLBAR.MENU.ANON.NOTIFICATION.TOR_ID_RENEW_ERROR', response);
        });
        vm.closeDropdown.now();
    }

    function showTorActivationDialog(showWarnings, event) {
        return $mdDialog.show({
            controller: 'TorActivationDialogController',
            controllerAs: 'ctrl',
            templateUrl: 'dialogs/tor/tor-activation.dialog.html',
            parent: angular.element(document.body),
            targetEvent: event,
            clickOutsideToClose: false,
            locals: {
                showWarnings: showWarnings
            }
        });
    }

    // function showTorVerifyConnectionDialog() {
    //     return $mdDialog.show({
    //         controller: 'TorVerifyConnectionDialogController',
    //         controllerAs: 'ctrl',
    //         templateUrl: 'app/dialogs/anon/tor-verify-connection.dialog.html',
    //         clickOutsideToClose: true
    //     });
    // }

    function showVpnActivationDialog(profile) {
        return $mdDialog.show({
            controller: 'VpnActivationDialogController',
            controllerAs: 'ctrl',
            templateUrl: 'app/dialogs/anon/vpn-activation.dialog.html',
            parent: angular.element(document.body),
            clickOutsideToClose: true,
            locals: {
                profile: profile
            }
        });
    }

    loadVpnProfiles();
    loadVpnConfig();
    loadTorConfig();
    createAnonUpdateTimer();

}
