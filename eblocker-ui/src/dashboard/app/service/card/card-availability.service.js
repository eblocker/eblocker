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
export default function CardAvailabilityService($q, FILTER_TYPE, CARD_HTML, DeviceService, SslService,
                                                UserProfileService, PauseService, VpnHomeService,
                                                DeviceSelectorService) {
    'ngInject';

    let device, globalSslState, profile, pause, vpnHomeStatus;

    function getDevice() {
        return DeviceService.getDevice().then(function success(response) {
            device = response.data;
        });
    }

    function getSslState() {
        return SslService.getSslStatus().then(function success(response) {
            globalSslState = response.data.globalSslStatus;
            return response;
        });
    }

    function getUserProfile() {
        return UserProfileService.getCurrentUsersProfile().then(function success(response) {
            profile = response.data;
        });
    }

    function getPause() {
        return PauseService.getPause().then(function success(response) {
            pause = response.data;
        });
    }

    function getVpnHomeStatus() {
        return VpnHomeService.loadStatus().then(function success(response) {
            vpnHomeStatus = response.data;
        });
    }

    /**
     * Load all data that is required to check all cards for unavailability.
     * This function is called before isCardUnavailable(..), so that isCardUnavailable() uses
     * the current data. The get functions get the current data from the service, which should
     * keep the data up to date (by some $interval).
     */
    function updateData() {
        return $q.all([getDevice(),
            getSslState(),
            getUserProfile(),
            getPause(),
            getVpnHomeStatus()]);
    }

    function isInternetAccessLocked(device) {
        return device.operatingUser === 2;
    }


    function hasFeature(feature, productInfo) {
        return feature === 'WOL' ? true : angular.isDefined(productInfo) &&
            productInfo.productFeatures.indexOf(feature) > -1;
    }

    function isCard(name, str) {
        return CARD_HTML[name].indexOf(str) > -1;
    }

    function isCardAvailable(card, productInfo) { // jshint ignore: line
        if (!hasFeature(card.requiredFeature, productInfo)) {
            // if feature of card is 'higher' than customers product feature, card is not available
            return false;
        } else if (isCard(card.name, 'dashboard-whitelist-dns') ||
            isCard(card.name, 'dashboard-filter-statistics')) {
            return isDnsCardAvailable(card, device) || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-whitelist')) {
            return isPatternFilterCardAvailable(card, globalSslState, device) || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-ssl')) {
            return isSslCardAvailable() || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-online-time')) {
            return isOnlineTimeCardAvailable(card, device, profile) || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-pause')) {
            return isPauseCardAvailable(device) || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-mobile')) {
            return isMobileCardAvailable(card, vpnHomeStatus, device) || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-user')) {
            return isUserCardAvailable(device);
        } else if (isCard(card.name, 'dashboard-icon')) {
            return isIconCardAvailable(device, globalSslState) || isInternetAccessLocked(device);
        } else if (isCard(card.name, 'dashboard-frag-finn')) {
            return isFragFinnCardAvailable(profile);
        } else if (isCard(card.name, 'dashboard-connection-test')) {
            return isConnectionTestCardAvailable(device);
        } else if (isCard(card.name, 'dashboard-filter')) {
            // TODO this will be implemented in BE: Only Admin may change filter type
            // Admin will be able to allow users to see this card.
            return false;
        }
        return true;
    }

    function isPatternFilterCardAvailable(card, globalSslState, device) {
        return device.filterMode !== FILTER_TYPE.DNS &&
            (device.filterMode !== FILTER_TYPE.AUTOMATIC || (globalSslState && device.sslEnabled));
    }

    function isSslCardAvailable() {
        return globalSslState && DeviceSelectorService.isLocalDevice();
    }

    function isDnsCardAvailable(card, device) {
        return device.filterMode !== FILTER_TYPE.NONE;
    }

    function isOnlineTimeCardAvailable(card, device, profile) {
        return device.enabled && (profile.controlmodeTime || profile.controlmodeMaxUsage);
    }

    function isPauseCardAvailable(device) {
        // if device is not enabled, BUT paused, then the card is still available
        return (device.enabled || device.paused);
    }

    function isMobileCardAvailable(card, vpnHomeStatus, device) {
        return vpnHomeStatus.isRunning && device.mobileState && DeviceSelectorService.isLocalDevice();
    }

    function isUserCardAvailable(device) {
        return device.assignedUser !== device.defaultSystemUser;
    }

    function isIconCardAvailable(device, sslGlobal) {
        return !device.controlBarAutoMode || (device.sslEnabled && sslGlobal);
    }

    function isFragFinnCardAvailable(profile) {
        return profile.controlmodeUrls;
    }

    function isConnectionTestCardAvailable(device) {
        return device.enabled && DeviceSelectorService.isLocalDevice() && !device.paused;
    }

    /**
     * For now only for pause card:
     * Make sure that pause card is hidden, when pausing not allowed. But show pause card in menu, so that user
     * gets a hint, as to why the pause card is not showed (tooltip).
     * Can be used for other cards as well.
     * @param card
     */
    function onlyShowInDropdown(card) {
        if (CARD_HTML[card.name] === '<dashboard-pause></dashboard-pause>') {
            return angular.isDefined(pause) && !pause.pausingAllowed;
        }
        return false;
    }

    function getTooltipForDisableState(card) {
        if (CARD_HTML[card.name] === '<dashboard-pause></dashboard-pause>') {
            return angular.isDefined(pause) && !pause.pausingAllowed ? 'PAUSE.CARD.TOOLTIP_MENU' : undefined;
        }
    }

    return {
        getTooltipForDisableState: getTooltipForDisableState,
        isCardAvailable: isCardAvailable,
        onlyShowInDropdown: onlyShowInDropdown,
        updateData: updateData,
    };
}
