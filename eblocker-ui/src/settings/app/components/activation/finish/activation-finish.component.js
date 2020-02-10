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
    templateUrl: 'app/components/activation/finish/activation-finish.component.html',
    controller: Controller,
    controllerAs: 'vm',
    bindings: {
        updatesStatus: '<'
    }
};

function Controller(logger, UpdateService, STATES, StateService, $stateParams, $interval, CustomerInfoService,
                    SetupService) {
    'ngInject';

    const vm = this;

    // vm.postRegistrationInformation = {
    //     'CANCEL': {
    //         'LABEL':'No, Thanks'
    //     },
    //     'TITLE': {
    //         'TEXT':'VPN Special Offer from Acme VPN'
    //     },
    //     'TEXT': {
    //         'P1': {
    //             'PRE': 'Take advantage from our VPN special offer:',
    //             'EM':'',
    //             'POST':''
    //         },
    //         'P2': {
    //             'PRE': 'Get ',
    //             'EM':'3 month free VPN',
    //             'POST':' from premium provider Acme VPN.'
    //         },
    //         'P3': {
    //             'PRE': 'No subscription, no obligations. VPN access will be terminated after 3 month. ',
    //             'EM':'Save money now',
    //             'POST':' and benefit from our special VPN partner prices afterwards.'
    //         }
    //     },
    //     'OK': {
    //         'LABEL': 'More Info',
    //         'LINK': 'https://www.eblocker.com/en/setup_wizard/acmevpn'
    //     }
    // };

    vm.nextStep = nextStep;
    vm.vpnRemindLater = vpnRemindLater;
    vm.doNotRemindAgain = doNotRemindAgain;
    vm.updateNow = updateNow;
    vm.finish = finish;
    vm.isCheckingForUpdates = true;

    vm.currentStep = 0;

    const NUM_CHECK_FOR_UPDATES = 4;
    const INTVERAL_CHECK_FOR_UPDATES = 2000;

    vm.$onDestroy = function() {
        stopPollingForUpdateStatus();
    };

    vm.$onInit = function() {
        if (angular.isObject($stateParams.param)) {
            vm.isReminder = $stateParams.param.isReminder === true;
            vm.postRegistrationInformation = $stateParams.param.postRegistrationInformation;
        }
        if (!vm.isReminder) {
            UpdateService.checkForUpdates().then(function success(response) {
                logger.debug('Successfully checked for updates: ', response.data);
                startPollingForUpdateStatus();
            }, function error(response) {
                logger.error('Unable to check for update status ', response);
                vm.isCheckingForUpdates = false;
            });
        }

        // ** make sure that setup wizard is automatically opened again, when license is revoked
        // --> setting this value here is only required, if the user revokes the license w/o reloading
        // the browser window between activating a license and revoking a license (very unusual case)
        SetupService.hasSetupBeenExecuted(false);
    };

    function getUpdateStatus() {
        UpdateService.getStatus().then(function success(response) {
            vm.numPolledForUpdateStatus++;
            vm.updatesStatus = response.data;
            logger.debug('Successfully received update-status. Updates available: ' +
                vm.updatesStatus.updatesAvailable + '. Still checking: ' + vm.updatesStatus.checking);
            vm.updatesAvailable = vm.updatesStatus.updatesAvailable;
            if ((vm.numPolledForUpdateStatus >= NUM_CHECK_FOR_UPDATES && !vm.updatesStatus.checking) ||
                vm.updatesAvailable) {
                vm.isCheckingForUpdates = false;
                stopPollingForUpdateStatus();
            }
        }, function error(response) {
            logger.error('Unable to check for update status ', response);
            vm.isCheckingForUpdates = false;
            stopPollingForUpdateStatus();
        });
    }

    let updateStatusInterval;
    function startPollingForUpdateStatus() {
        vm.numPolledForUpdateStatus = 0;
        updateStatusInterval = $interval(getUpdateStatus, INTVERAL_CHECK_FOR_UPDATES);
    }

    function stopPollingForUpdateStatus() {
        if (angular.isDefined(updateStatusInterval)) {
            $interval.cancel(updateStatusInterval);
        }
        updateStatusInterval = undefined;
    }

    function nextStep() {
        if (vm.currentStep + 1 === 1 && angular.isUndefined(vm.postRegistrationInformation)) {
            // skip the post registration card, there's no data to display
            vm.currentStep = 2;
        } else {
            vm.currentStep++;
        }
    }

    function vpnRemindLater() {
        if (vm.isReminder) {
            // do nothing, leave customer info on server
            finish();
        } else {
            CustomerInfoService.save({content: vm.postRegistrationInformation});
            nextStep();
        }
    }

    function doNotRemindAgain() {
        CustomerInfoService.remove();
        if (vm.isReminder) {
            finish();
        } else {
            nextStep();
        }
    }

    function updateNow() {
        UpdateService.setStatus({updating: true}).then(function success(response) {
            return UpdateService.getStatus();
        }).then(function success() {
            // go to update screen
            StateService.goToState(STATES.STAND_BY);
        }).catch(function error(response) {
            vm.updateFailReason = response.data;
            logger.error('Failed to update ', response);
        });
    }

    function finish() {
        StateService.goToState(STATES.AUTH);
    }
}
