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
    templateUrl: 'app/components/devices/list/devices-details.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        users: '<',
        vpnHomeStatus: '<',
        vpnHomeCertificates: '<'
    }
};

// jshint ignore: line
function Controller(logger, $stateParams, $window, $interval, $timeout, $q, $translate, // jshint ignore: line
                    StateService, STATES, ArrayUtilsService,
                    RegistrationService, SslService, DeviceService, CloakingService, NetworkService, DialogService,
                    VpnService, TorService, VpnHomeService, NotificationService, PauseService, ConsoleService,
                    deviceDetector) {
    'ngInject';
    'use strict';

    const vm = this;

    vm.descMaxLength = 2048;
    vm.maxLengthName = 50;
    vm.maxLengthDomain = 2048;

    vm.nameForm = {};
    vm.descriptionForm = {};
    vm.domainForm = {};

    vm.selectedIndex = 0;
    vm.editable = editable;
    vm.editName = editName;
    vm.hasFeature = hasFeature;
    vm.onChange = onChange;
    vm.onChangeOwner = onChangeOwner;
    // vm.onChangeFilter = onChangeFilter;
    vm.logoutOperatingUser = logoutOperatingUser;
    vm.isMobileDevice = isMobileDevice;
    vm.changeUserAgent = changeUserAgent;
    vm.updateCustomUserAgent = updateCustomUserAgent;
    vm.updateMessageSeverity = updateMessageSeverity;
    vm.onChangeHttps = onChangeHttps;

    vm.backState = STATES.DEVICES;
    vm.stateParams = $stateParams;

    vm.onChangeDevice = function (entry) {
        setupDevice(entry);
        resetPauseTimer();
    };

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param) &&
            angular.isObject($stateParams.param.entry) &&
            angular.isFunction($stateParams.param.getTableData)) {
            const device = $stateParams.param.entry;
            vm.tableData = $stateParams.param.getTableData();

            // init mobile state
            vm.tmpMobileState = device.mobileState;
            vm.operatingSystemType = getDeviceTypeObject(vm.osTypes, deviceDetector.os);

            setupDevice(device);
            updateAnonData();
            resetPauseTimer();
            startUpdateDevice();
            startUpdateAnon();
        } else {
            StateService.goToState(vm.backState);
        }
    };

    vm.$onDestroy = function() {
        stopPauseTimer();
        stopUpdateDevice();
        stopUpdateAnon();
    };

    let updateDeviceInterval;
    function startUpdateDevice() {
        if (angular.isDefined(updateDeviceInterval)) {
            return;
        }
        updateDeviceInterval = $interval(updateDevice, 4000);
    }

    function stopUpdateDevice() {
        if (angular.isDefined(updateDeviceInterval)) {
            $interval.cancel(updateDeviceInterval);
            updateDeviceInterval = undefined;
        }
    }

    let updateAnonInterval;
    function startUpdateAnon() {
        if (angular.isDefined(updateAnonInterval)) {
            return;
        }
        updateAnonInterval = $interval(updateAnonData, 4000);
    }

    function stopUpdateAnon() {
        if (angular.isDefined(updateAnonInterval)) {
            $interval.cancel(updateAnonInterval);
            updateAnonInterval = undefined;
        }
    }

    function updateDevice() {
        if (angular.isObject(vm.device) && angular.isString(vm.device.id) && !vm.device.isEblocker) {
            getById(vm.device.id);
        } else if (!vm.device.isEblocker) {
            logger.warn('Unable to update device ', vm.device);
        }
    }

    function getById(id) {
        return DeviceService.getById(id).then(function success(response) {
            setupDevice(response.data, true);
        });
    }

    function setupDevice(device, betterPerformance) {
        if (angular.isObject(device)) {
            vm.device = device;
            vm.device.pausedOrEnabled = vm.device.enabled || vm.device.paused;

            getCloakedUserAgentByDeviceId(vm.device);

            // ** update required, so that switch to a restricted user updates the pausing-allowed
            updatePauseStatus(true, vm.device.id);

            getSslStatus(true);

            if (!betterPerformance) {
                getDhcpStatus();
                getUserAgentList();
            }

            vm.device.hasCertificate = angular.isDefined(vm.vpnHomeCertificates) &&
                vm.vpnHomeCertificates.indexOf(vm.device.id) > -1;

            DeviceService.setDisplayValues(vm.device);

            // used in user-tab to show 'return' device option
            vm.users.forEach((user) => {
                if (user.id === vm.device.operatingUser) {
                    vm.device.operatingUserEntity = user;
                }
            });

            if (vm.device.messageShowInfo && vm.device.messageShowAlert) {
                vm.device.messageSeverity = 'ALL';
            } else if (vm.device.messageShowAlert) {
                vm.device.messageSeverity = 'ALERT_ONLY';
            } else {
                vm.device.messageSeverity = 'NONE';
            }

            vm.deviceName = {
                value: vm.device.name
            };
            vm.deviceIp = {
                value: vm.device.displayIpAddresses
            };
            vm.deviceMac = {
                value: vm.device.hardwareAddress
            };
            vm.deviceVendor = {
                value: getVendor(vm.device.vendor, vm.device.hardwareAddress)
            };
        }
    }

    function getVendor(vendor, hardwareAddress) {
        if (angular.isString(vendor) && vendor.length > 0) {
            return vendor;
        }
        // private MAC address (locally administered bit set)?
        if ('26ae'.includes(hardwareAddress[1])) {
            vendor = $translate.instant('ADMINCONSOLE.DEVICES_LIST.DETAILS.GENERAL.LABEL_VENDOR_PRIVATE_MAC');
        }
        return vendor;
    }

    function resetPauseTimer() {
        // stop if running.
        stopPauseTimer();
        // loads the pause and start keeps countdown, if pause is active.
        startPauseTimer();
    }

    function editable(device) {
        return angular.isObject(device) && !device.isEblocker;
    }

    function editName(event, entry) {
        if (editable(entry)) {

            const msgKeys = {
                label: 'ADMINCONSOLE.DEVICES_LIST.DETAILS.GENERAL.LABEL_NAME'
            };
            const subject = {value: entry.name, id: entry.id};

            // event, subject, msgKeys, okAction, isUnique
            DialogService.
            openEditDialog(event, subject, msgKeys , editNameActionOk, undefined, vm.maxLengthName, 1).
            then(function success(subject) {
                // ** we need to update the label's value as well
                vm.deviceName.value = subject.value;
            });
        }
    }

    function editNameActionOk(name) {
        vm.device.name = name;
        return onChange(vm.device);
    }

    function hasFeature(key) {
        return RegistrationService.hasProductKey(key);
    }

    function getSslStatus(reload) {
        SslService.getStatus(reload).then(function success(response) {
            vm.sslGloballyEnabled = response.data;
        }, function error(response) {
            logger.error('unable to get ssl state ', response);
        });
    }

    function getDhcpStatus() {
        NetworkService.getDhcpState().then(function success(response) {
            vm.dhcpActive = response.data;
        }, function error(response) {
            logger.error('unable to get dhcp state ', response);
        });
    }

    function logoutOperatingUser(device) {
        device.operatingUser = device.assignedUser;
        device.operatingUserEntity = device.assignedUserEntity;
        onChange(device);
    }

    function onChangeOwner(device) {
        device.operatingUser = device.assignedUser;
        onChange(device);
    }

    function updateMessageSeverity(device) {
        if (device.messageSeverity === 'NONE') {
            device.messageShowInfo = false;
            device.messageShowAlert = false;
        } else if (device.messageSeverity === 'ALERT_ONLY') {
            device.messageShowInfo = false;
            device.messageShowAlert = true;
        } else if (device.messageSeverity === 'ALL') {
            device.messageShowInfo = true;
            device.messageShowAlert = true;
        }
        vm.onChange(device);
    }

    vm.onChangeEnabled = function(device) {
        vm.isUpdatingDevice = true; // set to true before onChange is called, to avoid double click on switch
        device.enabled = device.pausedOrEnabled;
        if (device.paused) {
            PauseService.setPause(0, vm.device.id).then(function success() {
                stopPauseTimer();
                onChange(device);
            });
        } else {
            onChange(device);
        }
        DeviceService.invalidateCache();
    };

    vm.onResetDevice = function(device) {
        DialogService.resetDeviceConfirm(undefined, 
            function(){
                return DeviceService.resetDevice(device).then(function success(response){
                    setupDevice(response.data, false);
                }, function error(response) {
                    // Nothing to be done
                });
            }, 
            angular.noop);
    };

    vm.onChangePaused = function(device) {
        vm.pauseStatusPending = true;
        if (device.paused) {
            PauseService.setPause(300, vm.device.id).then(function success() {
                startPauseTimer();
            }).finally(function done() {
                vm.pauseStatusPending = false;
            });
        } else {
            PauseService.setPause(0, vm.device.id).then(function success() {
                stopPauseTimer();
            }).finally(function done() {
                vm.pauseStatusPending = false;
            });
        }
    };

    vm.getPauseDisplayValue = function() {
        return getPauseMinutes() + ':' + (getPauseSeconds() < 10 ? '0' : '') + getPauseSeconds();
    };

    vm.getPauseMinutes = getPauseMinutes;
    function getPauseMinutes() {
        if (angular.isNumber(vm.pauseRemaining)) {
            return Math.floor((vm.pauseRemaining / 60));
        }
    }

    vm.getPauseSeconds = getPauseSeconds;
    function getPauseSeconds() {
        if (angular.isNumber(vm.pauseRemaining)) {
            return (vm.pauseRemaining % 60);
        }
    }

    function countDownPause() {
        if (angular.isNumber(vm.pauseRemaining)) {
            vm.pauseRemaining--;
        }
        if (angular.isUndefined(vm.pauseRemaining) || vm.pauseRemaining % 10 === 0) {
            updatePauseStatus(true, vm.device.id);
        }
    }

    function updatePauseStatus(reload, deviceId) {
        PauseService.getPause(reload, deviceId).then(function success(pause) {
            vm.pauseRemaining = pause.pausing;
            vm.pausingAllowed = pause.pausingAllowed;
            if (vm.pauseRemaining === 0) {
                stopPauseTimer();
            } else if (vm.pauseRemaining > 0) {
                vm.device.paused = true;
            }
        });
    }

    let countdownTimer;
    function startPauseTimer() {
        if (angular.isDefined(countdownTimer)) {
            return;
        }
        countdownTimer = $interval(countDownPause, 1000);
    }

    function stopPauseTimer() {
        if (angular.isDefined(countdownTimer)) {
            $interval.cancel(countdownTimer);
            countdownTimer = undefined;
        }
        if (angular.isDefined(vm.device)) {
            vm.device.paused = false;
        }
        vm.pauseRemaining = undefined;
    }

    function onChangeHttps(device) {
        const msgKeys = {
            title: 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.TITLE',
            okButton: 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.ACTION.OK'
        };
        let showDialog = false;
        if (device.filterMode === 'AUTOMATIC') {
            showDialog = true;
            msgKeys.text = 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.TEXT_IS_AUTO';
        } else if (device.filterMode === 'ADVANCED') {
            showDialog = true;
            msgKeys.text = 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.TEXT_IS_PATTERN';
        }

        if (!device.controlBarAutoMode && device.iconMode !== 'OFF') {
            showDialog = true;
            msgKeys.additionalText = 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.TEXT_CONTROLBAR_ON';
        } else if (device.controlBarAutoMode) {
            showDialog = true;
            msgKeys.additionalText = 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.TEXT_CONTROLBAR_AUTO';
        }

        if (vm.selectedUserAgent !== 'Off') {
            showDialog = true;
            msgKeys.moreAdditionalText = 'ADMINCONSOLE.DIALOG.SSL_DEVICE_DISABLE_WARNING.TEXT_CLOAKING_ON';
        }

        if (showDialog && !device.sslEnabled) {
            DialogService.sslDisableWarning(undefined, undefined, device, msgKeys);
        }
        onChange(device);
    }

    function onChange(device) {

        vm.isUpdatingDevice = true;
        vm.spinnerDelay = true;

        // Icon display mode
        if (!device.displayIconOn) {
            device.iconMode = 'OFF';
        } else if (device.displayIconFiveSeconds){
            device.iconMode = (device.displayIconBrowserOnly ? 'FIVE_SECONDS_BROWSER_ONLY' : 'FIVE_SECONDS');
        } else {
            device.iconMode = (device.displayIconBrowserOnly ? 'ON' : 'ON_ALL_DEVICES');
        }

        return DeviceService.update(device.id, device).then(function success(response) {
            setupDevice(response.data, true);
        }).finally(function done() {
            vm.isUpdatingDevice = false;
            $timeout(disableDeviceUpdateSpinner, 500); // show spinner at least one second
            // deactivate all spinners (for message checkboxes)
        });
    }

    function disableDeviceUpdateSpinner() {
        vm.spinnerDelay = false;
    }


    // ** IP-Anon
    vm.cancelVpnConnectionAttempt = cancelVpnConnectionAttempt;
    vm.toggleAnon = toggleAnon;
    vm.disconnectIpAnon = disconnectIpAnon;
    vm.getVpnNameByProfileId = getVpnNameByProfileId;
    vm.getNewTorIdentity = getNewTorIdentity;
    vm.changeUserAgent = changeUserAgent;
    vm.updateCustomUserAgent = updateCustomUserAgent;
    vm.isCustomAgentValid = isCustomAgentValid;
    vm.disableCloaking = disableCloaking;
    vm.profiles = [];

    const torObject = {
        id: 'tor', label: 'Tor', name: 'SHARED.ANONYMIZATION.IP_ANON.LABEL_TOR'
    };

    function updateAnonData() {
        if (!vm.device.isEblocker && !vm.device.isGateway) {
            $q.all([loadVpnProfiles(), loadVpnConfig(vm.device.id), loadTorConfig()]).then(function success() {
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
        }
    }

    function loadVpnProfiles() {
        return VpnService.getProfiles().then(function success(response) {

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
        return TorService.getDeviceConfig(vm.device.id).then(function success(response) {
            vm.torConfig = response.data;
            vm.isTorEnabled = vm.torConfig.sessionUseTor;
        }, function error(response) {
            logger.error('Error loading tor config', response);
        });
    }

    function loadVpnConfig(deviceId) {
        return VpnService.getVpnStatusByDeviceId(deviceId).then(function success(response) {
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
        if (!vm.useAnonymizationService && vm.isTorEnabled && angular.isObject(vm.selectedProfile)) {
            return vm.selectedProfile.name;
        }
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
        let torDialogResult = false;
        if (angular.isObject(vm.torConfig) && vm.torConfig.sessionUseTor) {
            saveTorConfig(false);
        } else if (vm.device.areDeviceMessagesSettingsDefault) {
            showTorActivationDialog(true, event).then(function success(showWarnings) {
                deactivateVpnIfEnabled();
                torDialogResult = true;
                return updateShowWarningsTorActivation(showWarnings);
            }, function error(showWarnings) {
                vm.isTorEnabled = false;
                torDialogResult = false;
                return updateShowWarningsTorActivation(showWarnings);
            }).then(function success() {
                // wait until updateShowWarningsTorActivation (save device) is done, before saving Tor config.
                // --> race condition
                saveTorConfig(torDialogResult);
            });
        } else {
            deactivateVpnIfEnabled();
            saveTorConfig(true);
        }
    }

    function showTorActivationDialog(showWarnings, event){
        return DialogService.showTorActivationDialog(showWarnings, event);
    }

    function deactivateVpnIfEnabled() {
        if (angular.isObject(vm.vpnStatus) && vm.vpnStatus.active) {
            vm.vpnIsDisconnecting = true;
            const profile = getVpnProfileById(vm.vpnStatus.profileId);
            saveVpnStatus(profile, false).finally(function done() {
                vm.vpnIsDisconnecting = false;
            });
        }
    }

    function toggleAnon(profile) {
        if (profile.id === 'tor') {
            logger.debug('Toggle Tor enabled ' + vm.isTorEnabled );
            toggleTor();
        } else {
            logger.debug('Toggle VPN ' + profile.name );
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
            startVpnConnectionPoller(vpnProfile, vm.device.id);
        } else {
            saveVpnStatus(vpnProfile, false);
        }
    }

    function saveTorConfig(activate) {
        vm.updatingTor = true;
        TorService.setDeviceConfig({sessionUseTor: activate}, vm.device.id).then(function success() {
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

    function saveVpnStatus(profile, activate) {
        return VpnService.setVpnDeviceStatus(profile, vm.device.id, activate).then(function success() {
            if (activate) {
                logger.info('VPN has been connected.');
            } else {
                logger.info('VPN has been disconnected.');
                NotificationService.warning('SHARED.ANONYMIZATION.NOTIFICATION.CONNECTION_DOWN',
                    getVpnNameByProfileId(profile.id));
            }
        });
    }

    function updateShowWarningsTorActivation(bool) {
        vm.device.areDeviceMessagesSettingsDefault = bool;
        return onChange(vm.device);
    }

    function getNewTorIdentity() {
        TorService.getNewTorIdentity().then(function success() {
            NotificationService.info('SHARED.ANONYMIZATION.NOTIFICATION.TOR_ID_RENEW_SUCCESS');
        }, function error(response) {
            NotificationService.error('SHARED.ANONYMIZATION.NOTIFICATION.TOR_ID_RENEW_ERROR', response);
        });
    }

    function startVpnConnectionPoller(profile, deviceId) {
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

        VpnService.setVpnDeviceStatus(profile, deviceId, true).then(function success() {
            vm.vpnConnection.poller = $interval(function() {
                vm.vpnConnection.elapsed += vm.vpnConnection.interval;
                if (vm.vpnConnection.isTimeout()) {
                    NotificationService.error('SHARED.ANONYMIZATION.NOTIFICATION.VPN_CONNECT_TIMEOUT');
                    cancelVpnConnectionPoller();
                    VpnService.setVpnDeviceStatus(profile, deviceId, false);
                } else {
                    VpnService.getVpnStatus(profile).then(function success(response) {
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
            logger.info('cancelling poller ', vm.vpnConnection.poller);
            $interval.cancel(vm.vpnConnection.poller);
            vm.vpnConnection.poller = undefined;
        }
    }

    function cancelVpnConnectionAttempt(profile) {
        cancelVpnConnectionPoller();
        VpnService.setVpnDeviceStatus(profile, vm.device.id, false);
    }

    function disableCloaking() {
        vm.selectedUserAgent = 'Off';
        changeUserAgent(vm.device);
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

    function isMobileDevice(userAgentName) {
        return (userAgentName === 'iPad' || userAgentName === 'iPhone' || userAgentName === 'Android');
    }

    function updateCustomUserAgent(device) {
        device.isCustomUaInvalid = !isCustomAgentValid(device.customUserAgent);
        if (!device.isCustomUaInvalid) {
            changeUserAgent(device);
        }
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

    vm.downloadClientConf = downloadClientConf;
    vm.goToDashboard = goToDashboard;
    vm.onChangeMobile = onChangeMobile;
    vm.onChangeMobilePrivateNetworkAccess = onChangeMobilePrivateNetworkAccess;
    vm.isMobileConfigDownloadDisabled = isMobileConfigDownloadDisabled;


    function isMobileConfigDownloadDisabled() {
        return !vm.tmpMobileState ||
            vm.vpnHomeStatus.isFirstStart ||
            vm.isEnablingDevice ||
            vm.isDisablingDevice ||
            vm.isDownloadingConf ||
            !angular.isObject(vm.operatingSystemType) ||
            !angular.isString(vm.operatingSystemType.type);
    }

    // type equals enum on server
    // name equals string from deviceDetector (except "other")
    // value is the String visible in UI
    vm.osTypes = [
        {type: 'WINDOWS', name: 'windows', value: 'Windows'},
        {type: 'MAC', name: 'mac', value: 'MacOS'},
        {type: 'IOS', name: 'ios', value: 'iOS'},
        {type: 'ANDROID', name: 'android', value: 'Android'},
        {type: 'OTHER', name: 'other', value: 'SHARED.MOBILE.DEVICE_TYPE.OTHER'}
    ];

    function getDeviceTypeObject(types, type) {
        let ret = vm.osTypes[4];
        types.forEach((item) => {
            if (item.name === type) {
                ret = item;
            }
        });
        return ret;
    }

    function onChangeMobile(event, device) {
        if (vm.tmpMobileState === false) {
            DialogService.revokeCertificateInfo(event, onChangeDisable, cancel, device);
        } else {
            DialogService.addCertificateInfo(event, onChangeMobileEnable, device);
        }
    }

    function cancel() {
        vm.tmpMobileState = vm.device.mobileState;
    }

    function onChangeMobileEnable(device){
        return enableDeviceForMobile(device).then(function success() {
            device.mobileState = vm.tmpMobileState;
            onChange(device).finally(function synch() {
                vm.tmpMobileState = vm.device.mobileState; // use state of vm.device, which has been updated in onChange
            });
        });
    }

    function onChangeDisable(device){
        return disableDevice(device).then(function success() {
            device.mobileState = vm.tmpMobileState;
            onChange(device).finally(function synch() {
                vm.tmpMobileState = vm.device.mobileState; // use state of vm.device, which has been updated in onChange
            });
        });
    }

    function goToDashboard() {
        ConsoleService.goToDashboard(false, 'MOBILE_CARD');
    }

    // ** eBlocker Mobile

    function enableDeviceForMobile(device) {
        if (vm.vpnHomeStatus.isFirstStart || vm.isEnablingDevice) {
            let deferred = $q.defer();
            deferred.resolve('First start or disabling .. ');
            return deferred.promise;
        }

        vm.isEnablingDevice = true;
        return VpnHomeService.enableDevice(device.id).then(function success() {
            NotificationService.info('ADMINCONSOLE.VPN_HOME.NOTIFICATION.DEVICE_ENABLED');
        }).finally(function done() {
            vm.isEnablingDevice = false;
        });
    }

    function disableDevice(device) {
        if (vm.vpnHomeStatus.isFirstStart || vm.isDisablingDevice) {
            let deferred = $q.defer();
            deferred.resolve('First start or disabling .. ');
            return deferred.promise;
        }
        vm.isDisablingDevice = true;
        return VpnHomeService.disableDevice(device.id).then(function success() {
            NotificationService.info('ADMINCONSOLE.VPN_HOME.NOTIFICATION.DEVICE_DISABLED');
        }).finally(function done() {
            vm.isDisablingDevice = false;
        });
    }

    function onChangeMobilePrivateNetworkAccess(device) {
        return VpnHomeService.setPrivateNetworkAccess(device.id, device.mobilePrivateNetworkAccess)
            .then(function (response) {
                device.mobilePrivateNetworkAccess = response.data;
            });
    }

    function downloadClientConf(device) {
        if (!angular.isString(vm.vpnHomeStatus.host) || vm.vpnHomeStatus.host === '') {
            NotificationService.error('ADMINCONSOLE.VPN_HOME.NOTIFICATION.HOST_MISSING');
        } else {
            vm.isDownloadingConf = true;
            VpnHomeService.generateDownloadUrl(device.id, vm.operatingSystemType.type).then(function success(response) {
                // Sort certificates into dic
                $window.location = response.data;
            }, function error(response) {
                // fail
            }).finally(function done() {
                vm.isDownloadingConf = false;
            });
        }
    }
}
