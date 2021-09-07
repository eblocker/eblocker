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
    templateUrl: 'app/cards/pause/pause.component.html',
    controller: PauseController,
    controllerAs: 'vm',
    bindings: {
        cardId: '@'
    }
};

function PauseController($scope, logger, $interval, $timeout, $filter, PauseService, CardService, DataService,
                         DeviceSelectorService, EVENTS) {
    'ngInject';
    'use strict';

    const vm = this;
    const CARD_NAME = 'PAUSE'; // 'card-1';
    const PAUSE_INTERVAL = 300;

    let countdownTimer;
    let remainingPauseInSeconds = 0;

    vm.pausingAllowed = false;

    vm.paused = paused;
    vm.startPause = startPause;
    vm.stopPause = stopPause;
    vm.getPausingAsDuration = getPausingAsDuration;
    vm.getTitleTranslateString = getTitleTranslateString;
    vm.minusFive = minusFive;
    vm.plusFive = plusFive;

    vm.$onInit = function() {
        getPause(true);
        DataService.registerComponentAsServiceListener(CARD_NAME, 'PauseService');
    };

    vm.$postLink = function() {
        $timeout(function() {
            CardService.scrollToCard(CARD_NAME);
        }, 300);
    };

    vm.$onDestroy = function() {
        stopCountdownTimer();
        DataService.unregisterComponentAsServiceListener(CARD_NAME, 'PauseService');
    };

    $scope.$on(EVENTS.DEVICE_SELECTED, function() {
        stopCountdownTimer();
        getPause(true);
    });

    function getPausingAsDuration() {
        return $filter('duration')(remainingPauseInSeconds);
    }

    function getTitleTranslateString() {
        return remainingPauseInSeconds > 0 ? 'PAUSE.CARD.TITLE_COLLAPSED_PAUSED' : 'PAUSE.CARD.TITLE';
    }

    function setRemaingPauseInSeconds(seconds) {
        // adjust remainingPauseInSeconds to value from server, in case countdown in UI differs from countdown in server
        // may cause slight jitter in UI display of countdown (but only every 10 seconds)
        remainingPauseInSeconds = Math.max(seconds, 0);
    }

    function startPause() {
        setPause(PAUSE_INTERVAL);
        startCountdownTimer();
        logger.info('Pause started');
        onDeviceStateUpdate();
    }

    function minusFive() {
        if (remainingPauseInSeconds - PAUSE_INTERVAL > 0) {
            setPause(remainingPauseInSeconds - PAUSE_INTERVAL);
        } else {
            stopPause();
        }
    }

    function plusFive() {
        setPause(remainingPauseInSeconds + PAUSE_INTERVAL);
    }

    function stopPause() {
        setPause(0);
        stopCountdownTimer();
        logger.info('Pause stopped');
        onDeviceStateUpdate();
    }

    function paused() {
        return remainingPauseInSeconds > 0;
    }

    function setPause(seconds) {
        vm.pauseIsPending = true;
        PauseService.setPause(seconds)
            .then(function success(response) {
                setRemaingPauseInSeconds(response.pausing);
            }, function error(response) {
                logger.error('Could not set pause: ' + JSON.stringify(response));
            }).finally(function done() {
                vm.pauseIsPending = false;
            });
    }

    function getPause(reload) {
        PauseService.getPause(reload).then(function(response) {
                setRemaingPauseInSeconds(response.data.pausing);
                vm.pausingAllowed = response.data.pausingAllowed;
                if (paused()) {
                    startCountdownTimer();
                } else {
                    stopCountdownTimer();
                }
            }, function error(response) {
                logger.error('Could not get pause: ' + JSON.stringify(response));
            });
    }

    function startCountdownTimer() {
        // already running?
        if (angular.isDefined(countdownTimer)) {
            return;
        }
        countdownTimer = $interval(updatePausing, 1000);
    }

    function stopCountdownTimer() {
        if (angular.isDefined(countdownTimer)) {
            $interval.cancel(countdownTimer);
            countdownTimer = undefined;
        }
    }

    // called by countdown timer
    function updatePausing(countdownTimerUpdates) {
        if (remainingPauseInSeconds > 0) {
            remainingPauseInSeconds--;
        }

        if (remainingPauseInSeconds <= 0) { // pause ended?
            stopCountdownTimer();
            logger.info('Pause ended');
            $timeout(onDeviceStateUpdate, 2000); // allow some time for the backend to end the pause
            return;
        }

        // every ten seconds, synchronize pause with the backend
        if (Math.floor(countdownTimerUpdates % 10) === 0) {
            logger.info('Syncing pause with backend');
            getPause(true);
        }
    }

    // tell other components that the device's enabled/disabled state has changed
    function onDeviceStateUpdate() {
        DeviceSelectorService.onDeviceUpdate();
    }
}
