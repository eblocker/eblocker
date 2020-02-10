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

function PauseController(logger, $interval, $timeout, $filter, PauseService, CardService, DataService) {
    'ngInject';
    'use strict';

    const vm = this;
    const CARD_NAME = 'PAUSE'; // 'card-1';
    const PAUSE_THRESHOLD = 36000; // 36000 = 10 hours
    const PAUSE_INTERVAL = 300;

    let countdownTimer;
    let countdownTimerUpdates = 0;
    let pausedTime = 0; // actual pause remaining in seconds

    vm.error = true;
    vm.pausingAllowed = false;

    vm.paused = paused;
    vm.startPause = startPause;
    vm.stopPause = stopPause;
    vm.getPausingAsDuration = getPausingAsDuration;
    vm.getTitleTranslateString = getTitleTranslateString;
    vm.minusFive = minusFive;
    vm.plusFive = plusFive;
    vm.isPauseThreshold = isPauseThreshold;

    vm.$onInit = function() {
        // Load pause status after some time, because loading too fast may miss
        // the correct pause state.
        $timeout(getPause, 500);
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

    function isPauseThreshold() {
        return getPausing() > PAUSE_THRESHOLD;
    }

    function getPausingAsDuration() {
        return $filter('duration')(pausedTime);
    }

    function getTitleTranslateString() {
        return pausedTime > 0 ? 'PAUSE.CARD.TITLE_COLLAPSED_PAUSED' : 'PAUSE.CARD.TITLE';
    }

    // remaining seconds to pause
    function getPausing() {
        return pausedTime;
    }

    function setPausing(pausing) {
        // adjust pausedTime to value from server, in case countdown in UI differs from countdown in server
        // may cause slight jitter in UI display of countdown (but only every 10 seconds)
        pausedTime = Math.max(pausing, 0);
    }

    function startPause() {
        setPause(PAUSE_INTERVAL);
        startCountdownTimer();
    }

    function minusFive() {
        if (getPausing() - PAUSE_INTERVAL > 0) {
            setPause(getPausing() - PAUSE_INTERVAL);
        } else {
            stopPause();
        }
    }

    function plusFive() {
        let pause = getPausing() + PAUSE_INTERVAL;
        if (pause > PAUSE_THRESHOLD) {
            pause = PAUSE_THRESHOLD;
        }
        setPause(pause);
    }

    function stopPause() {
        setPause(0);
        stopCountdownTimer();
    }

    function paused() {
        return getPausing() > 0;
    }

    function setPause(seconds) {
        vm.pauseIsPending = true;
        PauseService.setPause(seconds)
            .then(function success(response) {
                setPausing(response.pausing);
            }, function error(response) {
                logger.error('Could not set pause: ' + JSON.stringify(response));
            }).finally(function done() {
            vm.pauseIsPending = false;
        });
    }

    function getPause(reload) {
        PauseService.getPause(reload).then(function(response) {
                setPausing(response.data.pausing);
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
        countdownTimerUpdates = 0;
    }

    function stopCountdownTimer() {
        if (angular.isDefined(countdownTimer)) {
            $interval.cancel(countdownTimer);
            countdownTimer = undefined;
        }
    }

    // must be called once per second
    function updatePausing() {
        if (pausedTime > 0) {
            // to allow display of smooth countdown every second
            pausedTime--;
        }

        // every minute, synchronize pause with the backend
        if (Math.floor(countdownTimerUpdates % 10) === 0) {
            logger.info('Syncing pause with backend');
            getPause(true);
        }
        countdownTimerUpdates++;
    }
}
