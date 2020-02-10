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
    templateUrl: 'app/cards/anon/anon.component.html',
    controllerAs: 'vm',
    controller: Controller,
    bindings: {
        cardId: '@'
    }
};

function Controller(logger, $interval, $timeout, $window, $translate, $q, CardService, TorService, VpnService, // jshint ignore: line
                    NotificationService, DialogService, CloakingService, DeviceService, registration, SslService,
                    ArrayUtilsService, DataService) {
    'ngInject';
    'use strict';

    const vm = this;

    const CARD_NAME = 'ANONYMIZATION'; //'card-13';

    vm.cancelVpnConnectionAttempt = cancelVpnConnectionAttempt;
    vm.toggleAnon = toggleAnon;
    vm.disconnectIpAnon = disconnectIpAnon;
    vm.getVpnNameByProfileId = getVpnNameByProfileId;
    vm.getNewTorIdentity = getNewTorIdentity;
    vm.goToTestPage = goToTestPage;
    vm.hasFeature = hasFeature;
    vm.isMobileDevice = isMobileDevice;
    vm.changeUserAgent = changeUserAgent;
    vm.updateCustomUserAgent = updateCustomUserAgent;
    vm.isCustomAgentValid = isCustomAgentValid;
    vm.disableCloaking = disableCloaking;

    let dataUpdateInterval;
    const INTERVAL = 2000;

    const torObject = {
        id: 'tor', label: 'Tor', name: 'SHARED.ANONYMIZATION.IP_ANON.LABEL_TOR'
    };

    function getSslStatus() {
        SslService.getSslStatus().then(function success(response) {
            vm.sslGloballyEnabled = response.data.globalSslStatus;
        }, function error(response) {
            logger.error('unable to get ssl state ', response);
        });
    }

    function getDevice() {
        return DeviceService.getDevice().then(function success(response) {
            vm.device = response.data; // use in UI
            return response.data;
        });
    }

    vm.$onInit = function() {
        vm.profiles = [];
        getDevice();
        updateData();
        startUpdateInterval();
        getUserAgentList();

        DataService.registerComponentAsServiceListener(CARD_NAME, 'TorService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'VpnService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'CloakingService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.registerComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$onDestroy = function() {
        stopUpdateInterval();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'TorService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'VpnService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'CloakingService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'SslService');
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'DeviceService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    function hasFeature(key) {
        return registration.hasProductKey(key);
    }

    function startUpdateInterval() {
        if (angular.isUndefined(dataUpdateInterval)) {
            dataUpdateInterval = $interval(updateData, INTERVAL);
        }
    }

    function stopUpdateInterval() {
        if (angular.isDefined(dataUpdateInterval)) {
            $interval.cancel(dataUpdateInterval);
            dataUpdateInterval = undefined;
        }
    }

    function updateData(reload) {
        $q.all([loadVpnProfiles(), loadVpnConfig(), loadTorConfig()]).then(function success() {
            // If we refactor the select box not to be disabled when a IP-Anon servie is connected:
            // we would need another check here, so that  the selectedProfile is not overridden when the user
            // tries to select another VPN or Tor: angular.isUndefined(vm.selectedProfile)
            if (angular.isObject(vm.vpnStatus) && vm.vpnStatus.active) {
                vm.selectedProfile = getVpnProfileById(vm.vpnStatus.profileId);
            }
            if (angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor) {
                vm.selectedProfile = torObject;
            }
        });
        getSslStatus();
        getDevice().then(function success(device) {
            getCloakedUserAgentByDeviceId(device);
        });
    }

    function loadVpnProfiles() {
        return VpnService.getVpnProfiles().then(function success(response) {

            vm.tmp = response.data.filter(function(profile) {
                return !profile.temporary && profile.enabled;
            });

            if (vm.tmp.length !== vm.profiles.length - 1) {
                vm.profiles = ArrayUtilsService.sortByProperty(vm.tmp, 'name');
                vm.profiles.unshift(torObject);
            }
        }, function error(response) {
            logger.error('Error loading VPN profiles', response);
        });
    }

    function loadTorConfig() {
        return TorService.getDeviceConfig().then(function success(response) {
            vm.torConfig = response.data;
            vm.isTorEnabled = vm.torConfig.sessionUseTor;
        }, function error(response) {
            logger.error('Error loading tor config', response);
        });
    }

    function loadVpnConfig() {
        return VpnService.getVpnStatusByDevice('me').then(function success(response) {
            if (response.status === 200) {
                vm.vpnStatus = response.data;
            } else if (response.status === 204) {
                // No content from server..
                vm.vpnStatus = undefined;
            }
        }, function error(response) {
            logger.error('Error loading VPN status', response);
        });
    }

    function getVpnNameByProfileId(profileId) {
        let name = 'unknown';
        if (angular.isArray(vm.profiles)) {
            vm.profiles.forEach((profile) => {
                if (profile.id === profileId) {
                    name = profile.name;
                }
            });
        }
        return name;
    }

    function getVpnProfileById(profileId) {
        let p;
        if (angular.isArray(vm.profiles)) {
            vm.profiles.forEach((profile) => {
                if (profile.id === profileId) {
                    p = profile;
                }
            });
        }
        return p;
    }

    function disconnectIpAnon() {
        disableTorIfActive();
        deactivateVpnIfEnabled();
        vm.selectedProfile = undefined;
    }

    function disableTorIfActive() {
        if (angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor) {
            saveTorConfig(false);
        }
    }

    /**
     * Activates Tor. If Tor is already active, deactivates Tor. If VPN is active
     * VPN is disabled before Tor is activated. Shows Warning dialog if required,
     * updates status flag for warnings.
     * @param event
     */
    function toggleTor(event) {
        if (angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor) {
            saveTorConfig(false);
        } else {
            TorService.getShowWarnings().then(function success(response) {
                let showWarnings = response.data;
                if (showWarnings) {
                    showTorActivationDialog(showWarnings, event).then(function success(showWarnings) {
                        deactivateVpnIfEnabled();
                        updateShowWarningsTorActivation(showWarnings);
                        saveTorConfig(true);
                    }, function error(showWarnings) {
                        vm.isTorEnabled = false;
                        vm.updatingTor = false;
                        updateShowWarningsTorActivation(showWarnings);
                    });
                } else {
                    deactivateVpnIfEnabled();
                    saveTorConfig(true);
                }
            }, function error(response) {
                vm.isTorEnabled = false;
                vm.updatingTor = false;
                logger.error('Unable to get show warnings for Tor dialog', response);
            });
        }
    }

    function deactivateVpnIfEnabled() {
        if (angular.isObject(vm.vpnStatus) && vm.vpnStatus.active) {
            vm.vpnIsDisconnecting = true;
            saveVpnStatus(vm.vpnStatus.profileId, false).finally(function done() {
                vm.vpnIsDisconnecting = false;
            });
        }
    }

    function toggleAnon(profile) {
        if (profile.id === 'tor') {
            toggleTor();
        } else {
            toggleVpn(profile);
        }
    }

    function toggleVpn(htmlProfile) {
        let vpnProfile = htmlProfile;

        let activate = !angular.isObject(vm.vpnStatus) || (vm.vpnStatus.profileId !== vpnProfile.id);
        if (activate && vm.torConfig.sessionUseTor) {
            // Deactivate Tor, if VPN activated
            saveTorConfig(false);
        }
        if (activate) {
            startVpnConnectionPoller(vpnProfile);
        } else {
            saveVpnStatus(vpnProfile.id, false);
        }
    }

    function saveTorConfig(activate) {
        vm.updatingTor = true;
        TorService.setDeviceConfig({sessionUseTor: activate}).then(function success() {
            if (activate) {
                vm.isTorEnabled = true;
                logger.info('Tor has been activated.');
            } else {
                vm.isTorEnabled = false;
                logger.info('Tor has been deactivated.');
            }
            vm.torConfig = vm.torConfig || {};
            vm.torConfig.sessionUseTor = activate;
        }).finally(function done() {
            vm.updatingTor = false;
        });
    }

    function saveVpnStatus(profileId, activate) {
        return VpnService.setVpnDeviceStatus(profileId, 'me', activate).then(function success() {
            if (activate) {
                logger.info('VPN has been connected.');
            } else {
                logger.info('VPN has been disconnected.');
                NotificationService.warning('SHARED.ANONYMIZATION.NOTIFICATION.CONNECTION_DOWN',
                    getVpnNameByProfileId(profileId));
            }
        });
    }

    function updateShowWarningsTorActivation(bool) {
        TorService.setShowWarnings(bool);
    }

    function getNewTorIdentity() {
        TorService.getNewTorIdentity().then(function success() {
            NotificationService.info('SHARED.ANONYMIZATION.NOTIFICATION.TOR_ID_RENEW_SUCCESS');
        }, function error(response) {
            NotificationService.error('SHARED.ANONYMIZATION.NOTIFICATION.TOR_ID_RENEW_ERROR', response);
        });
    }

    function goToTestPage() {
        const link = $translate.instant('SHARED.ANONYMIZATION.IP_ANON.TOR_CHECK_URL');
        $window.open(link, '_blank');
    }

    function showTorActivationDialog(showWarnings, event){
       return DialogService.showTorActivationDialog(showWarnings, event);
    }


    function startVpnConnectionPoller(profile) {
        vm.startingPoller = true;
        vm.vpnConnection = {
            poller: null,
            status: null,
            profile: profile,
            elapsed: 0,
            timeout: 60000,
            interval: 2000,
            isTimeout: function() {
                return this.elapsed >= this.timeout;
            }
        };

        VpnService.setVpnDeviceStatus(profile.id, 'me', true).then(function success() {
            vm.vpnConnection.poller = $interval(function() {
                vm.vpnConnection.elapsed += vm.vpnConnection.interval;
                if (vm.vpnConnection.isTimeout()) {
                    NotificationService.error('SHARED.ANONYMIZATION.NOTIFICATION.VPN_CONNECT_TIMEOUT');
                    cancelVpnConnectionPoller();
                    VpnService.setVpnDeviceStatus(profile.id, 'me', false);
                } else {
                    VpnService.getVpnStatus(profile.id).then(function success(response) {
                        let vpnStatus = response.data;
                        vm.vpnConnection.status = vpnStatus;
                        if (vpnStatus.up) {
                            cancelVpnConnectionPoller();
                        } else if (angular.isObject(vpnStatus.exitStatus)) {
                            NotificationService.error('SHARED.ANONYMIZATION.NOTIFICATION.VPN_CONNECT_ERROR');
                            cancelVpnConnectionPoller();
                        }
                    }, function error(response) {
                        logger.error('Unable to get VPN status ', response);
                    });
                }
            }, vm.vpnConnection.interval);
        }).finally(function done() {
            vm.startingPoller = false;
        });
    }

    function cancelVpnConnectionPoller() {
        if (angular.isObject(vm.vpnConnection.poller)) {
            logger.info('canceling poller ', vm.vpnConnection.poller);
            $interval.cancel(vm.vpnConnection.poller);
            vm.vpnConnection.poller = undefined;
        }
    }

    function cancelVpnConnectionAttempt(profile) {
        cancelVpnConnectionPoller();
        VpnService.setVpnDeviceStatus(profile.id, 'me', false);
    }

    function getUserAgentList() {
        CloakingService.getUserAgents().then(function success(response) {
            vm.userAgentList = [];
            response.data.forEach(function(each) {
                if (each !== 'Off') {
                    vm.userAgentList.push(each);
                }
            });
        });
    }

    function updateCustomUserAgent(device) {
        if (isCustomAgentValid(vm.customUserAgent)) {
            changeUserAgent(device);
        }
    }

    function disableCloaking() {
        vm.selectedUserAgent = 'Off';
        changeUserAgent(vm.device);
    }

    function changeUserAgent(device) {
        const userAgent = vm.selectedUserAgent;
        if (angular.isString(userAgent)) {
            CloakingService.setCloakedUserAgentByDeviceId({
                deviceId: device.id,
                userId: device.assignedUser,
                userAgentName: userAgent,
                userAgentValue: vm.customUserAgent
            }).then(function() {
                // Get the current user agent, so that the custom field is also updated
                // as soon as the selection changes. Custom field will hold the value
                // of the last selected user agent.
                getCloakedUserAgentByDeviceId(device);
            });
        }
    }

    function getCloakedUserAgentByDeviceId(device) {
        return CloakingService.getCloakedUserAgentByDeviceId(device.id, device.assignedUser).
        then(function success(response) {
            const agentName = response.data.agentName;
            if (vm.selectedUserAgent !== agentName &&
                angular.isDefined(agentName)) {
                vm.selectedUserAgent = agentName;
            }
            const customUserAgent = response.data.agentSpec;
            if (angular.isUndefined(vm.customUserAgent) && angular.isDefined(customUserAgent)) {
                vm.customUserAgent = customUserAgent;
            }
            return response;
        });
    }

    function isCustomAgentValid(str) {
        const pattern = /^.*\S+.*$/;
        return pattern.test(str);
    }

    function isMobileDevice(userAgentName) {
        return (userAgentName === 'iPad' || userAgentName === 'iPhone' || userAgentName === 'Android');
    }

}

