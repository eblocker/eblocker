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
    templateUrl: 'app/dashboard.component.html',
    controllerAs: 'vm',
    controller: AppController,
    bindings: {
        productInfo: '<',
        locale: '<'
    }
};

function AppController($scope, $state, $stateParams, $location, $window, $document, $filter,  //jshint ignore: line
                       Idle, logger, security, CardService, DataService, APP_CONTEXT, UserService,
                       DeviceService, deviceDetector, EventService, RedirectService, settings, SystemService,
                       ResolutionService, DialogService, DeviceSelectorService,
                       NotificationService, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;

    let remoteWarningShown = false;

    vm.getTitleDevice = getTitleDevice;
    vm.getTitleUser = getTitleUser;
    vm.showTitleDevice = showTitleDevice;
    vm.showTitleUser = showTitleUser;
    vm.isInternetAccessLocked = isInternetAccessLocked;
    vm.isMainState = isMainState;
    vm.goToSettings = goToSettings;
    vm.getUserRole = getUserRole;
    vm.getNameKey = getNameKey;

    vm.showDashboardOverlay = showDashboardOverlay;
    vm.isDashboardPaused = isDashboardPaused;
    vm.isServerNotRunning = isServerNotRunning;
    vm.getCurrentStatus = getCurrentStatus;
    vm.adminLogin = adminLogin;

    function goToSettings() {
        RedirectService.toConsole(false);
    }

    function getFilteredCards() {
        return $filter('filter')(CardService.getAllCards(), function(filter) {
            return filter.displayGlobally;
        });
    }

    vm.getDeviceTooltip = function() {
        return 'eBlockerOS / ' + deviceDetector.os + ' / ' + deviceDetector.browser;
    };

    function getTitleDevice() {
        return angular.isObject(DeviceSelectorService.getSelectedDevice()) ?
            DeviceSelectorService.getSelectedDevice().name : '';
    }

    function getTitleUser() {
        const dev = DeviceSelectorService.getSelectedDevice();
        if (angular.isObject(dev)) {
            vm.thisOperatingUser = UserService.getUserById(dev.operatingUser);
            if (angular.isObject(vm.thisOperatingUser) && !vm.thisOperatingUser.system) {
                return vm.thisOperatingUser.name;
            } else if (angular.isObject(vm.thisOperatingUser) && vm.thisOperatingUser.id === 2) {
                return 'APP.TOOLBAR.USER_LOCKED';
            }
        }
        return  '';
    }

    function showTitleDevice() {
        return getTitleDevice() !== '';
    }

    function showTitleUser() {
        return isFamily() && getTitleUser() !== '' && (!vm.thisOperatingUser.system || vm.thisOperatingUser.id === 2);
    }

    function isFamily() {
        return angular.isObject(vm.productInfo) && vm.productInfo.productFeatures.indexOf('FAM') > -1;
    }

    function getUserRole() {
        return vm.thisOperatingUser.userRole;
    }

    function getNameKey() {
        return vm.thisOperatingUser.nameKey;
    }

    function isInternetAccessLocked() {
        return vm.thisOperatingUser.id === 2;
    }

    function isMainState() {
        return $state.includes('main');
    }

    function updateVisibility(param) {
        CardService.updateVisibility(param);
    }

    CardService.anchorScroll($stateParams.anchor);
    // remove query string from URL. looks better that way - issue: causes reload
    // $location.search('anchor', null);

    function showDashboardOverlay() {
        return isDashboardPaused() || isServerNotRunning();
    }

    function isDashboardPaused() {
        return !DataService.isRunning() && isMainState();
    }

    function isServerNotRunning() {
        const el = $document.find('dashboard-component')[0];
        if (angular.isObject(el)) {
            // make sure overflow: hidden is only set if isServerRunning is explicitly 'false'
            el.style.overflowY = SystemService.isServerRunning() === false ? 'hidden' : 'auto';
        }
        return SystemService.isServerRunning() === false;
    }

    function getCurrentStatus() {
        if (SystemService.getCurrentStatus() === 'SHUTTING_DOWN') {
            vm.isShutDown = true;
        }
        return SystemService.getCurrentStatus();
    }

    $scope.$on('IdleStart', function() {
        // the user appears to have gone idle
        logger.debug('IdleStart');
        if (DataService.isRunning()) {
            // We only need to send blur event (stop services) if services are running
            EventService.fireEvent('blur');
        }
        // allow user to reset idle-watcher and start services after timeout has expired by moving the mouse
        $window.addEventListener('mousemove', mouseMove);
        $window.addEventListener('touchstart', mouseMove); // so that touch scroll starts services
    });

    function mouseMove(e) {
        // watch() causes IdleEnd to be called. IdleEnd will then fire focus-event which starts the services again.
        $window.removeEventListener('mousemove', mouseMove);
        $window.removeEventListener('touchstart', mouseMove);
        Idle.watch();
    }

    $scope.$on('IdleEnd', function() {
        // the user has come back from AFK and is doing stuff.
        logger.debug('IdleEnd: ' + Idle.running());
        // restarts the services or stops blur-timer, if still running (prevents services from stopping)
        EventService.fireEvent('focus');
    });

    $scope.$on('Keepalive', function() {
        logger.warn('Keepalive');
        security.renewToken();
    });

    vm.$onDestroy = function() {
        SystemService.stopTokenWatcher();
    };

    function hideExpandAllOption() {
        return isOneColumn() || !CardService.isAtLeastOneCollapsed();
    }

    function hideCollapseAllOption() {
        return isOneColumn() || CardService.isAtLeastOneCollapsed();
    }

    function isOneColumn(){
        return ResolutionService.getScreenSize() === 'xs' ||
            ResolutionService.getScreenSize() === 'sm' ||
            ResolutionService.getScreenSize() === 'mdsm';
    }

    function adminLogin() {
        DialogService.adminLogin(function onOk(deviceId) {
            populateDevicesDropdown();
            DeviceSelectorService.goToDevice(deviceId);
        }, function onCancel(isLoggedIn) {
            // login was successful, but user did not select any device:
            if (isLoggedIn) {
                populateDevicesDropdown();
            }
        });
    }

    function adminLogout() {
        security.requestToken(APP_CONTEXT.name).then(function(response) {
            populateDevicesDropdown();
            DeviceSelectorService.goToLocalDevice();
        }, function(reason) {
            logger.warn('Could not log out admin', reason);
        });
    }

    const initialDeviceDropdownContent = [
        {
            label: 'APP.DEVICE_MENU.SELECT_OTHER',
            image: '/img/icons/ic_computer_black.svg',
            action: vm.adminLogin,
            hide: security.isLoggedInAsAdmin,
            closeOnClick: true
        },
        {
            label: 'APP.DEVICE_MENU.SELECT_LOCAL',
            image: '/img/icons/ic_star_rate_black.svg',
            action: DeviceSelectorService.goToLocalDevice,
            hide: DeviceSelectorService.isLocalDevice,
            closeOnClick: true
        },
        {
            label: 'SHARED.LOGOUT.LABEL',
            image: '/img/icons/ic_person_black.svg',
            action: adminLogout,
            hide: function() {
                return !security.isLoggedInAsAdmin();
            },
            closeOnClick: true
        }
    ];

    function populateDevicesDropdown() {
        DeviceSelectorService.getDevicesByName().then(function(response) {
            let entries = response.map(device => {
                let entry = {
                    label: device.name,
                    image: '/img/icons/ic_computer_black.svg',
                    action: function() {DeviceSelectorService.goToDevice(device.id);},
                    closeOnClick: true
                };
                return entry;
            });
            vm.deviceDropdownContent = initialDeviceDropdownContent.concat(entries);
        }, function(reason) {
            logger.error('Could not populate devices dropdown menu', reason);
        });
    }

    vm.$onInit = function() {

        SystemService.startTokenWatcher(security.requestToken, APP_CONTEXT.name);

        settings.setHeaderTitle(); // depends on $state

        vm.mainDropdownContent = [
            {
                label: 'APP.CONTENT.HELP.LABEL',
                image: '/img/icons/ic_help.svg',
                url: 'APP.CONTENT.HELP.URL',
                closeOnClick: true,
            },
            {
                label: 'APP.CONTENT.EXPAND.LABEL',
                image: '/img/icons/unfold_more.svg',
                hide: hideExpandAllOption,
                action: CardService.toggleExpandCollapseAll,
                closeOnClick: true,
            },
            {
                label: 'APP.CONTENT.COLLAPSE.LABEL',
                image: '/img/icons/unfold_less.svg',
                hide: hideCollapseAllOption,
                action: CardService.toggleExpandCollapseAll,
                closeOnClick: true,
            },
            {
                isList: true,
                getList: getFilteredCards,
                action: updateVisibility
            }
        ];

        vm.deviceDropdownContent = initialDeviceDropdownContent;

        populateDevicesDropdown();
        if (DeviceSelectorService.isRemoteDevice()) {
            DeviceSelectorService.goToDevice($stateParams.deviceId);
        }
    };

    function deviceSelectedHandler() {
        if (DeviceSelectorService.isRemoteDevice()) {
            showRemoteWarning();
            setRemoteBackground(true);
        } else {
            setRemoteBackground(false);
        }
    }

    function setRemoteBackground(isRemote) {
        const el = $document.find('dashboard-component')[0];
        if (angular.isObject(el)) {
            el.classList.remove('dashboard-remote-device');
            if (isRemote) {
                el.classList.add('dashboard-remote-device');
            }
        }
    }

    function showRemoteWarning() {
        if (! remoteWarningShown) {
            NotificationService.info('APP.REMOTE_WARNING.LABEL');
            remoteWarningShown = true;
        }
    }

    $scope.$on(EVENTS.DEVICE_SELECTED, deviceSelectedHandler);
}
